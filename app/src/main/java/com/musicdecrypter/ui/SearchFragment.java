package com.musicdecrypter.ui;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
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

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

public class SearchFragment extends Fragment implements DecryptBridge.DecryptCallback {

    private CheckBox cbSelectAll;
    private MaterialButton btnBatchDecrypt;
    private RecyclerView rvMusicFiles;
    private MusicAdapter adapter;

    private final List<MusicFileItem> musicFileList = new ArrayList<>();
    private final List<MusicFileItem> pendingDecryptList = new ArrayList<>();
    private int currentDecryptIndex = 0;
    private int totalDecryptCount = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_search, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // 控件id和布局完全对应
        cbSelectAll = view.findViewById(R.id.cb_select_all);
        btnBatchDecrypt = view.findViewById(R.id.btn_batch_decrypt);
        rvMusicFiles = view.findViewById(R.id.rv_music_files);

        rvMusicFiles.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new MusicAdapter(musicFileList, new OnItemActionListener() {
            @Override
            public void onDecryptClick(MusicFileItem item) {
                startSingleDecrypt(item);
            }

            @Override
            public void onSelectChange(List<MusicFileItem> selectedList) {
                boolean hasSelected = !selectedList.isEmpty();
                btnBatchDecrypt.setEnabled(hasSelected);
                cbSelectAll.setChecked(selectedList.size() == musicFileList.size() && !musicFileList.isEmpty());
            }
        });
        rvMusicFiles.setAdapter(adapter);

        // 全选逻辑
        cbSelectAll.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed()) {
                adapter.setAllSelected(isChecked);
            }
        });

        // 批量解密逻辑
        btnBatchDecrypt.setOnClickListener(v -> {
            List<MusicFileItem> selected = new ArrayList<>();
            for (MusicFileItem item : musicFileList) {
                if (item.isSelected()) selected.add(item);
            }
            startBatchDecrypt(selected);
        });

        checkStoragePermission();
        loadMusicFileList();
    }

    private void checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(android.net.Uri.parse("package:" + requireContext().getPackageName()));
                startActivity(intent);
                Toast.makeText(requireContext(), "请授予全部文件访问权限，否则无法读取音乐文件", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void loadMusicFileList() {
        musicFileList.clear();
        List<FileScannerUtils.MusicDir> dirs = FileScannerUtils.MUSIC_DIR_LIST;

        for (FileScannerUtils.MusicDir dir : dirs) {
            List<String> fileNames = FileScannerUtils.scanMusicFiles(dir.getPath());
            for (String fileName : fileNames) {
                String fullPath = dir.getPath() + fileName;
                musicFileList.add(new MusicFileItem(dir.getName(), fileName, fullPath));
            }
        }

        adapter.notifyDataSetChanged();
        if (musicFileList.isEmpty()) {
            Toast.makeText(requireContext(), "未找到本地加密音乐文件", Toast.LENGTH_SHORT).show();
        }
    }

    private void startSingleDecrypt(MusicFileItem item) {
        pendingDecryptList.clear();
        pendingDecryptList.add(item);
        currentDecryptIndex = 0;
        totalDecryptCount = 1;
        startDecryptQueue();
    }

    private void startBatchDecrypt(List<MusicFileItem> items) {
        pendingDecryptList.clear();
        pendingDecryptList.addAll(items);
        currentDecryptIndex = 0;
        totalDecryptCount = items.size();
        startDecryptQueue();
    }

    private void startDecryptQueue() {
        if (pendingDecryptList.isEmpty()) {
            if (isAdded() && getContext() != null) {
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(), "解密完成！文件已保存到 下载/MusicDecrypter 目录", Toast.LENGTH_LONG).show();
                    adapter.setAllSelected(false);
                });
            }
            return;
        }

        MusicFileItem currentItem = pendingDecryptList.remove(0);
        currentDecryptIndex++;

        if (isAdded() && getContext() != null) {
            requireActivity().runOnUiThread(() -> {
                Toast.makeText(requireContext(), String.format("正在解密(%d/%d)：%s", currentDecryptIndex, totalDecryptCount, currentItem.getFileName()), Toast.LENGTH_SHORT).show();
            });
        }

        // 安全强转，避免空指针
        MainActivity activity = getActivity() instanceof MainActivity ? (MainActivity) getActivity() : null;
        if (activity != null) {
            activity.startDecrypt(currentItem.getFilePath(), currentItem.getFileName(), this);
        } else {
            startDecryptQueue();
        }
    }

    @Override
    public void onDecryptSuccess(String fileName, byte[] fileData) {
        if (!isAdded() || getContext() == null) {
            startDecryptQueue();
            return;
        }
    
        try {
            // 读取自定义存储路径
            String saveDirPath = SpUtils.getSavePath(requireContext());
            File saveDir = new File(saveDirPath);
            if (!saveDir.exists()) saveDir.mkdirs();
            File outFile = new File(saveDir, fileName);
            FileOutputStream fos = new FileOutputStream(outFile);
            fos.write(fileData);
            fos.flush();
            fos.close();
            // 刷新媒体库
            requireContext().sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, android.net.Uri.fromFile(outFile)));

            // 提示完整存储路径
            String finalPath = outFile.getAbsolutePath();
            requireActivity().runOnUiThread(() -> {
                Toast.makeText(requireContext(), "解密成功！文件已保存到：\n" + finalPath, Toast.LENGTH_LONG).show();
            });
    
        } catch (Exception e) {
            requireActivity().runOnUiThread(() -> {
                Toast.makeText(requireContext(), "文件保存失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
        }
        startDecryptQueue();
    }


    @Override
    public void onDecryptFailed(String errorMsg) {
        if (isAdded() && getContext() != null) {
            requireActivity().runOnUiThread(() -> {
                Toast.makeText(requireContext(), "解密失败：" + errorMsg, Toast.LENGTH_SHORT).show();
            });
        }
        startDecryptQueue();
    }

    @Override
    public void onDecryptProgress(int current, int total) {
        // 预留进度回调
    }

    @Override
    public void onResume() {
        super.onResume();
        loadMusicFileList();
    }

    // 音乐文件数据类
    public static class MusicFileItem {
        private final String platform;
        private final String fileName;
        private final String filePath;
        private boolean isSelected;

        public MusicFileItem(String platform, String fileName, String filePath) {
            this.platform = platform;
            this.fileName = fileName;
            this.filePath = filePath;
            this.isSelected = false;
        }

        public String getPlatform() { return platform; }
        public String getFileName() { return fileName; }
        public String getFilePath() { return filePath; }
        public boolean isSelected() { return isSelected; }
        public void setSelected(boolean selected) { isSelected = selected; }
    }

    // 列表适配器
    public static class MusicAdapter extends RecyclerView.Adapter<MusicAdapter.VH> {
        private final List<MusicFileItem> itemList;
        private final OnItemActionListener listener;

        public MusicAdapter(List<MusicFileItem> itemList, OnItemActionListener listener) {
            this.itemList = itemList;
            this.listener = listener;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_music_file, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            MusicFileItem item = itemList.get(position);
            holder.tvFileName.setText(item.getFileName());
            holder.cbSelect.setChecked(item.isSelected());

            holder.cbSelect.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (buttonView.isPressed()) {
                    item.setSelected(isChecked);
                    notifySelectedChange();
                }
            });

            holder.btnDecrypt.setOnClickListener(v -> {
                if (listener != null) listener.onDecryptClick(item);
            });
        }

        private void notifySelectedChange() {
            if (listener != null) {
                List<MusicFileItem> selected = new ArrayList<>();
                for (MusicFileItem item : itemList) {
                    if (item.isSelected()) selected.add(item);
                }
                listener.onSelectChange(selected);
            }
        }

        public void setAllSelected(boolean isSelected) {
            for (MusicFileItem item : itemList) {
                item.setSelected(isSelected);
            }
            notifyDataSetChanged();
            notifySelectedChange();
        }

        @Override
        public int getItemCount() { return itemList.size(); }

        static class VH extends RecyclerView.ViewHolder {
            CheckBox cbSelect;
            TextView tvFileName;
            MaterialButton btnDecrypt;

            VH(View itemView) {
                super(itemView);
                cbSelect = itemView.findViewById(R.id.cb_select);
                tvFileName = itemView.findViewById(R.id.tv_file_name);
                btnDecrypt = itemView.findViewById(R.id.btn_decrypt);
            }
        }
    }

    // 事件回调接口
    public interface OnItemActionListener {
        void onDecryptClick(MusicFileItem item);
        void onSelectChange(List<MusicFileItem> selectedList);
    }
}
