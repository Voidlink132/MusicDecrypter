package com.musicdecrypter;

import android.app.DownloadManager;
import android.content.Intent;
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
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
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
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.musicdecrypter.ui.SearchFragment;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
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
    private boolean isDownloading = false; // 下载锁，防止重复下载

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        checkStoragePermission();
        initBottomNav();
        initDecryptWebView();
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
                    case 1: return new com.musicdecrypter.ui.DecryptFragment();
                    case 2: return new com.musicdecrypter.ui.SettingsFragment();
                    default: return new SearchFragment();
                }
            }
            @Override
            public int getItemCount() { return 3; }
        });

        viewPager.setUserInputEnabled(false);
        viewPager.setOffscreenPageLimit(2);

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_search) viewPager.setCurrentItem(0, false);
            else if (id == R.id.nav_decrypt) viewPager.setCurrentItem(1, false);
            else if (id == R.id.nav_settings) viewPager.setCurrentItem(2, false);
            return true;
        });

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                switch (position) {
                    case 0: bottomNav.setSelectedItemId(R.id.nav_search); break;
                    case 1: bottomNav.setSelectedItemId(R.id.nav_decrypt); break;
                    case 2: bottomNav.setSelectedItemId(R.id.nav_settings); break;
                }
            }
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
                if (url.contains("bridge_file")) {
                    try {
                        if (pendingFile != null) {
                            FileInputStream fis = new FileInputStream(pendingFile);
                            WebResourceResponse response = new WebResourceResponse("application/octet-stream", "UTF-8", fis);
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
            // 关键修复：如果已经在下载中，直接拦截重复请求
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
                    "  AndroidBlob.updateStatus('解密成功，正在提取文件...', 85);" +
                    "  var xhr = new XMLHttpRequest();" +
                    "  xhr.open('GET', '" + url + "', true);" +
                    "  xhr.responseType = 'blob';" +
                    "  xhr.onload = function() {" +
                    "    var blob = xhr.response;" +
                    "    AndroidBlob.startDownload('" + finalFileName + "');" +
                    "    var reader = new FileReader();" +
                    "    reader.onload = function() {" +
                    "      var base64 = reader.result.split(',')[1];" +
                    "      AndroidBlob.appendChunk(base64);" +
                    "      AndroidBlob.endDownload('" + finalFileName + "');" +
                    "    };" +
                    "    reader.readAsDataURL(blob);" +
                    "  };" +
                    "  xhr.onerror = function() { AndroidBlob.onError('提取解密文件失败'); };" +
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
        
        this.isDownloading = false; // 重置下载锁
        this.pendingFile = file;
        this.targetFileName = file.getName(); 
        updateSearchProgress(true, "正在准备文件...", 10);
        
        decryptWebView.onResume();
        decryptWebView.resumeTimers();

        if (isPageFinished) {
            injectDecryptionScript();
        } else {
            decryptWebView.loadUrl(ONLINE_DECRYPT_URL);
            updateSearchProgress(true, "正在连接解密服务器...", 5);
        }
    }

    public void triggerManualFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT); // 使用 ACTION_OPEN_DOCUMENT 唤起系统文件选择器
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
                    while ((len = is.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                    }
                }
                startDecryption(cacheFile);
            } catch (Exception e) {
                Toast.makeText(this, "加载文件失败", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private String getFileNameFromUri(Uri uri) {
        String name = "temp_music";
        if ("content".equals(uri.getScheme())) {
            try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        name = cursor.getString(nameIndex);
                    }
                }
            } catch (Exception ignored) {}
        } else if ("file".equals(uri.getScheme())) {
            name = new File(uri.getPath()).getName();
        }
        return name;
    }

    private void injectDecryptionScript() {
        if (pendingFile == null) return;
        String fileName = pendingFile.getName();
        String script = "javascript:(function() {" +
                "  AndroidBlob.updateStatus('正在上传文件至核心...', 25);" +
                "  var xhr = new XMLHttpRequest();" +
                "  xhr.open('GET', '" + ONLINE_DECRYPT_URL + "/___bridge_file___', true);" +
                "  xhr.responseType = 'blob';" +
                "  xhr.onload = function() {" +
                "    if (xhr.status === 200) {" +
                "      AndroidBlob.updateStatus('上传成功，正在进行解密...', 50);" +
                "      var file = new File([xhr.response], '" + fileName + "', {type: 'application/octet-stream'});" +
                "      var container = document.querySelector('input[type=file]');" +
                "      if (container) {" +
                "        var dt = new DataTransfer(); dt.items.add(file);" +
                "        container.files = dt.files;" +
                "        container.dispatchEvent(new Event('change', { bubbles: true }));" +
                "        " +
                "        var checkCount = 0;" +
                "        var checkInterval = setInterval(function() {" +
                "          checkCount++;" +
                "          var btn = document.querySelector('.download-button') || " +
                "                    document.querySelector('.file-action-btn.download') ||" +
                "                    document.querySelector('button[title=\"下载全部\"]') ||" +
                "                    Array.from(document.querySelectorAll('button')).find(b => b.textContent.includes('下载'));" +
                "          " +
                "          if (btn) {" +
                "            AndroidBlob.updateStatus('解密完成，正在触发下载...', 75);" +
                "            btn.click();" +
                "            clearInterval(checkInterval);" +
                "          } else if (checkCount > 60) {" +
                "            clearInterval(checkInterval);" +
                "            AndroidBlob.onError('解密超时，请重试');" +
                "          }" +
                "        }, 500);" +
                "      } else { AndroidBlob.onError('未找到解密入口'); }" +
                "    } else { AndroidBlob.onError('文件同步失败: ' + xhr.status); }" +
                "  };" +
                "  xhr.onerror = function() { AndroidBlob.onError('无法连接到网桥'); };" +
                "  xhr.send();" +
                "})();";
        
        decryptWebView.post(() -> decryptWebView.evaluateJavascript(script, null));
    }

    private void updateSearchProgress(boolean visible, String step, int percent) {
        runOnUiThread(() -> {
            try {
                Fragment fragment = getSupportFragmentManager().findFragmentByTag("f0");
                if (fragment instanceof SearchFragment) {
                    ((SearchFragment) fragment).updateProgress(visible, step, percent);
                }
            } catch (Exception e) {
                Log.e("UI", "Progress update failed", e);
            }
        });
    }

    private void saveNormalFile(File sourceFile) {
        File destDir = new File(Environment.getExternalStorageDirectory(), "Music/MusicDecrypter");
        if (!destDir.exists()) destDir.mkdirs();
        File destFile = new File(destDir, sourceFile.getName());
        try (FileInputStream fis = new FileInputStream(sourceFile); FileOutputStream fos = new FileOutputStream(destFile)) {
            fis.getChannel().transferTo(0, fis.getChannel().size(), fos.getChannel());
            Toast.makeText(this, "文件已保存至音乐目录", Toast.LENGTH_SHORT).show();
            // 弹出打开方式
            openDecryptedFile(destFile);
            sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(destFile)));
        } catch (IOException ignored) {}
    }

    private void openDecryptedFile(File file) {
        try {
            Uri contentUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(contentUri, MimeTypeMapUtils.getMimeType(file.getName()));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "打开文件"));
        } catch (Exception e) {
            Toast.makeText(this, "无法呼起打开方式", Toast.LENGTH_SHORT).show();
        }
    }

    private class BlobDownloadInterface {
        @JavascriptInterface
        public void updateStatus(String step, int percent) {
            updateSearchProgress(true, step, percent);
        }

        private FileOutputStream fos;
        private File currentOutputFile;

        @JavascriptInterface
        public void startDownload(String name) {
            try {
                File d = new File(Environment.getExternalStorageDirectory(), "Music/MusicDecrypter");
                if (!d.exists()) d.mkdirs();
                currentOutputFile = new File(d, name);
                fos = new FileOutputStream(currentOutputFile);
                updateSearchProgress(true, "正在写入手机存储...", 95);
            } catch (Exception e) { e.printStackTrace(); }
        }

        @JavascriptInterface
        public void appendChunk(String b64) {
            try { if (fos != null) fos.write(Base64.decode(b64, Base64.DEFAULT)); } catch (Exception ignored) {}
        }

        @JavascriptInterface
        public void endDownload(String name) {
            try {
                if (fos != null) { fos.close(); fos = null; }
                runOnUiThread(() -> {
                    updateSearchProgress(false, "", 100);
                    Toast.makeText(MainActivity.this, "解密成功并已保存！", Toast.LENGTH_LONG).show();
                    
                    if (currentOutputFile != null) {
                        openDecryptedFile(currentOutputFile);
                        sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(currentOutputFile)));
                    }
                    
                    targetFileName = null;
                    isDownloading = false; // 下载结束，重置锁
                });
            } catch (Exception e) { e.printStackTrace(); }
        }

        @JavascriptInterface
        public void onError(String msg) {
            runOnUiThread(() -> {
                Toast.makeText(MainActivity.this, "解密失败: " + msg, Toast.LENGTH_LONG).show();
                updateSearchProgress(false, null, 0);
                targetFileName = null;
                isDownloading = false; // 出错，重置锁
            });
        }
    }

    public WebView getDecryptWebView() { return decryptWebView; }

    private static class MimeTypeMapUtils {
        public static String getMimeType(String fileName) {
            String name = fileName.toLowerCase();
            if (name.endsWith(".flac")) return "audio/flac";
            if (name.endsWith(".mp3")) return "audio/mpeg";
            if (name.endsWith(".ogg")) return "audio/ogg";
            if (name.endsWith(".wav")) return "audio/x-wav";
            if (name.endsWith(".m4a")) return "audio/mp4";
            return "audio/*";
        }

        public static String getExtensionFromMimeType(String mimeType) {
            if (mimeType == null) return null;
            if (mimeType.contains("flac")) return "flac";
            if (mimeType.contains("mp3")) return "mp3";
            if (mimeType.contains("ogg")) return "ogg";
            if (mimeType.contains("mpeg")) return "mp3";
            if (mimeType.contains("wav")) return "wav";
            if (mimeType.contains("m4a")) return "m4a";
            return null;
        }
    }
}
