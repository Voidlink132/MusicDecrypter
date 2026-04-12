package com.musicdecrypter.ui;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.musicdecrypter.R;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SettingsFragment extends Fragment {

    private TextView tvSavePath;
    private TextView tvPermissionStatus;
    private TextView tvVersion;
    private TextView tvInstallTime;
    
    private TextView tvDemoStep;
    private TextView tvDemoPercent;
    private ProgressBar pbDemo;
    
    private SharedPreferences sp;
    private Handler handler = new Handler(Looper.getMainLooper());
    private int demoProgress = 0;
    private final String[] demoSteps = {
            "正在扫描本地音乐...", 
            "正在上传至核心...", 
            "正在进行解密...", 
            "解密完成，正在触发下载...", 
            "正在写入手机存储..."
    };

    private final ActivityResultLauncher<Intent> dirChooserLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        Toast.makeText(requireContext(), "已选择新目录", Toast.LENGTH_SHORT).show();
                    }
                }
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
        sp = requireContext().getSharedPreferences("config", Context.MODE_PRIVATE);

        tvSavePath = view.findViewById(R.id.tv_save_path);
        tvPermissionStatus = view.findViewById(R.id.tv_permission_status);
        tvVersion = view.findViewById(R.id.tv_version);
        tvInstallTime = view.findViewById(R.id.tv_install_time);
        
        tvDemoStep = view.findViewById(R.id.tvDemoStep);
        tvDemoPercent = view.findViewById(R.id.tvDemoPercent);
        pbDemo = view.findViewById(R.id.pbDemo);
        
        View layoutSavePath = view.findViewById(R.id.layout_save_path);
        View btnModifyPath = view.findViewById(R.id.btn_modify_path);
        View btnResetPath = view.findViewById(R.id.btn_reset_path);
        View layoutPermission = view.findViewById(R.id.layout_permission);
        View layoutSourceCode = view.findViewById(R.id.tv_source_code);
        View layoutLicenses = view.findViewById(R.id.tv_licenses);
        View layoutAbout = view.findViewById(R.id.tv_about);
        SwitchMaterial switchShowOthers = view.findViewById(R.id.switch_show_others);

        // 1. 设置路径和版本信息
        String defaultPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).getAbsolutePath() + "/MusicDecrypter";
        tvSavePath.setText(defaultPath);

        try {
            PackageInfo pInfo = requireContext().getPackageManager().getPackageInfo(requireContext().getPackageName(), 0);
            tvVersion.setText(pInfo.versionName + " (" + pInfo.versionCode + ")");
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy年MM月dd日", Locale.CHINESE);
            tvInstallTime.setText(sdf.format(new Date(pInfo.firstInstallTime)));
        } catch (PackageManager.NameNotFoundException e) {
            tvVersion.setText("1.0.0 (1)");
        }

        // 2. 开关逻辑
        switchShowOthers.setChecked(sp.getBoolean("show_others", false));
        switchShowOthers.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sp.edit().putBoolean("show_others", isChecked).apply();
            Toast.makeText(getContext(), isChecked ? "已开启显示其他来源" : "已隐藏其他来源", Toast.LENGTH_SHORT).show();
        });

        // 3. 存储设置点击
        layoutSavePath.setOnClickListener(v -> openMusicDirectory(tvSavePath.getText().toString()));

        btnModifyPath.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            dirChooserLauncher.launch(intent);
        });

        btnResetPath.setOnClickListener(v -> {
            tvSavePath.setText(defaultPath);
            Toast.makeText(getContext(), "已恢复默认路径", Toast.LENGTH_SHORT).show();
        });

        // 4. 权限管理点击
        layoutPermission.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (!Environment.isExternalStorageManager()) {
                    try {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                        intent.setData(Uri.parse("package:" + requireContext().getPackageName()));
                        startActivity(intent);
                    } catch (Exception e) {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                        startActivity(intent);
                    }
                } else {
                    Toast.makeText(getContext(), "已获得所有文件访问权限", Toast.LENGTH_SHORT).show();
                }
            }
        });

        layoutSourceCode.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Voidlink132/MusicDecrypter/"));
            startActivity(intent);
        });

        layoutLicenses.setOnClickListener(v -> showLicensesDialog());

        layoutAbout.setOnClickListener(v -> new AlertDialog.Builder(requireContext())
                .setTitle("关于本工具")
                .setMessage("MusicDecrypter 是一款专业的音乐解密辅助工具。\n\n旨在让音乐回归本质，实现解密自由。")
                .setPositiveButton("知道了", null)
                .show());

        updatePermissionStatus();
        startDemoLoop();
    }

    private void openMusicDirectory(String path) {
        File file = new File(path);
        if (!file.exists()) file.mkdirs();
        
        try {
            // 终极策略：通过指定系统文件管理器包名来实现图2样式精准跳转
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri uri = Uri.parse("content://com.android.externalstorage.documents/document/primary%3AMusic%2FMusicDecrypter");
            intent.setDataAndType(uri, "vnd.android.cursor.dir/document");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            // 针对系统文件管理器的包名列表
            String[] targetPackages = {
                "com.google.android.documentsui", 
                "com.android.documentsui", 
                "com.android.fileexplorer", 
                "com.miui.explorer"
            };

            boolean success = false;
            for (String pkg : targetPackages) {
                try {
                    intent.setPackage(pkg);
                    startActivity(intent);
                    success = true;
                    break;
                } catch (Exception ignored) {}
            }

            if (!success) {
                intent.setPackage(null); // 清除指定包名，弹出选择器
                startActivity(Intent.createChooser(intent, "查看文件夹"));
            }
        } catch (Exception e) {
            Toast.makeText(getContext(), "无法跳转到文件夹", Toast.LENGTH_SHORT).show();
        }
    }

    private void showLicensesDialog() {
        String licenseText = "本应用使用了以下开源项目：\n\n" +
                "• AndroidX Libraries (Apache 2.0)\n" +
                "• Material Components (Apache 2.0)\n" +
                "• Markwon Core (Apache 2.0)\n" +
                "• ViewPager2 (Apache 2.0)\n" +
                "• ConstraintLayout (Apache 2.0)\n\n" +
                "Copyright © 2024 MusicDecrypter Contributors\n" +
                "Licensed under the Apache License, Version 2.0";
                
        new AlertDialog.Builder(requireContext())
                .setTitle("开源许可")
                .setMessage(licenseText)
                .setPositiveButton("知道了", null)
                .show();
    }

    private void startDemoLoop() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (pbDemo != null) {
                    demoProgress = (demoProgress + 1) % 101;
                    pbDemo.setProgress(demoProgress);
                    tvDemoPercent.setText(demoProgress + "%");
                    
                    int stepIndex = 0;
                    if (demoProgress < 20) stepIndex = 0;
                    else if (demoProgress < 40) stepIndex = 1;
                    else if (demoProgress < 60) stepIndex = 2;
                    else if (demoProgress < 85) stepIndex = 3;
                    else stepIndex = 4;
                    
                    tvDemoStep.setText(demoSteps[stepIndex]);
                    
                    handler.postDelayed(this, 100);
                }
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        updatePermissionStatus();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacksAndMessages(null);
    }

    private void updatePermissionStatus() {
        if (tvPermissionStatus == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            boolean hasPermission = Environment.isExternalStorageManager();
            tvPermissionStatus.setText(hasPermission ? "已授权" : "未授权");
            tvPermissionStatus.setTextColor(hasPermission ? 0xFF4CAF50 : 0xFFF44336);
        } else {
            tvPermissionStatus.setText("系统版本无需此项");
            tvPermissionStatus.setTextColor(0xFF999999);
        }
    }
}
