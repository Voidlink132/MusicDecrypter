package com.ncmconverter.app;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

public class ManualPage {
    private Context mContext;
    private LinearLayout mRootView;
    private ScrollView mScrollView;
    private EditText mInputPath, mOutputPath;
    public static final int REQ_SELECT = 300;

    // 构造方法：传入主页面上下文
    public ManualPage(Context context) {
        this.mContext = context;
        initView();
    }

    // 初始化页面视图（单独写手动页的布局+逻辑）
    private void initView() {
        // 滚动布局：避免内容超出屏幕
        mScrollView = new ScrollView(mContext);
        mScrollView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 内容布局
        mRootView = new LinearLayout(mContext);
        mRootView.setOrientation(LinearLayout.VERTICAL);
        mRootView.setPadding(32, 32, 32, 32);
        mScrollView.addView(mRootView);

        // NCM路径输入
        TextView tv1 = new TextView(mContext);
        tv1.setText("NCM文件路径：");
        tv1.setTextSize(16);
        mRootView.addView(tv1);

        mInputPath = new EditText(mContext);
        mInputPath.setHint("点击下方按钮选择NCM文件");
        mRootView.addView(mInputPath);

        // 选择文件按钮
        Button btnSelect = new Button(mContext);
        btnSelect.setText("选择NCM文件");
        btnSelect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("*/*");
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                ((MainActivity)mContext).startActivityForResult(intent, REQ_SELECT);
            }
        });
        mRootView.addView(btnSelect);

        // 输出路径输入
        TextView tv2 = new TextView(mContext);
        tv2.setText("FLAC输出路径：");
        tv2.setTextSize(16);
        tv2.setPadding(0, 16, 0, 0);
        mRootView.addView(tv2);

        mOutputPath = new EditText(mContext);
        mOutputPath.setText("/sdcard/NCM2FLAC/");
        mRootView.addView(mOutputPath);

        // 开始转换按钮
        Button btnConvert = new Button(mContext);
        btnConvert.setText("开始转换");
        btnConvert.setPadding(0, 16, 0, 0);
        btnConvert.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String inPathStr = mInputPath.getText().toString().trim();
                String outPathStr = mOutputPath.getText().toString().trim();
                if (inPathStr.isEmpty() || outPathStr.isEmpty()) {
                    Toast.makeText(mContext, "路径不能为空", Toast.LENGTH_SHORT).show();
                    return;
                }
                // 核心修复：加final修饰，允许匿名内部类引用
                final File inFile = new File(inPathStr);
                final String finalOutPath = outPathStr;
                if (!inFile.exists() || !inFile.isFile()) {
                    Toast.makeText(mContext, "文件不存在或不是有效文件", Toast.LENGTH_SHORT).show();
                    return;
                }
                // 后台转换
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        final boolean isOk = NcmTool.convert(inFile, new File(finalOutPath));
                        if (isOk && ((MainActivity)mContext).isDownloadLrc) {
                            LrcTool.download(inFile.getName().replace(".ncm", ""), new File(finalOutPath), mContext);
                        }
                        ((MainActivity)mContext).runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(mContext, isOk ? "转换成功" : "转换失败", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                }).start();
            }
        });
        mRootView.addView(btnConvert);
    }

    // 供主页面调用：设置选择的文件路径
    public void setSelectPath(String path) {
        mInputPath.setText(path);
        Toast.makeText(mContext, "文件已选择", Toast.LENGTH_SHORT).show();
    }

    // 提供给主页面的：获取当前页视图（直接返回滚动布局，解决布局嵌套报错）
    public View getView() {
        return mScrollView;
    }
}
