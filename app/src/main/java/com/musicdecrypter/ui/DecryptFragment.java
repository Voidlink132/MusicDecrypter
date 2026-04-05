package com.musicdecrypter.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.musicdecrypter.MainActivity;
import com.musicdecrypter.R;

public class DecryptFragment extends Fragment {
    private WebView decryptWebView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // 直接加载WebView容器布局（仅一个FrameLayout）
        return inflater.inflate(R.layout.fragment_decrypt_webview, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // 获取MainActivity的WebView实例，添加到当前Fragment布局
        if (getActivity() instanceof MainActivity) {
            decryptWebView = ((MainActivity) getActivity()).getDecryptWebView();
            ViewGroup container = view.findViewById(R.id.webview_container);
            // 先移除原有父布局（避免重复添加）
            ViewGroup parent = (ViewGroup) decryptWebView.getParent();
            if (parent != null) {
                parent.removeView(decryptWebView);
            }
            // 添加WebView到当前Fragment
            container.addView(decryptWebView);
        }
    }
}
