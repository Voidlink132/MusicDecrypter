package com.ncmconverter.app;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class SettingsPage {
    private Context mContext;
    private LinearLayout mRootView;
    private ScrollView mScrollView;
    private static final String VERSION = "v1.1";
    private static final String UPDATE_LOG = "v1.1 更新日志：\n" +
            "1. 新增LRC歌词自动下载，支持开关控制\n" +
            "2. 按HTML逻辑拆分文件，一个功能一个文件\n" +
            "3. 修复NCM解密头偏移，FLAC可正常播放\n" +
            "4. 适配Android15+所有文件访问权限\n" +
            "5. 纯Termux编译，无外部依赖，无报错";

    // 构造方法：传入主页面上下文
    public SettingsPage(Context context) {
        this.mContext = context;
        initView();
    }

    // 初始化页面视图（单独写设置页的布局+逻辑）
    private void initView() {
        mScrollView = new ScrollView(mContext);
        mScrollView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        mRootView = new LinearLayout(mContext);
        mRootView.setOrientation(LinearLayout.VERTICAL);
        mRootView.setPadding(32, 32, 32, 32);
        mScrollView.addView(mRootView);

        // 版本号
        TextView tvVer = new TextView(mContext);
        tvVer.setText("当前版本：" + VERSION);
        tvVer.setTextSize(18);
        tvVer.setPadding(0, 0, 0, 16);
        mRootView.addView(tvVer);

        // 更新日志
        TextView tvLogTitle = new TextView(mContext);
        tvLogTitle.setText("更新日志：");
        tvLogTitle.setTextSize(16);
        mRootView.addView(tvLogTitle);

        TextView tvLog = new TextView(mContext);
        tvLog.setText(UPDATE_LOG);
        tvLog.setTextSize(14);
        tvLog.setPadding(0, 8, 0, 24);
        mRootView.addView(tvLog);

        // LRC下载开关
        Switch lrcSwitch = new Switch(mContext);
        lrcSwitch.setText("自动下载LRC歌词");
        lrcSwitch.setChecked(((MainActivity)mContext).isDownloadLrc);
        lrcSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                ((MainActivity)mContext).isDownloadLrc = isChecked;
                Toast.makeText(mContext, isChecked ? "已开启LRC下载" : "已关闭LRC下载", Toast.LENGTH_SHORT).show();
            }
        });
        mRootView.addView(lrcSwitch);

        // 权限检测
        TextView tvPermTitle = new TextView(mContext);
        tvPermTitle.setText("权限检测状态：");
        tvPermTitle.setTextSize(16);
        tvPermTitle.setPadding(0, 24, 0, 8);
        mRootView.addView(tvPermTitle);

        TextView tvPerm = new TextView(mContext);
        if (PermTool.hasStoragePerm(mContext)) {
            tvPerm.setText("✅ 已获得所有文件访问权限");
            tvPerm.setTextColor(Color.parseColor("#00C851"));
        } else {
            tvPerm.setText("❌ 未获得所有文件访问权限");
            tvPerm.setTextColor(Color.parseColor("#FF4444"));
        }
        mRootView.addView(tvPerm);

        // 去设置权限按钮
        Button btnSetPerm = new Button(mContext);
        btnSetPerm.setText("立即去设置权限");
        btnSetPerm.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        btnSetPerm.setPadding(0, 16, 0, 0);
        btnSetPerm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PermTool.goSetPerm(mContext);
            }
        });
        mRootView.addView(btnSetPerm);
    }

    // 提供给主页面的：获取当前页视图（直接返回滚动布局，解决布局报错）
    public View getView() {
        return mScrollView;
    }
}
