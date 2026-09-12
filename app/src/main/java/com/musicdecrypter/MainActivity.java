package com.musicdecrypter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaScannerConnection;
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
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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

@SuppressWarnings("deprecation")
public class MainActivity extends AppCompatActivity {

    public static final String ONLINE_DECRYPT_URL = "https://music-unlock.netlify.app";
    private static final String TAG = "MainActivity";

    private WebView decryptWebView;
    private ViewPager2 viewPager;
    private BottomNavigationView bottomNav;

    private File pendingFile;
    private String targetFileName;
    private boolean isPageFinished = false;
    private boolean isDownloading = false;
    private SharedPreferences sp;

    private final ActivityResultLauncher<Intent> fileChooserLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null && result.getData().getData() != null) {
                    Uri uri = result.getData().getData();
                    try {
                        String fileName = getFileNameFromUri(uri);
                        File cacheFile = new File(getExternalCacheDir(), fileName);
                        try (InputStream is = getContentResolver().openInputStream(uri);
                             FileOutputStream fos = new FileOutputStream(cacheFile)) {
                            byte[] buffer = new byte[8192];
                            int len;
                            while ((len = is.read(buffer)) > 0) {
                                fos.write(buffer, 0, len);
                            }
                        }
                        startDecryption(cacheFile);
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, "加载文件失败", Toast.LENGTH_SHORT).show();
                    }
                }
            }
    );

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
            if (id == R.id.nav_music_list) {
                viewPager.setCurrentItem(0, false);
            } else if (id == R.id.nav_decrypt) {
                viewPager.setCurrentItem(1, false);
            } else if (id == R.id.nav_search) {
                viewPager.setCurrentItem(2, false);
            } else if (id == R.id.nav_settings) {
                viewPager.setCurrentItem(3, false);
            }
            return true;
        });
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void initDecryptWebView() {
        decryptWebView = new WebView(getApplicationContext());
        WebSettings webSettings = decryptWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        decryptWebView.addJavascriptInterface(new BlobDownloadInterface(), "AndroidBlob");

        decryptWebView.setWebViewClient(new WebViewClient() {
            @SuppressLint("WebViewClientOnReceivedSslError")
            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, android.net.http.SslError error) {
                handler.proceed();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                isPageFinished = true;
                if (pendingFile != null) {
                    injectDecryptionScript();
                }
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
                    } catch (Exception e) {
                        Log.e(TAG, "Bridge Error", e);
                    }
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

            String js = "javascript:(function() {" +
                    "  AndroidBlob.updateStatus('解密成功，正在保存...', 85);" +
                    "  var xhr = new XMLHttpRequest();" +
                    "  xhr.open('GET', '" + url + "', true);" +
                    "  xhr.responseType = 'blob';" +
                    "  xhr.onload = function() {" +
                    "    var blob = xhr.response;" +
                    "    var chunkSize = 1024 * 512;" +
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

        final String fBridgeName = bridgeFileName.replace("'", "\\'");

        String script = "javascript:(function() {" +
                "  localStorage.clear(); sessionStorage.clear();" +
                "  AndroidBlob.updateStatus('正在同步文件...', 25);" +
                "  var xhr = new XMLHttpRequest();" +
                "  xhr.open('GET', '" + ONLINE_DECRYPT_URL + "/___bridge_file___', true);" +
                "  xhr.responseType = 'blob';" +
                "  xhr.onload = function() {" +
                "    if (xhr.status === 200) {" +
                "      AndroidBlob.updateStatus('解密中...', 50);" +
                "      var file = new File([xhr.response], '" + fBridgeName + "', {type: 'application/octet-stream'});" +
                "      var input = document.querySelector('input[type=file]') || document.querySelector('input');" +
                "      if (input) {" +
                "        var dt = new DataTransfer(); dt.items.add(file);" +
                "        input.files = dt.files;" +
                "        input.dispatchEvent(new Event('change', { bubbles: true }));" +
                "        " +
                "        var startTime = Date.now();" +
                "        function findAndClick() {" +
                "          var element = document.querySelector('.el-icon-download') || document.querySelector('[class*=\"el-icon-download\"]');" +
                "          if (element) {" +
                "            AndroidBlob.updateStatus('解密完成，准备保存...', 75);" +
                "            element.click();" +
                "            if (element.parentElement) element.parentElement.click();" +
                "            return true;" +
                "          }" +
                "          return false;" +
                "        }" +
                "        " +
                "        var observer = new MutationObserver(function(mutations, obs) {" +
                "          if (findAndClick()) { obs.disconnect(); }" +
                "          else if (Date.now() - startTime > 60000) {" +
                "            obs.disconnect(); AndroidBlob.onError('解密超时，请检查文件格式');" +
                "          }" +
                "        });" +
                "        observer.observe(document.body, { childList: true, subtree: true });" +
                "        findAndClick();" +
                "      } else { AndroidBlob.onError('找不到上传入口'); }" +
                "    } else { AndroidBlob.onError('同步失败'); }" +
                "  };" +
                "  xhr.onerror = function() { AndroidBlob.onError('网络中断'); };" +
                "  xhr.send();" +
                "})();";

        decryptWebView.post(() -> decryptWebView.evaluateJavascript(script, null));
    }

    private void updateSearchProgress(boolean visible, String step, int percent) {
        runOnUiThread(() -> {
            try {
                Fragment f0 = getSupportFragmentManager().findFragmentByTag("f0");
                if (f0 instanceof SearchFragment) {
                    ((SearchFragment) f0).updateProgress(visible, step, percent);
                }
                Fragment f1 = getSupportFragmentManager().findFragmentByTag("f1");
                if (f1 instanceof OnlineDecryptFragment) {
                    ((OnlineDecryptFragment) f1).updateProgress(visible, step, percent);
                }
            } catch (Exception ignored) {}
        });
    }

    private void saveNormalFile(File sourceFile) {
        File destDir = new File(Environment.getExternalStorageDirectory(), "Music/MusicDecrypter");
        if (!destDir.exists()) {
            destDir.mkdirs();
        }
        File destFile = new File(destDir, sourceFile.getName());
        try (FileInputStream fis = new FileInputStream(sourceFile);
             FileOutputStream fos = new FileOutputStream(destFile)) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = fis.read(buffer)) > 0) {
                fos.write(buffer, 0, len);
            }
            Toast.makeText(this, "文件已保存", Toast.LENGTH_SHORT).show();
            checkAndFetchLyric(sourceFile.getName(), destDir);
            MediaScannerConnection.scanFile(this, new String[]{destFile.getAbsolutePath()}, null, null);
        } catch (IOException ignored) {}
    }

    private void checkAndFetchLyric(String fileName, File saveDir) {
        if (sp.getBoolean("fetch_lyric", false)) {
            updateSearchProgress(true, "匹配歌词...", 98);
            LyricFetcher.fetchLyric(MainActivity.this, fileName, saveDir, new LyricFetcher.LyricCallback() {
                @Override
                public void onSuccess(File lyricFile) {
                    runOnUiThread(() -> {
                        updateSearchProgress(false, "", 100);
                        Toast.makeText(MainActivity.this, "歌词已匹配下载", Toast.LENGTH_SHORT).show();
                        MediaScannerConnection.scanFile(MainActivity.this, new String[]{lyricFile.getAbsolutePath()}, null, null);
                    });
                }
                @Override
                public void onError(String msg) {
                    updateSearchProgress(false, "", 100);
                }
            });
        }
    }

    private class BlobDownloadInterface {
        private FileOutputStream currentFos;
        private File currentOutputFile;

        @JavascriptInterface
        public void updateStatus(String step, int percent) {
            updateSearchProgress(true, step, percent);
        }

        @JavascriptInterface
        public void startDownload(String name) {
            try {
                File d = new File(Environment.getExternalStorageDirectory(), "Music/MusicDecrypter");
                if (!d.exists()) {
                    d.mkdirs();
                }
                currentOutputFile = new File(d, name);
                currentFos = new FileOutputStream(currentOutputFile);
            } catch (Exception ignored) {
                isDownloading = false;
            }
        }

        @JavascriptInterface
        public void appendChunk(String b64) {
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

        @JavascriptInterface
        public void endDownload(String name) {
            try {
                if (currentFos != null) {
                    currentFos.flush();
                    currentFos.getFD().sync();
                    currentFos.close();
                    currentFos = null;
                }
                runOnUiThread(() -> {
                    updateSearchProgress(false, "", 100);
                    if (currentOutputFile != null && currentOutputFile.length() > 0) {
                        Toast.makeText(MainActivity.this, "解密成功！", Toast.LENGTH_LONG).show();
                        checkAndFetchLyric(name, currentOutputFile.getParentFile());
                        MediaScannerConnection.scanFile(MainActivity.this, new String[]{currentOutputFile.getAbsolutePath()}, null, null);
                    } else {
                        Toast.makeText(MainActivity.this, "保存异常：文件内容为空", Toast.LENGTH_LONG).show();
                    }
                    targetFileName = null;
                    isDownloading = false;
                    pendingFile = null;
                });
            } catch (Exception ignored) {
                isDownloading = false;
            }
        }

        @JavascriptInterface
        public void onError(String msg) {
            runOnUiThread(() -> {
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
                updateSearchProgress(false, null, 0);
                targetFileName = null;
                isDownloading = false;
            });
        }
    }

    public WebView getDecryptWebView() {
        return decryptWebView;
    }

    public void triggerManualFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        fileChooserLauncher.launch(intent);
    }

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
