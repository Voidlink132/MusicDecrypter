package com.musicdecrypter;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.util.Base64;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.SslErrorHandler;
import android.webkit.URLUtil;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.musicdecrypter.ui.OnlineDecryptFragment;
import com.musicdecrypter.ui.SearchFragment;
import com.musicdecrypter.ui.SettingsFragment;
import com.musicdecrypter.ui.DecryptFragment;
import com.musicdecrypter.util.LyricFetcher;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    public static final String ONLINE_DECRYPT_URL = "https://music-unlock.netlify.app";
    private static final int FILE_CHOOSER_RESULT_CODE = 1001;

    private WebView decryptWebView;
    private ViewPager2 viewPager;
    private BottomNavigationView bottomNav;

    private File pendingFile;
    private String targetFileName; 
    private boolean isPageFinished = false;
    private boolean isDownloading = false;
    private SharedPreferences sp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        sp = getSharedPreferences("config", Context.MODE_PRIVATE);

        checkStoragePermission();
        initDecryptWebView();
        initBottomNav();
    }

    private void checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                new AlertDialog.Builder(this)
                        .setTitle("需要权限")
                        .setMessage("为了扫描并解密手机中的音乐文件，请在接下来的设置中授予“所有文件访问权限”。")
                        .setPositiveButton("去设置", (dialog, which) -> {
                            try {
                                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                                intent.setData(Uri.parse("package:" + getPackageName()));
                                startActivity(intent);
                            } catch (Exception e) {
                                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                                startActivity(intent);
                            }
                        })
                        .setNegativeButton("取消", null).show();
            }
        }
    }

    private void initBottomNav() {
        viewPager = findViewById(R.id.view_pager);
        bottomNav = findViewById(R.id.bottom_nav);

        viewPager.setAdapter(new FragmentStateAdapter(this) {
            @NonNull
            @Override
            public Fragment createFragment(int position) {
                switch (position) {
                    case 0: return new SearchFragment();
                    case 1: return new OnlineDecryptFragment();
                    case 2: return new DecryptFragment();
                    case 3: return new SettingsFragment();
                    default: return new SearchFragment();
                }
            }
            @Override
            public int getItemCount() { return 4; }
        });

        viewPager.setUserInputEnabled(false);
        viewPager.setOffscreenPageLimit(3);

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_music_list) viewPager.setCurrentItem(0, false);
            else if (id == R.id.nav_decrypt) viewPager.setCurrentItem(1, false);
            else if (id == R.id.nav_search) viewPager.setCurrentItem(2, false);
            else if (id == R.id.nav_settings) viewPager.setCurrentItem(3, false);
            return true;
        });
    }

    private void initDecryptWebView() {
        decryptWebView = new WebView(getApplicationContext());
        WebSettings webSettings = decryptWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        decryptWebView.addJavascriptInterface(new BlobDownloadInterface(), "AndroidBlob");

        decryptWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, android.net.http.SslError error) { handler.proceed(); }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                isPageFinished = true;
                if (pendingFile != null) injectDecryptionScript();
            }

            @Nullable
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (url.contains("___bridge_file___")) {
                    try {
                        if (pendingFile != null) {
                            FileInputStream fis = new FileInputStream(pendingFile);
                            WebResourceResponse response = new WebResourceResponse("application/octet-stream", null, fis);
                            Map<String, String> headers = new HashMap<>();
                            headers.put("Access-Control-Allow-Origin", "*");
                            response.setResponseHeaders(headers);
                            return response;
                        }
                    } catch (Exception e) { Log.e("Bridge", "Error", e); }
                }
                return super.shouldInterceptRequest(view, request);
            }
        });

        decryptWebView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            if (isDownloading) return;
            isDownloading = true;

            String finalFileName;
            if (targetFileName != null) {
                String ext = MimeTypeMapUtils.getExtensionFromMimeType(mimeType);
                String baseName = targetFileName.contains(".") ? targetFileName.substring(0, targetFileName.lastIndexOf(".")) : targetFileName;
                finalFileName = baseName + "." + (ext != null ? ext : "mp3");
            } else {
                finalFileName = URLUtil.guessFileName(url, contentDisposition, mimeType);
            }

            // 关键修复：采用分片提取 Blob，解决大文件 0B 和乱码问题
            String js = "javascript:(function() {" +
                    "  AndroidBlob.updateStatus('解密成功，正在保存...', 85);" +
                    "  var xhr = new XMLHttpRequest();" +
                    "  xhr.open('GET', '" + url + "', true);" +
                    "  xhr.responseType = 'blob';" +
                    "  xhr.onload = function() {" +
                    "    var blob = xhr.response;" +
                    "    var chunkSize = 1024 * 512;" + // 512KB 分片提取
                    "    var offset = 0;" +
                    "    AndroidBlob.startDownload('" + finalFileName + "');" +
                    "    function readNext() {" +
                    "      if (offset >= blob.size) {" +
                    "        AndroidBlob.endDownload('" + finalFileName + "');" +
                    "        return;" +
                    "      }" +
                    "      var slice = blob.slice(offset, offset + chunkSize);" +
                    "      var reader = new FileReader();" +
                    "      reader.onload = function(e) {" +
                    "        var res = e.target.result;" +
                    "        var b64 = res.substring(res.indexOf(',') + 1);" +
                    "        AndroidBlob.appendChunk(b64);" +
                    "        offset += chunkSize;" +
                    "        readNext();" +
                    "      };" +
                    "      reader.readAsDataURL(slice);" +
                    "    }" +
                    "    readNext();" +
                    "  };" +
                    "  xhr.onerror = function() { AndroidBlob.onError('提取失败'); };" +
                    "  xhr.send();" +
                    "})();";
            decryptWebView.evaluateJavascript(js, null);
        });

        decryptWebView.loadUrl(ONLINE_DECRYPT_URL);
    }

    public void startDecryption(File file) {
        String fileName = file.getName().toLowerCase();
        if (fileName.endsWith(".mp3") || fileName.endsWith(".ogg") || fileName.endsWith(".flac") || fileName.endsWith(".wav") || fileName.endsWith(".m4a")) {
            saveNormalFile(file);
            return;
        }
        
        this.isDownloading = false;
        this.pendingFile = file;
        this.targetFileName = file.getName(); 
        updateSearchProgress(true, "初始化环境中...", 10);
        
        decryptWebView.onResume();
        decryptWebView.resumeTimers();

        // 核心修复：每次开始前彻底刷新页面并清空缓存，防止第二首歌下载到第一首
        isPageFinished = false;
        decryptWebView.clearCache(true);
        decryptWebView.loadUrl(ONLINE_DECRYPT_URL);
    }

    private String getFileNameFromUri(Uri uri) {
        String name = "temp_music";
        if ("content".equals(uri.getScheme())) {
            try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) name = cursor.getString(nameIndex);
                }
            } catch (Exception ignored) {}
        } else if ("file".equals(uri.getScheme()) && uri.getPath() != null) {
            name = new File(uri.getPath()).getName();
        }
        return name;
    }

    private void injectDecryptionScript() {
        if (pendingFile == null) return;
        String fileName = pendingFile.getName();
        String bridgeFileName = fileName;
        if (bridgeFileName.toLowerCase().endsWith(".mflac0")) {
            bridgeFileName = bridgeFileName.substring(0, bridgeFileName.lastIndexOf(".")) + ".mflac";
        }

        // 核心修复：增加 localStorage 清理和精准匹配（必须匹配当前正在处理的文件名）
        String script = "javascript:(function() {" +
                "  localStorage.clear(); sessionStorage.clear();" +
                "  AndroidBlob.updateStatus('正在同步文件...', 25);" +
                "  var xhr = new XMLHttpRequest();" +
                "  xhr.open('GET', '" + ONLINE_DECRYPT_URL + "/___bridge_file___', true);" +
                "  xhr.responseType = 'blob';" +
                "  xhr.onload = function() {" +
                "    if (xhr.status === 200) {" +
                "      AndroidBlob.updateStatus('解密中...', 50);" +
                "      var file = new File([xhr.response], '" + bridgeFileName.replace("'", "\\'") + "', {type: 'application/octet-stream'});" +
                "      var input = document.querySelector('input[type=file]') || document.querySelector('input');" +
                "      if (input) {" +
                "        var dt = new DataTransfer(); dt.items.add(file);" +
                "        input.files = dt.files;" +
                "        input.dispatchEvent(new Event('change', { bubbles: true }));" +
                "        " +
                "        var count = 0;" +
                "        var timer = setInterval(function() {" +
                "          count++;" +
                "          /* 精准定位：在列表中寻找包含当前文件名的下载按钮 */ " +
                "          var btn = Array.from(document.querySelectorAll('button, .download-button, .file-action-btn.download')).find(function(b) {" +
                "              var row = b.closest('.file-item, tr, .list-item, .result-item') || b.parentElement;" +
                "              var txt = row ? row.textContent.toLowerCase() : '';" +
                "              var isMatch = txt.indexOf('" + bridgeFileName.toLowerCase().replace("'", "\\'") + "') !== -1 || " +
                "                            txt.indexOf('" + fileName.toLowerCase().replace("'", "\\'") + "') !== -1;" +
                "              var isDownload = b.textContent.includes('下载') || b.title.includes('下载') || b.classList.contains('download');" +
                "              return isMatch && isDownload;" +
                "          });" +
                "          " +
                "          if (btn) {" +
                "            AndroidBlob.updateStatus('解密完成，准备保存...', 75);" +
                "            btn.click();" +
                "            clearInterval(timer);" +
                "          } else if (count > 80) {" +
                "            clearInterval(timer);" +
                "            AndroidBlob.onError('解密超时，请重试');" +
                "          }" +
                "        }, 1000);" +
                "      } else { AndroidBlob.onError('解密组件加载失败'); }" +
                "    } else { AndroidBlob.onError('同步文件失败'); }" +
                "  };" +
                "  xhr.onerror = function() { AndroidBlob.onError('网络中断'); };" +
                "  xhr.send();" +
                "})();";
        
        decryptWebView.post(() -> decryptWebView.evaluateJavascript(script, null));
    }

    public void triggerManualFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, FILE_CHOOSER_RESULT_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_RESULT_CODE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try {
                String fileName = getFileNameFromUri(uri);
                File cacheFile = new File(getExternalCacheDir(), fileName);
                try (InputStream is = getContentResolver().openInputStream(uri);
                     FileOutputStream fos = new FileOutputStream(cacheFile)) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = is.read(buffer)) > 0) fos.write(buffer, 0, len);
                }
                startDecryption(cacheFile);
            } catch (Exception e) {
                Toast.makeText(this, "加载文件失败", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void updateSearchProgress(boolean visible, String step, int percent) {
        runOnUiThread(() -> {
            try {
                Fragment f0 = getSupportFragmentManager().findFragmentByTag("f0");
                if (f0 instanceof SearchFragment) ((SearchFragment) f0).updateProgress(visible, step, percent);
                Fragment f1 = getSupportFragmentManager().findFragmentByTag("f1");
                if (f1 instanceof OnlineDecryptFragment) ((OnlineDecryptFragment) f1).updateProgress(visible, step, percent);
            } catch (Exception ignored) {}
        });
    }

    private void saveNormalFile(File sourceFile) {
        File destDir = new File(Environment.getExternalStorageDirectory(), "Music/MusicDecrypter");
        if (!destDir.exists()) destDir.mkdirs();
        File destFile = new File(destDir, sourceFile.getName());
        try (FileInputStream fis = new FileInputStream(sourceFile); FileOutputStream fos = new FileOutputStream(destFile)) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = fis.read(buffer)) > 0) fos.write(buffer, 0, len);
            Toast.makeText(this, "文件已保存", Toast.LENGTH_SHORT).show();
            checkAndFetchLyric(sourceFile.getName(), destDir);
            sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(destFile)));
        } catch (IOException ignored) {}
    }

    private void checkAndFetchLyric(String fileName, File saveDir) {
        if (sp.getBoolean("fetch_lyric", false)) {
            updateSearchProgress(true, "匹配歌词...", 98);
            LyricFetcher.fetchLyric(MainActivity.this, fileName, saveDir, new LyricFetcher.LyricCallback() {
                @Override public void onSuccess(File lyricFile) {
                    runOnUiThread(() -> {
                        updateSearchProgress(false, "", 100);
                        Toast.makeText(MainActivity.this, "歌词已匹配下载", Toast.LENGTH_SHORT).show();
                        sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(lyricFile)));
                    });
                }
                @Override public void onError(String msg) { updateSearchProgress(false, "", 100); }
            });
        }
    }

    private class BlobDownloadInterface {
        @JavascriptInterface public void updateStatus(String step, int percent) { updateSearchProgress(true, step, percent); }
        private FileOutputStream currentFos;
        private File currentOutputFile;
        @JavascriptInterface public void startDownload(String name) {
            try {
                File d = new File(Environment.getExternalStorageDirectory(), "Music/MusicDecrypter");
                if (!d.exists()) d.mkdirs();
                currentOutputFile = new File(d, name);
                currentFos = new FileOutputStream(currentOutputFile);
            } catch (Exception ignored) { isDownloading = false; }
        }
        @JavascriptInterface public void appendChunk(String b64) {
            try { 
                if (currentFos != null) {
                    byte[] data = Base64.decode(b64, Base64.DEFAULT);
                    if (data != null && data.length > 0) {
                        currentFos.write(data);
                        currentFos.flush();
                    }
                }
            } catch (Exception ignored) {}
        }
        @JavascriptInterface public void endDownload(String name) {
            try {
                if (currentFos != null) { 
                    currentFos.flush();
                    currentFos.getFD().sync(); // 物理刷盘，解决 0B 问题
                    currentFos.close(); 
                    currentFos = null; 
                }
                runOnUiThread(() -> {
                    updateSearchProgress(false, "", 100);
                    if (currentOutputFile != null && currentOutputFile.length() > 0) {
                        Toast.makeText(MainActivity.this, "解密成功！", Toast.LENGTH_LONG).show();
                        checkAndFetchLyric(name, currentOutputFile.getParentFile());
                        sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(currentOutputFile)));
                    } else {
                        Toast.makeText(MainActivity.this, "保存异常：文件内容为空", Toast.LENGTH_LONG).show();
                    }
                    targetFileName = null; isDownloading = false;
                    pendingFile = null;
                });
            } catch (Exception ignored) { isDownloading = false; }
        }
        @JavascriptInterface public void onError(String msg) {
            runOnUiThread(() -> {
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
                updateSearchProgress(false, null, 0);
                targetFileName = null; isDownloading = false;
            });
        }
    }

    public WebView getDecryptWebView() { return decryptWebView; }

    private static class MimeTypeMapUtils {
        public static String getExtensionFromMimeType(String mimeType) {
            if (mimeType == null) return null;
            if (mimeType.contains("flac")) return "flac";
            if (mimeType.contains("mp3")) return "mp3";
            if (mimeType.contains("ogg")) return "ogg";
            if (mimeType.contains("wav")) return "wav";
            if (mimeType.contains("m4a")) return "m4a";
            return null;
        }
    }
}
