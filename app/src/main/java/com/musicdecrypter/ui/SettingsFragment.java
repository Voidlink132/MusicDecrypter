package com.musicdecrypter.ui;

import android.app.Activity;
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
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
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
        View layoutLicenses = view.findViewById(R.id.layout_license);
        View layoutAbout = view.findViewById(R.id.tv_about);
        
        SwitchMaterial switchShowOthers = view.findViewById(R.id.switch_show_others);
        SwitchMaterial switchFetchLyric = view.findViewById(R.id.switch_fetch_lyric);

        // 歌词二级设置相关组件
        View layoutLyricSettings = view.findViewById(R.id.layout_lyric_settings);
        TextView tvLyricEncoding = view.findViewById(R.id.tv_lyric_encoding);
        TextView tvLyricFormat = view.findViewById(R.id.tv_lyric_format);
        SwitchMaterial switchBilingualLyric = view.findViewById(R.id.switch_bilingual_lyric);
        View layoutBilingualOptions = view.findViewById(R.id.layout_bilingual_options);
        TextView tvBilingualType = view.findViewById(R.id.tv_bilingual_type);
        View layoutCombineSymbol = view.findViewById(R.id.layout_combine_symbol);
        EditText etCombineSymbol = view.findViewById(R.id.et_combine_symbol);

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
        });

        // 歌词主开关
        boolean fetchLyric = sp.getBoolean("fetch_lyric", false);
        switchFetchLyric.setChecked(fetchLyric);
        layoutLyricSettings.setVisibility(fetchLyric ? View.VISIBLE : View.GONE);
        switchFetchLyric.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sp.edit().putBoolean("fetch_lyric", isChecked).apply();
            layoutLyricSettings.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        // 编码选择
        tvLyricEncoding.setText(sp.getString("lyric_encoding", "UTF-8"));
        view.findViewById(R.id.btn_lyric_encoding).setOnClickListener(v -> {
            String[] encodings = {"UTF-8", "UTF-16 LE"};
            new AlertDialog.Builder(requireContext())
                    .setTitle("选择歌词输出编码")
                    .setItems(encodings, (dialog, which) -> {
                        String selected = encodings[which];
                        sp.edit().putString("lyric_encoding", selected).apply();
                        tvLyricEncoding.setText(selected);
                    }).show();
        });

        // 格式选择
        tvLyricFormat.setText(sp.getString("lyric_format", "LRC"));
        view.findViewById(R.id.btn_lyric_format).setOnClickListener(v -> {
            String[] formats = {"LRC", "SRT"};
            new AlertDialog.Builder(requireContext())
                    .setTitle("选择歌词输出格式")
                    .setItems(formats, (dialog, which) -> {
                        String selected = formats[which];
                        sp.edit().putString("lyric_format", selected).apply();
                        tvLyricFormat.setText(selected);
                    }).show();
        });

        // 双语开关
        boolean bilingual = sp.getBoolean("bilingual_lyric", false);
        switchBilingualLyric.setChecked(bilingual);
        layoutBilingualOptions.setVisibility(bilingual ? View.VISIBLE : View.GONE);
        switchBilingualLyric.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sp.edit().putBoolean("bilingual_lyric", isChecked).apply();
            layoutBilingualOptions.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        // 双语类型
        String bilingualType = sp.getString("bilingual_type", "合并");
        tvBilingualType.setText(bilingualType);
        layoutCombineSymbol.setVisibility("合并".equals(bilingualType) ? View.VISIBLE : View.GONE);
        view.findViewById(R.id.btn_bilingual_type).setOnClickListener(v -> {
            String[] types = {"合并", "交错", "独立"};
            new AlertDialog.Builder(requireContext())
                    .setTitle("双语展示方式")
                    .setItems(types, (dialog, which) -> {
                        String selected = types[which];
                        sp.edit().putString("bilingual_type", selected).apply();
                        tvBilingualType.setText(selected);
                        layoutCombineSymbol.setVisibility("合并".equals(selected) ? View.VISIBLE : View.GONE);
                    }).show();
        });

        // 合并符
        etCombineSymbol.setText(sp.getString("combine_symbol", "/"));
        etCombineSymbol.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                sp.edit().putString("combine_symbol", s.toString()).apply();
            }
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
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri uri = Uri.parse("content://com.android.externalstorage.documents/document/primary%3AMusic%2FMusicDecrypter");
            intent.setDataAndType(uri, "vnd.android.cursor.dir/document");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            String[] targetPackages = {
                "com.google.android.documentsui", 
                "com.android.documentsui", 
                "com.android.fileexplorer", 
                "com.miui.explorer",
                "com.coloros.filemanager",
                "com.huawei.hidisk"
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
                intent.setPackage(null);
                startActivity(Intent.createChooser(intent, "使用文件管理打开"));
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
                "• ConstraintLayout (Apache 2.0)\n" +
                "• RecyclerView (Apache 2.0)\n\n" +
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
