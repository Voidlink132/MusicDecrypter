package com.ncmconverter.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class MainActivity extends Activity {
    // 全局页面容器
    public FrameLayout pageContainer;
    // 全局功能开关（给所有页面调用）
    public boolean isDownloadLrc = true;
    // 页面实例（用于回调传值）
    private AutoPage autoPage;
    private ManualPage manualPage;
    private SettingsPage settingsPage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 主布局：垂直布局（容器+底部Tab）
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 1. 页面容器（占满屏幕大部分，用来加载其他页面）
        pageContainer = new FrameLayout(this);
        pageContainer.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));
        root.addView(pageContainer);

        // 2. 底部Tab导航（和HTML导航栏完全一致）
        RadioGroup bottomTab = new RadioGroup(this);
        bottomTab.setOrientation(RadioGroup.HORIZONTAL);
        bottomTab.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        bottomTab.setGravity(Gravity.CENTER);
        bottomTab.setBackgroundColor(0xFFF5F5F5);

        // 创建3个Tab按钮
        RadioButton tabAuto = createTabBtn("自动", 1);
        RadioButton tabManual = createTabBtn("手动", 2);
        RadioButton tabSettings = createTabBtn("设置", 3);
        bottomTab.addView(tabAuto);
        bottomTab.addView(tabManual);
        bottomTab.addView(tabSettings);

        // 初始化页面实例
        autoPage = new AutoPage(this);
        manualPage = new ManualPage(this);
        settingsPage = new SettingsPage(this);

        // Tab切换监听（核心：加载对应页面）
        bottomTab.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                pageContainer.removeAllViews(); // 清空容器
                if (checkedId == 1) {
                    pageContainer.addView(autoPage.getView());
                } else if (checkedId == 2) {
                    pageContainer.addView(manualPage.getView());
                } else if (checkedId == 3) {
                    pageContainer.addView(settingsPage.getView());
                }
            }
        });

        root.addView(bottomTab);
        setContentView(root);

        // 初始化检查权限+默认加载自动页
        PermTool.checkStoragePerm(this);
        pageContainer.addView(autoPage.getView());
    }

    // 封装Tab按钮创建方法（避免重复代码）
    private RadioButton createTabBtn(String text, int id) {
        RadioButton btn = new RadioButton(this);
        btn.setText(text);
        btn.setId(id);
        btn.setButtonDrawable(null); // 隐藏单选圆点
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(0, 16, 0, 16);
        RadioGroup.LayoutParams params = new RadioGroup.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        btn.setLayoutParams(params);
        return btn;
    }

    // 核心修复：接收手动选择文件的回调（解决ManualPage文件选择无响应）
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == ManualPage.REQ_SELECT && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                // 将Uri转为真实文件路径
                String realPath = getRealPathFromUri(uri);
                if (realPath != null) {
                    manualPage.setSelectPath(realPath);
                }
            }
        }
    }

    // 辅助方法：Uri转真实文件路径（适配Android所有版本）
    private String getRealPathFromUri(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            File tempFile = new File(getCacheDir(), "temp_ncm.ncm");
            FileOutputStream fos = new FileOutputStream(tempFile);
            byte[] buffer = new byte[4096];
            int len;
            while ((len = is.read(buffer)) != -1) {
                fos.write(buffer, 0, len);
            }
            is.close();
            fos.close();
            return tempFile.getAbsolutePath();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
