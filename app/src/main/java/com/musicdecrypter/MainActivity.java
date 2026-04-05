package com.musicdecrypter;

import android.os.Bundle;
import android.util.Log;
import android.webkit.DownloadListener;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.net.http.SslError;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MusicDecrypter_Main";
    private ViewPager2 viewPager;
    private BottomNavigationView bottomNav;
    // 内嵌官方在线解密网页
    public static final String ONLINE_DECRYPT_URL = "https://unlock-music.netlify.app/";
    private WebView decryptWebView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initBottomNav();
        // 初始化WebView（全局唯一，避免重复创建）
        initDecryptWebView();
    }

    private void initBottomNav() {
        viewPager = findViewById(R.id.view_pager);
        bottomNav = findViewById(R.id.bottom_nav);
        FragmentPagerAdapter adapter = new FragmentPagerAdapter(this);
        viewPager.setAdapter(adapter);
        viewPager.setUserInputEnabled(false);
        viewPager.setOffscreenPageLimit(2);

        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_search) {
                viewPager.setCurrentItem(0, false);
                return true;
            } else if (itemId == R.id.nav_decrypt) {
                viewPager.setCurrentItem(1, false);
                return true;
            } else if (itemId == R.id.nav_settings) {
                viewPager.setCurrentItem(2, false);
                return true;
            }
            return false;
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

    /**
     * 初始化WebView：适配在线网页，支持文件上传、下载、JavaScript
     */
    private void initDecryptWebView() {
        WebView.setWebContentsDebuggingEnabled(true);
        decryptWebView = new WebView(getApplicationContext());
        WebSettings webSettings = decryptWebView.getSettings();

        // 核心配置（确保网页正常运行）
        webSettings.setJavaScriptEnabled(true); // 必须启用JS
        webSettings.setJavaScriptCanOpenWindowsAutomatically(true);
        webSettings.setDomStorageEnabled(true); // 支持本地存储
        webSettings.setAllowFileAccess(true); // 允许访问本地文件（上传用）
        webSettings.setAllowContentAccess(true);
        webSettings.setAllowFileAccessFromFileURLs(true);
        webSettings.setAllowUniversalAccessFromFileURLs(true);
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE); // 支持混合内容
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setCacheMode(WebSettings.LOAD_NO_CACHE); // 禁用缓存，确保加载最新网页

        // 网页加载进度监听
        decryptWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                super.onProgressChanged(view, newProgress);
                Log.d(TAG, "网页加载进度：" + newProgress + "%");
            }
        });

        // 网页加载生命周期+SSL错误处理
        decryptWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                Log.d(TAG, "开始加载网页：" + url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                Log.d(TAG, "网页加载完成：" + url);
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                super.onReceivedError(view, errorCode, description, failingUrl);
                Log.e(TAG, "网页加载错误：" + description + "（错误码：" + errorCode + "）");
                Toast.makeText(MainActivity.this, "网页加载失败，请检查网络", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.proceed(); // 忽略SSL错误（避免网页因证书问题无法加载）
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                // 拦截网页跳转，在当前WebView内打开
                view.loadUrl(request.getUrl().toString());
                return true;
            }
        });

        // 支持文件下载（解密后下载文件）
        decryptWebView.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> {
            Log.d(TAG, "开始下载文件：" + url);
            // 调用系统下载管理器下载
            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW);
            intent.setData(android.net.Uri.parse(url));
            startActivity(intent);
        });

        // 预加载在线网页
        decryptWebView.loadUrl(ONLINE_DECRYPT_URL);
    }

    /**
     * 对外提供WebView实例（供DecryptFragment使用）
     */
    public WebView getDecryptWebView() {
        return decryptWebView;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 释放WebView资源
        if (decryptWebView != null) {
            decryptWebView.stopLoading();
            decryptWebView.removeAllViews();
            decryptWebView.destroy();
            decryptWebView = null;
        }
    }
}
