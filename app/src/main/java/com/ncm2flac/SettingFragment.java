package com.ncm2flac;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

public class SettingFragment extends Fragment {

    private SharedPreferences sp;
    private final ActivityResultLauncher<Intent> selectBackgroundLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        // 持久化图片读取权限，重启APP不失效
                        final int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION;
                        requireActivity().getContentResolver().takePersistableUriPermission(uri, takeFlags);
                        // 保存背景地址到本地
                        sp.edit().putString("background_uri", uri.toString()).apply();
                        // 通知主页面更新背景
                        if (requireActivity() instanceof MainActivity) {
                            ((MainActivity) requireActivity()).updateBackground();
                        }
                        Toast.makeText(getContext(), "背景设置成功", Toast.LENGTH_SHORT).show();
                    }
                }
            }
    );

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_setting, container, false);
        sp = requireActivity().getSharedPreferences("app_config", 0);

        // 绑定控件
        TextView tvVersion = view.findViewById(R.id.tv_version);
        View layoutUsage = view.findViewById(R.id.layout_usage);
        View layoutCheckPermission = view.findViewById(R.id.layout_check_permission);
        View layoutSetBackground = view.findViewById(R.id.layout_set_background);

        // 自动读取并显示版本号
        try {
            PackageInfo packageInfo = requireActivity().getPackageManager().getPackageInfo(requireActivity().getPackageName(), 0);
            tvVersion.setText(packageInfo.versionName);
        } catch (PackageManager.NameNotFoundException e) {
            tvVersion.setText("1.0.0");
        }

        // 使用说明弹窗
        layoutUsage.setOnClickListener(v -> {
            new AlertDialog.Builder(requireContext())
                    .setTitle("使用说明")
                    .setMessage("1. 首次使用请先开启「所有文件访问权限」\n2. 自动扫描：自动扫描手机内的NCM格式音频文件\n3. 手动选择：手动选择需要转换的NCM文件\n4. 转换后的FLAC文件保存在手机根目录的NCM2FLAC文件夹中")
                    .setPositiveButton("我知道了", null)
                    .show();
        });

        // 检查权限功能
        layoutCheckPermission.setOnClickListener(v -> {
            if (hasAllFilePermission()) {
                Toast.makeText(getContext(), "已获得所有文件访问权限", Toast.LENGTH_SHORT).show();
            } else {
                // 跳转到系统权限设置页
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.setData(Uri.parse("package:" + requireActivity().getPackageName()));
                    startActivity(intent);
                }
            }
        });

        // 自定义背景功能
        layoutSetBackground.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/*");
            selectBackgroundLauncher.launch(intent);
        });

        return view;
    }

    // 权限判断方法
    private boolean hasAllFilePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return requireActivity().checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                    && requireActivity().checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        } else {
            return true;
        }
    }
}
