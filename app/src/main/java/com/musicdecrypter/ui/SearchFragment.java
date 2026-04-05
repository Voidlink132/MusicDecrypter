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
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.musicdecrypter.MainActivity;
import com.musicdecrypter.R;
import com.musicdecrypter.utils.DecryptBridge;
import com.musicdecrypter.utils.FileScannerUtils;
import com.musicdecrypter.utils.SpUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

public class SearchFragment extends Fragment implements MainActivity.OnEngineStateChangeListener, DecryptBridge.DecryptCallback {

    private static final String TAG = "MusicDecrypter_Search";
    private LinearLayout llProgressArea;
    private TextView tvDecryptStep;
    private TextView tvProgressPercent;
    private ProgressBar decryptProgressBar;
    private RecyclerView rvMusicFiles;
    private TextView tvEmptyTip;
    private Button btnRefreshScan;
    private MusicGroupAdapter adapter;

    private final Map<String, List<MusicFileItem>> musicGroupMap = new HashMap<>();
    private final List<MusicGroupItem> musicGroupList = new ArrayList<>();
    private final List<MusicFileItem> pendingDecryptList = new ArrayList<>();
    private int currentDecryptIndex = 0;
    private int totalDecryptCount = 0;
    private MainActivity mainActivity;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final String[] stepTexts = {
            "就绪",
            "正在读取文件...",
            "正在初始化解密引擎...",
            "正在解密文件...",
            "正在保存文件...",
            "解密完成"
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Log.d(TAG, "===== SearchFragment onCreateView 执行 =====");
        return inflater.inflate(R.layout.fragment_search, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Log.d(TAG, "===== SearchFragment onViewCreated 执行 =====");
        // 绑定控件
        llProgressArea = view.findViewById(R.id.ll_progress_area);
        tvDecryptStep = view.findViewById(R.id.tv_decrypt_step);
        tvProgressPercent = view.findViewById(R.id.tv_progress_percent);
        decryptProgressBar = view.findViewById(R.id.decrypt_progress_bar);
        rvMusicFiles = view.findViewById(R.id.rv_music_files);
        tvEmptyTip = view.findViewById(R.id.tv_empty_tip);
        btnRefreshScan = view.findViewById(R.id.btn_refresh_scan);

        // 刷新扫描按钮点击事件
        btnRefreshScan.setOnClickListener(v -> {
            Log.d(TAG, "用户点击重新扫描按钮");
            checkStoragePermissionAndScan();
        });

        // 初始化列表
        rvMusicFiles.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new MusicGroupAdapter(musicGroupList, item -> {
            Log.d(TAG, "用户点击解密文件：" + item.getFileName());
            startSingleDecrypt(item);
        });
        rvMusicFiles.setAdapter(adapter);
        Log.d(TAG, "列表适配器初始化完成");

        // 首次启动校验权限+扫描
        checkStoragePermissionAndScan();
        Log.d(TAG, "===== SearchFragment onViewCreated 完成 =====");
    }

    @Override
    public void onStart() {
        super.onStart();
        Log.d(TAG, "SearchFragment onStart 执行");
        // 绑定引擎状态监听
        if (getActivity() instanceof MainActivity) {
            mainActivity = (MainActivity) getActivity();
            mainActivity.addEngineStateListener(this);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        Log.d(TAG, "SearchFragment onStop 执行");
        // 移除监听，避免内存泄漏
        if (mainActivity != null) {
            mainActivity.removeEngineStateListener(this);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "SearchFragment onResume 执行，重新触发扫描");
        // 页面回到前台时，重新扫描
        checkStoragePermissionAndScan();
    }

    // 引擎状态回调
    @Override
    public void onEngineStateChange(int state, String message) {
        Log.d(TAG, "引擎状态变化：" + state + " | " + message);
        if (!isAdded() || getContext() == null) return;
        requireActivity().runOnUiThread(() -> {
            switch (state) {
                case MainActivity.ENGINE_STATE_LOADING:
                    llProgressArea.setVisibility(View.VISIBLE);
                    tvDecryptStep.setText(message);
                    tvProgressPercent.setVisibility(View.GONE);
                    decryptProgressBar.setIndeterminate(true);
                    break;
                case MainActivity.ENGINE_STATE_READY:
                    llProgressArea.setVisibility(View.GONE);
                    decryptProgressBar.setIndeterminate(false);
                    decryptProgressBar.setProgress(0);
                    tvProgressPercent.setText("0%");
                    break;
                case MainActivity.ENGINE_STATE_ERROR:
                case MainActivity.ENGINE_STATE_TIMEOUT:
                    llProgressArea.setVisibility(View.VISIBLE);
                    tvDecryptStep.setText(message);
                    tvProgressPercent.setVisibility(View.GONE);
                    decryptProgressBar.setIndeterminate(false);
                    decryptProgressBar.setProgress(0);
                    Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                    break;
            }
        });
    }

    // 权限校验+申请
    private void checkStoragePermissionAndScan() {
        Log.d(TAG, "开始校验存储权限");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Log.e(TAG, "未授予全部文件访问权限");
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
                Log.e(TAG, "未授予存储读写权限");
                ActivityCompat.requestPermissions(requireActivity(),
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        1001);
                return;
            }
        }
        Log.d(TAG, "存储权限校验通过，开始扫描文件");
        // 权限已授予，启动扫描
        startScanMusicFiles();
    }

    // 子线程执行文件扫描
    private void startScanMusicFiles() {
        if (!isAdded()) return;
        btnRefreshScan.setEnabled(false);
        btnRefreshScan.setText("扫描中...");
        tvEmptyTip.setVisibility(View.GONE);
        Log.d(TAG, "===== 开始执行文件扫描 =====");

        Executors.newSingleThreadExecutor().execute(() -> {
            musicGroupMap.clear();
            musicGroupList.clear();

            // 执行全量扫描
            List<FileScannerUtils.MusicFileInfo> allFiles = FileScannerUtils.scanAllMusicFiles();
            Log.d(TAG, "扫描完成，找到加密文件总数：" + allFiles.size());

            // 按平台分组
            for (FileScannerUtils.MusicFileInfo fileInfo : allFiles) {
                String platform = fileInfo.platform;
                if (!musicGroupMap.containsKey(platform)) {
                    musicGroupMap.put(platform, new ArrayList<>());
                }
                musicGroupMap.get(platform).add(new MusicFileItem(platform, fileInfo.fileName, fileInfo.fullPath));
                Log.d(TAG, "找到文件：" + fileInfo.fileName + " | 路径：" + fileInfo.fullPath);
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
                Log.d(TAG, "列表适配器已刷新，分类数：" + musicGroupList.size());

                // 空数据提示
                if (musicGroupList.isEmpty()) {
                    tvEmptyTip.setVisibility(View.VISIBLE);
                    tvEmptyTip.setText("未找到本地加密音乐文件");
                    Log.d(TAG, "扫描结果为空，显示空提示");
                } else {
                    tvEmptyTip.setVisibility(View.GONE);
                    Log.d(TAG, "扫描结果非空，隐藏空提示");
                }
            });
        });
    }

    private void startSingleDecrypt(MusicFileItem item) {
        if (mainActivity == null || mainActivity.getEngineState() != MainActivity.ENGINE_STATE_READY) {
            String error = "解密引擎未就绪，请稍候重试";
            Log.e(TAG, error);
            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show();
            return;
        }
        pendingDecryptList.clear();
        pendingDecryptList.add(item);
        currentDecryptIndex = 0;
        totalDecryptCount = 1;
        startDecryptQueue();
    }

    private void startDecryptQueue() {
        if (pendingDecryptList.isEmpty()) {
            if (isAdded() && getContext() != null) {
                requireActivity().runOnUiThread(() -> {
                    llProgressArea.setVisibility(View.GONE);
                    Toast.makeText(requireContext(), "全部解密完成！文件已保存到 " + SpUtils.getSavePath(requireContext()), Toast.LENGTH_LONG).show();
                });
            }
            return;
        }

        MusicFileItem currentItem = pendingDecryptList.remove(0);
        currentDecryptIndex++;
        Log.d(TAG, "开始解密队列，当前第" + currentDecryptIndex + "个，共" + totalDecryptCount + "个");

        if (isAdded() && getContext() != null) {
            requireActivity().runOnUiThread(() -> {
                llProgressArea.setVisibility(View.VISIBLE);
                tvProgressPercent.setVisibility(View.VISIBLE);
                tvDecryptStep.setText(String.format("正在解密(%d/%d)：%s", currentDecryptIndex, totalDecryptCount, currentItem.getFileName()));
                tvProgressPercent.setText("0%");
                decryptProgressBar.setProgress(0);
            });
        }

        if (mainActivity != null) {
            mainActivity.startDecrypt(currentItem.getFilePath(), currentItem.getFileName(), this);
        } else {
            startDecryptQueue();
        }
    }

    @Override
    public void onDecryptProgress(int current, int total, int step) {
        if (!isAdded() || getContext() == null) return;
        requireActivity().runOnUiThread(() -> {
            decryptProgressBar.setProgress(current);
            tvProgressPercent.setText(current + "%");
            if (step >= 0 && step < stepTexts.length) {
                tvDecryptStep.setText(stepTexts[step]);
            }
        });
    }

    @Override
    public void onDecryptSuccess(String fileName, byte[] fileData) {
        Log.d(TAG, "解密成功，文件名：" + fileName);
        if (!isAdded() || getContext() == null) {
            startDecryptQueue();
            return;
        }

        try {
            String saveDirPath = SpUtils.getSavePath(requireContext());
            File saveDir = new File(saveDirPath);
            if (!saveDir.exists()) saveDir.mkdirs();
            File outFile = new File(saveDir, fileName);
            FileOutputStream fos = new FileOutputStream(outFile);
            fos.write(fileData);
            fos.flush();
            fos.close();
            requireContext().sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, android.net.Uri.fromFile(outFile)));
            Log.d(TAG, "文件已保存到：" + outFile.getAbsolutePath());

            requireActivity().runOnUiThread(() -> {
                Toast.makeText(requireContext(), "解密成功！已保存到：\n" + outFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
            });

        } catch (Exception e) {
            Log.e(TAG, "保存文件失败：" + e.getMessage());
            requireActivity().runOnUiThread(() -> {
                Toast.makeText(requireContext(), "保存失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
        }
        startDecryptQueue();
    }

    @Override
    public void onDecryptFailed(String errorMsg) {
        Log.e(TAG, "解密失败：" + errorMsg);
        if (isAdded() && getContext() != null) {
            requireActivity().runOnUiThread(() -> {
                llProgressArea.setVisibility(View.GONE);
                Toast.makeText(requireContext(), "解密失败：" + errorMsg, Toast.LENGTH_SHORT).show();
            });
        }
        startDecryptQueue();
    }

    // 分类列表实体
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

    // 音乐文件实体
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

    // 分类列表适配器
    public static class MusicGroupAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private static final int TYPE_GROUP_HEADER = 0;
        private static final int TYPE_FILE_ITEM = 1;
        private final List<MusicGroupItem> groupList;
        private final List<Object> displayList = new ArrayList<>();
        private final OnItemDecryptListener listener;

        public interface OnItemDecryptListener {
            void onDecryptClick(MusicFileItem item);
        }

        public MusicGroupAdapter(List<MusicGroupItem> groupList, OnItemDecryptListener listener) {
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
            if (item instanceof MusicGroupItem) {
                return TYPE_GROUP_HEADER;
            } else {
                return TYPE_FILE_ITEM;
            }
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            if (viewType == TYPE_GROUP_HEADER) {
                View view = inflater.inflate(R.layout.item_group_header, parent, false);
                return new GroupHeaderVH(view);
            } else {
                View view = inflater.inflate(R.layout.item_music_file, parent, false);
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
                vh.btnDecrypt.setText("解密并下载");
                vh.btnDecrypt.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onDecryptClick(fileItem);
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
            Button btnDecrypt;

            FileItemVH(View itemView) {
                super(itemView);
                tvFileName = itemView.findViewById(R.id.tv_file_name);
                btnDecrypt = itemView.findViewById(R.id.btn_decrypt);
            }
        }
    }
}
