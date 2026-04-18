package com.musicdecrypter.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.musicdecrypter.MainActivity;
import com.musicdecrypter.R;

public class OnlineDecryptFragment extends Fragment {

    private WebView webView;
    private View progressArea;
    private ProgressBar progressBar;
    private TextView tvStep;
    private TextView tvPercent;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // 使用包含 WebView 的布局
        return inflater.inflate(R.layout.fragment_decrypt, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        progressArea = view.findViewById(R.id.ll_progress_area);
        progressBar = view.findViewById(R.id.decrypt_progress_bar);
        tvStep = view.findViewById(R.id.tv_decrypt_step);
        tvPercent = view.findViewById(R.id.tv_progress_percent);
        
        if (getActivity() instanceof MainActivity) {
            webView = ((MainActivity) getActivity()).getDecryptWebView();
            if (webView.getParent() != null) {
                ((ViewGroup) webView.getParent()).removeView(webView);
            }
            
            // 将 WebView 添加到 fragment_decrypt.xml 中定义的 WebView 容器或替换掉占位 WebView
            View webViewPlaceholder = view.findViewById(R.id.webView);
            if (webViewPlaceholder != null) {
                ViewGroup parent = (ViewGroup) webViewPlaceholder.getParent();
                int index = parent.indexOfChild(webViewPlaceholder);
                parent.removeView(webViewPlaceholder);
                
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
                parent.addView(webView, index, lp);
            }
        }

        view.findViewById(R.id.btn_select_file).setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).triggerManualFilePicker();
            }
        });
        
        view.findViewById(R.id.btn_download).setOnClickListener(v -> {
             // 打开下载目录逻辑（如果需要）
        });
    }

    public void updateProgress(boolean visible, String step, int percent) {
        if (progressArea == null) return;
        progressArea.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (step != null) tvStep.setText(step);
        if (progressBar != null) progressBar.setProgress(percent);
        if (tvPercent != null) tvPercent.setText(percent + "%");
    }
}
