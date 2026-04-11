package com.musicdecrypter;

import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.webkit.DownloadListener;
import android.webkit.SslErrorHandler;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.net.http.SslError;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    // 已替换为你指定的解密网址
    public static final String ONLINE_DECRYPT_URL = "https://music-unlock.netlify.app";
    private static final int FILE_CHOOSER_RESULT_CODE = 1001;

    private ValueCallback<Uri[]> mUploadMessage;
    private WebView decryptWebView;

    private ViewPager2 viewPager;
    private BottomNavigationView bottomNav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化底部导航
        initBottomNav();
        // 初始化解密WebView
        initDecryptWebView();
    }

    // 底部导航+ViewPager2初始化（和三周前逻辑完全一致）
    private void initBottomNav() {
        viewPager = findViewById(R.id.view_pager);
        bottomNav = findViewById(R.id.bottom_nav);

        viewPager.setAdapter(new FragmentStateAdapter(this) {
            @NonNull
            @Override
            public androidx.fragment.app.Fragment createFragment(int position) {
                switch (position) {
                    case 0: return new com.musicdecrypter.ui.SearchFragment();
                    case 1: return new com.musicdecrypter.ui.DecryptFragment();
                    case 2: return new com.musicdecrypter.ui.SettingsFragment();
                    default: return new com.musicdecrypter.ui.SearchFragment();
                }
            }

            @Override
            public int getItemCount() {
                return 3;
            }
        });

        // 禁止左右滑动切换，只允许点击底部导航
        viewPager.setUserInputEnabled(false);
        // 预加载所有页面，避免切换时重新加载
        viewPager.setOffscreenPageLimit(2);

        // 底部导航点击事件
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_search) {
                viewPager.setCurrentItem(0, false);
                return true;
            } else if (id == R.id.nav_decrypt) {
                viewPager.setCurrentItem(1, false);
                return true;
            } else if (id == R.id.nav_settings) {
                viewPager.setCurrentItem(2, false);
                return true;
            }
            return false;
        });

        // 页面切换同步底部导航选中状态
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

    // WebView初始化（完全恢复可用配置，支持文件选择、下载）
    private void initDecryptWebView() {
        decryptWebView = new WebView(getApplicationContext());
        WebSettings webSettings = decryptWebView.getSettings();

        // 核心配置，和三周前可用版本完全一致
        webSettings.setJavaScriptEnabled(true);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

        // 文件选择支持（适配解密页的上传功能）
        decryptWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                if (mUploadMessage != null) {
                    mUploadMessage.onReceiveValue(null);
                }
                mUploadMessage = filePathCallback;

                Intent intent = fileChooserParams.createIntent();
                intent.setType("audio/*");
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);

                try {
                    startActivityForResult(intent, FILE_CHOOSER_RESULT_CODE);
                } catch (ActivityNotFoundException e) {
                    mUploadMessage.onReceiveValue(null);
                    mUploadMessage = null;
                    Toast.makeText(MainActivity.this, "未找到文件管理器", Toast.LENGTH_SHORT).show();
                    return false;
                }
                return true;
            }
        });

        // 网页加载处理（忽略SSL错误，正常加载页面）
        decryptWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.proceed();
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                view.loadUrl(request.getUrl().toString());
                return true;
            }
        });

        // 下载支持（解密后的文件自动下载到系统音乐目录）
        decryptWebView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimeType, long contentLength) {
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                request.setMimeType(mimeType);
                request.addRequestHeader("User-Agent", userAgent);
                request.setTitle(URLUtil.guessFileName(url, contentDisposition, mimeType));
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, URLUtil.guessFileName(url, contentDisposition, mimeType));

                DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                dm.enqueue(request);
                Toast.makeText(MainActivity.this, "开始下载解密文件", Toast.LENGTH_SHORT).show();
            }
        });

        // 加载你指定的解密网址
        decryptWebView.loadUrl(ONLINE_DECRYPT_URL);
    }

    // 给DecryptFragment提供WebView实例
    public WebView getDecryptWebView() {
        return decryptWebView;
    }

    // 文件选择结果回调
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_RESULT_CODE) {
            if (mUploadMessage == null) return;
            Uri[] result = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            mUploadMessage.onReceiveValue(result);
            mUploadMessage = null;
        }
    }

    // WebView生命周期管理
    @Override
    protected void onPause() {
        super.onPause();
        if (decryptWebView != null) decryptWebView.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (decryptWebView != null) decryptWebView.onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (decryptWebView != null) {
            decryptWebView.stopLoading();
            decryptWebView.destroy();
        }
        if (mUploadMessage != null) {
            mUploadMessage.onReceiveValue(null);
            mUploadMessage = null;
        }
    }
}
