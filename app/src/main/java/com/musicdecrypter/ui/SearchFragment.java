package com.musicdecrypter.ui;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
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

public class SearchFragment extends Fragment implements MainActivity.OnEngineStateChangeListener, DecryptBridge.DecryptCallback {

    private LinearLayout llProgressArea;
    private TextView tvDecryptStep;
    private ProgressBar decryptProgressBar;
    private RecyclerView rvMusicFiles;
    private MusicGroupAdapter adapter;

    private final Map<String, List<MusicFileItem>> musicGroupMap = new HashMap<>();
    private final List<MusicGroupItem> musicGroupList = new ArrayList<>();
    private final List<MusicFileItem> pendingDecryptList = new ArrayList<>();
    private int currentDecryptIndex = 0;
    private int totalDecryptCount = 0;
    private MainActivity mainActivity;

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
        return inflater.inflate(R.layout.fragment_search, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // 绑定控件
        llProgressArea = view.findViewById(R.id.ll_progress_area);
        tvDecryptStep = view.findViewById(R.id.tv_decrypt_step);
        decryptProgressBar = view.findViewById(R.id.decrypt_progress_bar);
        rvMusicFiles = view.findViewById(R.id.rv_music_files);

        // 初始化分类列表
        rvMusicFiles.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new MusicGroupAdapter(musicGroupList, item -> {
            startSingleDecrypt(item);
        });
        rvMusicFiles.setAdapter(adapter);

        checkStoragePermission();
        loadMusicFileList();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getActivity() instanceof MainActivity) {
            mainActivity = (MainActivity) getActivity();
            mainActivity.addEngineStateListener(this);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mainActivity != null) {
            mainActivity.removeEngineStateListener(this);
        }
    }

    // 引擎状态回调，解决重复初始化
    @Override
    public void onEngineStateChange(int state, String message) {
        if (!isAdded() || getContext() == null) return;
        requireActivity().runOnUiThread(() -> {
            switch (state) {
                case MainActivity.ENGINE_STATE_LOADING:
                    llProgressArea.setVisibility(View.VISIBLE);
                    tvDecryptStep.setText(message);
                    decryptProgressBar.setIndeterminate(true);
                    break;
                case MainActivity.ENGINE_STATE_READY:
                    llProgressArea.setVisibility(View.GONE);
                    decryptProgressBar.setIndeterminate(false);
                    decryptProgressBar.setProgress(0);
                    Toast.makeText(requireContext(), "解密引擎就绪", Toast.LENGTH_SHORT).show();
                    break;
                case MainActivity.ENGINE_STATE_ERROR:
                case MainActivity.ENGINE_STATE_TIMEOUT:
                    llProgressArea.setVisibility(View.VISIBLE);
                    tvDecryptStep.setText(message);
                    decryptProgressBar.setIndeterminate(false);
                    decryptProgressBar.setProgress(0);
                    Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                    break;
            }
        });
    }

    private void checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(android.net.Uri.parse("package:" + requireContext().getPackageName()));
                startActivity(intent);
                Toast.makeText(requireContext(), "请授予全部文件访问权限", Toast.LENGTH_LONG).show();
            }
        }
    }

    // 按平台分类加载文件，实现KR2样式
    private void loadMusicFileList() {
        musicGroupMap.clear();
        musicGroupList.clear();

        List<FileScannerUtils.MusicFileInfo> allFiles = FileScannerUtils.scanAllMusicFiles();
        // 按平台分组
        for (FileScannerUtils.MusicFileInfo fileInfo : allFiles) {
            String platform = fileInfo.platform;
            if (!musicGroupMap.containsKey(platform)) {
                musicGroupMap.put(platform, new ArrayList<>());
            }
            musicGroupMap.get(platform).add(new MusicFileItem(platform, fileInfo.fileName, fileInfo.fullPath));
        }

        // 生成分类列表
        for (Map.Entry<String, List<MusicFileItem>> entry : musicGroupMap.entrySet()) {
            musicGroupList.add(new MusicGroupItem(entry.getKey(), entry.getValue()));
        }

        adapter.notifyDataSetChanged();
        if (musicGroupList.isEmpty()) {
            Toast.makeText(requireContext(), "未找到加密音乐文件", Toast.LENGTH_SHORT).show();
        }
    }

    private void startSingleDecrypt(MusicFileItem item) {
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
                    Toast.makeText(requireContext(), "解密完成！文件已保存到 " + SpUtils.getSavePath(requireContext()), Toast.LENGTH_LONG).show();
                });
            }
            return;
        }

        MusicFileItem currentItem = pendingDecryptList.remove(0);
        currentDecryptIndex++;

        if (isAdded() && getContext() != null) {
            requireActivity().runOnUiThread(() -> {
                llProgressArea.setVisibility(View.VISIBLE);
                tvDecryptStep.setText(String.format("正在解密(%d/%d)：%s", currentDecryptIndex, totalDecryptCount, currentItem.getFileName()));
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
            if (step >= 0 && step < stepTexts.length) {
                tvDecryptStep.setText(stepTexts[step]);
            }
        });
    }

    @Override
    public void onDecryptSuccess(String fileName, byte[] fileData) {
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

            requireActivity().runOnUiThread(() -> {
                Toast.makeText(requireContext(), "解密成功！已保存到：\n" + outFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
            });

        } catch (Exception e) {
            requireActivity().runOnUiThread(() -> {
                Toast.makeText(requireContext(), "保存失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
        }
        startDecryptQueue();
    }

    @Override
    public void onDecryptFailed(String errorMsg) {
        if (isAdded() && getContext() != null) {
            requireActivity().runOnUiThread(() -> {
                llProgressArea.setVisibility(View.GONE);
                Toast.makeText(requireContext(), "解密失败：" + errorMsg, Toast.LENGTH_SHORT).show();
            });
        }
        startDecryptQueue();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadMusicFileList();
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

    // 分类列表适配器（KR2样式）
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

        private void refreshDisplayList() {
            displayList.clear();
            for (MusicGroupItem group : groupList) {
                displayList.add(group); // 分类标题
                displayList.addAll(group.getFileList()); // 分类下的文件
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
            MaterialButton btnDecrypt;

            FileItemVH(View itemView) {
                super(itemView);
                tvFileName = itemView.findViewById(R.id.tv_file_name);
                btnDecrypt = itemView.findViewById(R.id.btn_decrypt);
            }
        }
    }
}
