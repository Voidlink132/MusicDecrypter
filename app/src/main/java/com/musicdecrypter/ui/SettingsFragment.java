package com.musicdecrypter.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.musicdecrypter.R;

import io.noties.markwon.Markwon;

public class SettingsFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        TextView tvContent = view.findViewById(R.id.tv_settings_content);

        // Markwon初始化，解决编译报错
        Markwon markwon = Markwon.create(requireContext());
        markwon.setMarkdown(tvContent, "### 音乐解密工具\n\n- 支持网易云NCM、QQ音乐MGG/QMC、酷狗KGM格式\n- 解密后文件保存到 下载/MusicDecrypter 目录\n\n© 2024 MusicDecrypter");
    }
}
