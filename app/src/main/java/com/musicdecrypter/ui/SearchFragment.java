package com.musicdecrypter.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.musicdecrypter.R;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;

/**
 * 仅保留文件扫描和列表展示功能，解密功能跳转至内嵌网页完成
 */
public class SearchFragment extends Fragment {

    private static final String TAG = "SearchFragment";
    private static final int REQUEST_STORAGE_PERMISSION = 1001;

    // 控件绑定（移除解密进度相关控件）
    private RecyclerView rvMusicFiles;
    private TextView tvEmptyTip;
    private Button btnRefreshScan;
    private MusicGroupAdapter adapter;

    // 数据存储（仅保留文件列表相关）
    private final Map<String, List<MusicFileItem>> musicGroupMap = new HashMap<>();
    private final List<MusicGroupItem> musicGroupList = new ArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView: 初始化查找页面（仅扫描功能）");
        return inflater.inflate(R.layout.fragment_search_scan_only, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bindViews(view);
        initRecyclerView();
        // 刷新扫描按钮事件
        btnRefreshScan.setOnClickListener(v -> checkStoragePermissionAndScan());
        // 首次启动扫描
        checkStoragePermissionAndScan();
    }

    private void bindViews(View view) {
        rvMusicFiles = view.findViewById(R.id.rv_music_files);
        tvEmptyTip = view.findViewById(R.id.tv_empty_tip);
        btnRefreshScan = view.findViewById(R.id.btn_refresh_scan);
        // 移除解密进度相关控件绑定（llProgressArea、tvDecryptStep等）
    }

    private void initRecyclerView() {
        rvMusicFiles.setLayoutManager(new LinearLayoutManager(requireContext()));
        // 列表点击事件：提示跳转至解密页面
        adapter = new MusicGroupAdapter(musicGroupList, item -> {
            Toast.makeText(requireContext(), "请切换至「解密」页面，上传文件完成解密", Toast.LENGTH_SHORT).show();
            // 可选：自动切换到解密页面（若需要）
            if (getActivity() instanceof com.musicdecrypter.MainActivity) {
                ((com.musicdecrypter.MainActivity) getActivity()).findViewById(R.id.nav_decrypt).performClick();
            }
        });
        rvMusicFiles.setAdapter(adapter);
        Log.d(TAG, "initRecyclerView: 列表初始化完成");
    }

    @Override
    public void onResume() {
        super.onResume();
        // 页面回到前台，重新扫描
        checkStoragePermissionAndScan();
    }

    /**
     * 权限校验（保留原逻辑）
     */
    private void checkStoragePermissionAndScan() {
        Log.d(TAG, "checkStoragePermissionAndScan: 校验存储权限");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                tvEmptyTip.setText("请授予全部文件访问权限，否则无法读取音乐文件");
                tvEmptyTip.setVisibility(View.VISIBLE);
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(android.net.Uri.parse("package:" + requireContext().getPackageName()));
                startActivity(intent);
                return;
            }
        } else {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(requireActivity(),
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        REQUEST_STORAGE_PERMISSION);
                return;
            }
        }
        startScanMusicFiles();
    }

    /**
     * 保留原扫描逻辑（全路径覆盖+去重）
     */
    private void startScanMusicFiles() {
        if (!isAdded()) return;
        btnRefreshScan.setEnabled(false);
        btnRefreshScan.setText("扫描中...");
        tvEmptyTip.setVisibility(View.GONE);
        Log.d(TAG, "startScanMusicFiles: 启动文件扫描");

        Executors.newSingleThreadExecutor().execute(() -> {
            musicGroupMap.clear();
            musicGroupList.clear();
            Set<String> scannedFilePaths = new HashSet<>();

            // 全路径扫描加密音乐文件
            List<File> allMusicFiles = scanAllEncryptedMusicFiles();
            Log.d(TAG, "扫描完成，找到加密文件数：" + allMusicFiles.size());

            // 按平台分组+去重
            for (File file : allMusicFiles) {
                String filePath = file.getAbsolutePath();
                if (scannedFilePaths.contains(filePath)) continue;
                scannedFilePaths.add(filePath);

                String fileName = file.getName();
                String platform = getPlatformByFileName(fileName);
                if (!musicGroupMap.containsKey(platform)) {
                    musicGroupMap.put(platform, new ArrayList<>());
                }
                musicGroupMap.get(platform).add(new MusicFileItem(platform, fileName, filePath));
            }

            // 生成分类列表
            for (Map.Entry<String, List<MusicFileItem>> entry : musicGroupMap.entrySet()) {
                musicGroupList.add(new MusicGroupItem(entry.getKey(), entry.getValue()));
            }

            // 主线程更新UI
            mainHandler.post(() -> {
                if (!isAdded()) return;
                btnRefreshScan.setEnabled(true);
                btnRefreshScan.setText("重新扫描");
                adapter.refreshData(musicGroupList);

                if (musicGroupList.isEmpty()) {
                    tvEmptyTip.setVisibility(View.VISIBLE);
                    tvEmptyTip.setText("未找到本地加密音乐文件");
                } else {
                    tvEmptyTip.setVisibility(View.GONE);
                }
            });
        });
    }

    /**
     * 扫描所有加密音乐文件（保留原逻辑）
     */
    private List<File> scanAllEncryptedMusicFiles() {
        List<File> result = new ArrayList<>();
        String rootPath = Environment.getExternalStorageDirectory().getAbsolutePath();

        // 主流音乐APP存储目录
        String[] scanPaths = {
                rootPath + "/Download/netease/cloudmusic/Music/",
                rootPath + "/netease/cloudmusic/Music/",
                rootPath + "/Music/qqmusic/song/",
                rootPath + "/QQMusic/song/",
                rootPath + "/tencent/QQMusic/song/",
                rootPath + "/Download/kgmusic/download/",
                rootPath + "/KuGou/download/",
                rootPath + "/kuwo/download/music/",
                rootPath + "/Download/",
                rootPath + "/Music/",
                rootPath + "/Android/data/com.netease.cloudmusic/files/Download/",
                rootPath + "/Android/data/com.tencent.qqmusic/files/Music/",
                rootPath + "/Android/data/com.kugou.android/files/download/"
        };

        // 支持的加密格式
        String[] supportExts = {".ncm", ".mgg", ".mflac", ".kgm", ".kgma", ".qmc0", ".qmc1", ".qmcflac"};

        // 遍历目录扫描
        for (String path : scanPaths) {
            File dir = new File(path);
            if (!dir.exists() || !dir.isDirectory()) continue;
            scanDirRecursive(dir, supportExts, result);
        }
        return result;
    }

    /**
     * 递归扫描子目录（保留原逻辑）
     */
    private void scanDirRecursive(File dir, String[] supportExts, List<File> result) {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                scanDirRecursive(file, supportExts, result);
            } else {
                String fileName = file.getName().toLowerCase();
                for (String ext : supportExts) {
                    if (fileName.endsWith(ext)) {
                        result.add(file);
                        break;
                    }
                }
            }
        }
    }

    /**
     * 根据文件名判断平台（保留原逻辑）
     */
    private String getPlatformByFileName(String fileName) {
        String ext = fileName.substring(fileName.lastIndexOf(".")).toLowerCase();
        switch (ext) {
            case ".ncm": return "网易云音乐";
            case ".mgg":
            case ".mflac":
            case ".qmc0":
            case ".qmc1":
            case ".qmcflac": return "QQ音乐";
            case ".kgm":
            case ".kgma": return "酷狗音乐";
            default: return "其他平台";
        }
    }

    // ==================== 列表实体+适配器（保留原逻辑，修改按钮点击事件） ====================
    public static class MusicGroupItem {
        private final String platform;
        private final List<MusicFileItem> fileList;

        public MusicGroupItem(String platform, List<MusicFileItem> fileList) {
            this.platform = platform;
            this.fileList = fileList;
        }

        public String getPlatform() { return platform; }
        public List<MusicFileItem> getFileList() { return fileList; }
    }

    public static class MusicFileItem {
        private final String platform;
        private final String fileName;
        private final String filePath;

        public MusicFileItem(String platform, String fileName, String filePath) {
            this.platform = platform;
            this.fileName = fileName;
            this.filePath = filePath;
        }

        public String getPlatform() { return platform; }
        public String getFileName() { return fileName; }
        public String getFilePath() { return filePath; }
    }

    public static class MusicGroupAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int TYPE_GROUP_HEADER = 0;
        private static final int TYPE_FILE_ITEM = 1;
        private final List<MusicGroupItem> groupList;
        private final List<Object> displayList = new ArrayList<>();
        private final OnItemClickListener listener;

        public interface OnItemClickListener {
            void onItemClick(MusicFileItem item);
        }

        public MusicGroupAdapter(List<MusicGroupItem> groupList, OnItemClickListener listener) {
            this.groupList = groupList;
            this.listener = listener;
            refreshDisplayList();
        }

        public void refreshData(List<MusicGroupItem> newGroupList) {
            groupList.clear();
            groupList.addAll(newGroupList);
            refreshDisplayList();
            notifyDataSetChanged();
        }

        private void refreshDisplayList() {
            displayList.clear();
            for (MusicGroupItem group : groupList) {
                displayList.add(group);
                displayList.addAll(group.getFileList());
            }
        }

        @Override
        public int getItemViewType(int position) {
            Object item = displayList.get(position);
            return item instanceof MusicGroupItem ? TYPE_GROUP_HEADER : TYPE_FILE_ITEM;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            if (viewType == TYPE_GROUP_HEADER) {
                View view = inflater.inflate(R.layout.item_group_header, parent, false);
                return new GroupHeaderVH(view);
            } else {
                View view = inflater.inflate(R.layout.item_music_file_scan_only, parent, false);
                return new FileItemVH(view);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Object item = displayList.get(position);
            if (holder instanceof GroupHeaderVH) {
                MusicGroupItem group = (MusicGroupItem) item;
                ((GroupHeaderVH) holder).tvGroupTitle.setText(group.getPlatform());
            } else if (holder instanceof FileItemVH) {
                MusicFileItem fileItem = (MusicFileItem) item;
                FileItemVH vh = (FileItemVH) holder;
                vh.tvFileName.setText(fileItem.getFileName());
                // 按钮点击：触发回调（提示跳转解密页面）
                vh.btnGotoDecrypt.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onItemClick(fileItem);
                    }
                });
            }
        }

        @Override
        public int getItemCount() {
            return displayList.size();
        }

        static class GroupHeaderVH extends RecyclerView.ViewHolder {
            TextView tvGroupTitle;
            GroupHeaderVH(View itemView) {
                super(itemView);
                tvGroupTitle = itemView.findViewById(R.id.tv_group_title);
            }
        }

        static class FileItemVH extends RecyclerView.ViewHolder {
            TextView tvFileName;
            Button btnGotoDecrypt;
            FileItemVH(View itemView) {
                super(itemView);
                tvFileName = itemView.findViewById(R.id.tv_file_name);
                btnGotoDecrypt = itemView.findViewById(R.id.btn_goto_decrypt);
            }
        }
    }
}
