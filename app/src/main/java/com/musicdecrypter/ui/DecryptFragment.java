package com.musicdecrypter.ui;

import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.musicdecrypter.MainActivity;
import com.musicdecrypter.R;

public class DecryptFragment extends Fragment {
    private WebView webView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // 使用包含 webview_container 的布局
        return inflater.inflate(R.layout.fragment_decrypt_webview, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // 获取MainActivity的WebView实例，添加到当前Fragment布局
        if (getActivity() instanceof MainActivity) {
            webView = ((MainActivity) getActivity()).getDecryptWebView();
            ViewGroup container = view.findViewById(R.id.webview_container);
            
            if (webView != null && container != null) {
                // 先从原有父布局移除（避免重复添加）
                ViewGroup parent = (ViewGroup) webView.getParent();
                if (parent != null) {
                    parent.removeView(webView);
                }
                // 添加WebView到当前Fragment
                container.addView(webView);
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (webView != null) {
            webView.onResume();
            webView.resumeTimers();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (webView != null) {
            webView.onPause();
            webView.pauseTimers();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (webView != null) {
            // 从父布局移除即可，不在这里销毁，因为生命周期由 MainActivity 管理
            ViewGroup parent = (ViewGroup) webView.getParent();
            if (parent != null) {
                parent.removeView(webView);
            }
            webView = null;
        }
    }
}
