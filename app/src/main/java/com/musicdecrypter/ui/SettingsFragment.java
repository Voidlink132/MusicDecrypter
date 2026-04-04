package com.musicdecrypter.ui;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.musicdecrypter.R;
import com.musicdecrypter.utils.SpUtils;

import java.io.File;

import io.noties.markwon.Markwon;

public class SettingsFragment extends Fragment {

    private TextView tvSavePath;
    private MaterialButton btnResetPath;
    private MaterialButton btnModifyPath;
    private TextView tvSettingsContent;

    // 目录选择器
    private final ActivityResultLauncher<Intent> dirChooserLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) return;
                Uri uri = result.getData().getData();
                if (uri == null) return;

                // 解析选择的目录路径
                String path = uri.getPath();
                if (path != null && path.startsWith("/tree/")) {
                    path = path.replace("/tree/", "");
                    if (path.contains(":")) {
                        String[] split = path.split(":");
                        if (split.length >= 2) {
                            path = split[0].equals("primary")
                                    ? Environment.getExternalStorageDirectory() + "/" + split[1]
                                    : "/storage/" + split[0] + "/" + split[1];
                        }
                    }
                }

                // 验证路径可用性
                File dir = new File(path);
                if (!dir.exists() || !dir.canWrite()) {
                    Toast.makeText(requireContext(), "该路径不可用，请选择其他目录", Toast.LENGTH_SHORT).show();
                    return;
                }

                // 保存并更新路径
                SpUtils.setSavePath(requireContext(), path);
                tvSavePath.setText(SpUtils.getSavePath(requireContext()));
                Toast.makeText(requireContext(), "存储路径已修改", Toast.LENGTH_SHORT).show();
            }
    );

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // 绑定控件
        tvSavePath = view.findViewById(R.id.tv_save_path);
        btnResetPath = view.findViewById(R.id.btn_reset_path);
        btnModifyPath = view.findViewById(R.id.btn_modify_path);
        tvSettingsContent = view.findViewById(R.id.tv_settings_content);

        // 显示当前存储路径
        tvSavePath.setText(SpUtils.getSavePath(requireContext()));

        // 恢复默认路径
        btnResetPath.setOnClickListener(v -> {
            SpUtils.setSavePath(requireContext(), SpUtils.getDefaultPath());
            tvSavePath.setText(SpUtils.getSavePath(requireContext()));
            Toast.makeText(requireContext(), "已恢复默认存储路径", Toast.LENGTH_SHORT).show();
        });

        // 修改存储路径
        btnModifyPath.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            dirChooserLauncher.launch(intent);
        });

        // 加载关于说明
        Markwon markwon = Markwon.create(requireContext());
        markwon.setMarkdown(tvSettingsContent, "### 音乐解密工具\n\n- 支持网易云NCM、QQ音乐MGG/QMC、酷狗KGM等加密格式\n- 解密完成的文件自动保存到设置的存储目录中\n- 支持单文件解密、批量多选解密\n\n© 2024 MusicDecrypter");
    }
}
