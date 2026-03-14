package com.ncm2flac;

import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ncm2flac.adapter.NcmFileAdapter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class AutoScanFragment extends Fragment {

    private RecyclerView rvNcmFiles;
    private NcmFileAdapter adapter;
    private List<File> ncmFileList = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_auto_scan, container, false);
        rvNcmFiles = view.findViewById(R.id.rv_ncm_files);
        rvNcmFiles.setLayoutManager(new LinearLayoutManager(getContext()));
        
        // 修复：这里必须传入第三个参数 OnConvertListener（暂时传 null 或空实现，不影响功能）
        adapter = new NcmFileAdapter(getContext(), ncmFileList, null);
        rvNcmFiles.setAdapter(adapter);
        
        scanNcmFiles();
        return view;
    }

    private void scanNcmFiles() {
        ncmFileList.clear();
        // 扫描网易云音乐默认下载目录
        File musicDir = new File(Environment.getExternalStorageDirectory(), "/Download/netease/cloudmusic/Music/");
        if (musicDir.exists() && musicDir.isDirectory()) {
            File[] files = musicDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.getName().endsWith(".ncm")) {
                        ncmFileList.add(file);
                    }
                }
            }
        }
        // 扫描根目录，跳过系统文件夹
        File rootDir = Environment.getExternalStorageDirectory();
        scanDir(rootDir);
        
        adapter.notifyDataSetChanged();
        Toast.makeText(getContext(), "找到 " + ncmFileList.size() + " 个NCM文件", Toast.LENGTH_LONG).show();
    }

    private void scanDir(File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        
        for (File file : files) {
            if (file.isDirectory()) {
                if (!file.getAbsolutePath().contains("/Android/")) {
                    scanDir(file);
                }
            } else {
                if (file.getName().endsWith(".ncm") && !ncmFileList.contains(file)) {
                    ncmFileList.add(file);
                }
            }
        }
    }
}
