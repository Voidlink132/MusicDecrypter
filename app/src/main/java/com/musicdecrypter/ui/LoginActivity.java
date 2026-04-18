package com.musicdecrypter.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.webkit.CookieManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class LoginActivity extends AppCompatActivity {

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WebView webView = new WebView(this);
        setContentView(webView);

        String url = getIntent().getStringExtra("url");
        String platform = getIntent().getStringExtra("platform");

        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                String cookie = CookieManager.getInstance().getCookie(url);
                if (cookie != null) {
                    boolean isNeteaseSuccess = "netease".equals(platform) && cookie.contains("MUSIC_U");
                    boolean isQQSuccess = "qq".equals(platform) && (cookie.contains("pskey") || cookie.contains("p_skey") || (cookie.contains("uin=") && cookie.contains("skey=")));

                    if (isNeteaseSuccess || isQQSuccess) {
                        getSharedPreferences("config", Context.MODE_PRIVATE)
                                .edit()
                                .putString("cookie_" + platform, cookie)
                                .apply();
                        
                        Toast.makeText(LoginActivity.this, "登录成功，已获取 VIP 权限", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                }
            }
        });

        if (url != null) webView.loadUrl(url);
    }
}
