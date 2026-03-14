package com.ncm2flac;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
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

        // 无权限时跳转到权限设置页
        View.OnClickListener toPermissionSetting = v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        };
        btnAutoScan.setOnClickListener(toPermissionSetting);
        btnManualSelect.setOnClickListener(toPermissionSetting);

        // 启动时加载自定义背景
        updateBackground();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 每次回到页面都检查权限
        checkPermission();
    }

    private void checkPermission() {
        if (hasAllFilePermission()) {
            // 有权限：显示内容布局
            layoutPermissionDenied.setVisibility(View.GONE);
            layoutContent.setVisibility(View.VISIBLE);
            initViewPager();
        } else {
            // 无权限：显示权限提示
            layoutPermissionDenied.setVisibility(View.VISIBLE);
            layoutContent.setVisibility(View.GONE);
            Toast.makeText(this, "权限被拒绝，无法使用转换功能", Toast.LENGTH_LONG).show();
        }
    }

    // 核心权限判断方法
    private boolean hasAllFilePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+：判断是否有所有文件访问权限
            return Environment.isExternalStorageManager();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6-10：判断读写权限
            return checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == getPackageManager().PERMISSION_GRANTED
                    && checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == getPackageManager().PERMISSION_GRANTED;
        } else {
            // Android 6以下：默认有权限
            return true;
        }
    }

    private void initViewPager() {
        ViewPager2 viewPager = findViewById(R.id.view_pager);
        TabLayout tabLayout = findViewById(R.id.tab_layout);
        ViewPagerAdapter adapter = new ViewPagerAdapter(this);
        viewPager.setAdapter(adapter);
        viewPager.setOffscreenPageLimit(3);

        // 绑定Tab和ViewPager
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            switch (position) {
                case 0:
                    tab.setText("自动");
                    break;
                case 1:
                    tab.setText("手动");
                    break;
                case 2:
                    tab.setText("设置");
                    break;
            }
        }).attach();
    }

    // 公开方法：供设置页调用，更新APP背景
    public void updateBackground() {
        String backgroundUri = sp.getString("background_uri", "");
        if (!backgroundUri.isEmpty()) {
            try {
                Uri uri = Uri.parse(backgroundUri);
                InputStream inputStream = getContentResolver().openInputStream(uri);
                rootLayout.setBackgroundDrawable(android.graphics.drawable.Drawable.createFromStream(inputStream, uri.toString()));
                inputStream.close();
            } catch (Exception e) {
                e.printStackTrace();
                rootLayout.setBackgroundColor(0xFFFFFFFF);
            }
        } else {
            rootLayout.setBackgroundColor(0xFFFFFFFF);
        }
    }
}
