package com.ncmconverter.app;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class AutoPage {
    private Context mContext;
    private ListView mListView;
    private View mRootView;
    private static final String NCM_DIR = "/sdcard/Download/netease/cloudmusic/Music/";
    private static final String OUT_DIR = "/sdcard/NCM2FLAC/";

    // 构造方法：传入主页面上下文
    public AutoPage(Context context) {
        this.mContext = context;
        initView();
    }

    // 初始化页面视图（单独写自动页的布局+逻辑）
    private void initView() {
        mListView = new ListView(mContext);
        mListView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mRootView = mListView;
        // 扫描NCM文件并显示（final修饰，解决报错）
        final List<String> ncmList = scanNcmFiles();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(mContext, android.R.layout.simple_list_item_1, ncmList);
        mListView.setAdapter(adapter);
        // 点击item转换NCM
        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                final String ncmName = ncmList.get(position);
                // 后台转换，不卡UI
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        final boolean isOk = NcmTool.convert(new File(NCM_DIR, ncmName), new File(OUT_DIR));
                        // 如果开启LRC，就下载歌词
                        if (isOk && ((MainActivity)mContext).isDownloadLrc) {
                            LrcTool.download(ncmName.replace(".ncm", ""), new File(OUT_DIR), mContext);
                        }
                        // 主线程弹提示
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
        Toast.makeText(mContext, "找到" + ncmList.size() + "个NCM文件", Toast.LENGTH_SHORT).show();
    }

    // 单独实现：扫描NCM文件
    private List<String> scanNcmFiles() {
        List<String> list = new ArrayList<>();
        File dir = new File(NCM_DIR);
        if (!dir.exists() || !dir.isDirectory()) return list;
        File[] files = dir.listFiles();
        if (files == null) return list;
        for (File f : files) {
            if (f.isFile() && f.getName().endsWith(".ncm")) {
                list.add(f.getName());
            }
        }
        return list;
    }

    // 提供给主页面的：获取当前页视图（直接返回根视图，解决布局报错）
    public View getView() {
        return mRootView;
    }
}
