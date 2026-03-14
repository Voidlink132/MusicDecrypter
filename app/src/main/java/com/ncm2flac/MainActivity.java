package com.ncm2flac;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.io.InputStream;

public class MainActivity extends AppCompatActivity {

    private LinearLayout layoutPermissionDenied;
    private LinearLayout layoutContent;
    private View rootLayout;
    private SharedPreferences sp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        sp = getSharedPreferences("app_config", 0);

        rootLayout = findViewById(R.id.root_layout);
        layoutPermissionDenied = findViewById(R.id.layout_permission_denied);
        layoutContent = findViewById(R.id.layout_content);

        Button btnAutoScan = findViewById(R.id.btn_auto_scan);
        Button btnManualSelect = findViewById(R.id.btn_manual_select);

        // 无权限跳转设置
        View.OnClickListener toPermissionSetting = v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        };
        btnAutoScan.setOnClickListener(toPermissionSetting);
        btnManualSelect.setOnClickListener(toPermissionSetting);

        // 加载背景
        updateBackground();
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkPermission();
    }

    private void checkPermission() {
        if (hasAllFilePermission()) {
            layoutPermissionDenied.setVisibility(View.GONE);
            layoutContent.setVisibility(View.VISIBLE);
            initViewPager();
        } else {
            layoutPermissionDenied.setVisibility(View.VISIBLE);
            layoutContent.setVisibility(View.GONE);
            Toast.makeText(this, "权限被拒绝", Toast.LENGTH_LONG).show();
        }
    }

    // 权限判断
    private boolean hasAllFilePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        } else
