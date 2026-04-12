package com.musicdecrypter.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.musicdecrypter.R;
import com.musicdecrypter.model.MusicFile;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SearchFragment extends Fragment {

    // 所有变量已提前声明，彻底解决找不到符号报错
    private RecyclerView recyclerView;
    private TextView tvEmpty;
    private MusicAdapter adapter;
    private List<MusicFile> musicList = new ArrayList<>();

    // 你指定的3个固定扫描目录（和三周前完全一致）
    private static final String[] SCAN_DIRECTORIES = {
            "/storage/emulated/0/Download/netease/cloudmusic/Music/",
            "/storage/emulated/0/Music/qqmusic/song/",
            "/storage/emulated/0/Download/kgmusic/download/"
    };

    // 支持的加密音乐格式
    private static final String[] SUPPORT_EXTENSIONS = {
            ".ncm", ".mgg", ".mflac",
            ".kgm", ".kgma",
            ".qmc0", ".qmcflac", ".qmc3"
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // 绑定布局
        View rootView = inflater.inflate(R.layout.fragment_search, container, false);

        // 绑定控件（ID和布局文件完全对应）
        recyclerView = rootView.findViewById(R.id.recyclerV1iew);
        tvEmpty = rootView.findViewById(R.id.tv_empty);

        // 初始化列表
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new MusicAdapter(musicList);
        recyclerView.setAdapter(adapter);

        // 启动扫描（和三周前逻辑完全一致）
        startScanMusic();

        return rootView;
    }

    // 启动扫描（子线程执行，不卡UI）
    private void startScanMusic() {
        // 清空原有数据
        musicList.clear();
        adapter.notifyDataSetChanged();
        tvEmpty.setVisibility(View.GONE);

        // 开启子线程扫描
        new Thread(() -> {
            // 遍历所有指定目录
            for (String dirPath : SCAN_DIRECTORIES) {
                File targetDir = new File(dirPath);
                // 目录存在且是文件夹，才进行扫描
                if (targetDir.exists() && targetDir.isDirectory()) {
                    scanDirectory(targetDir);
                }
            }

            // 扫描完成，切回主线程更新UI
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    adapter.notifyDataSetChanged();
                    if (musicList.isEmpty()) {
                        // 没有找到文件，显示空提示
                        tvEmpty.setVisibility(View.VISIBLE);
                    } else {
                        // 找到文件，隐藏空提示，弹出吐司
                        tvEmpty.setVisibility(View.GONE);
                        Toast.makeText(getContext(), "扫描完成，共找到 " + musicList.size() + " 个加密音乐文件", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }).start();
    }

    // 递归扫描目录（和三周前逻辑完全一致）
    private void scanDirectory(File directory) {
        // 如果是文件，判断是否是加密音乐
        if (directory.isFile()) {
            String fileName = directory.getName().toLowerCase();
            // 遍历所有支持的格式
            for (String ext : SUPPORT_EXTENSIONS) {
                if (fileName.endsWith(ext)) {
                    // 匹配到加密格式，加入列表
                    musicList.add(new MusicFile(directory.getName(), directory.getAbsolutePath()));
                    break;
                }
            }
            return;
        }

        // 如果是文件夹，遍历子文件
        File[] childFiles = directory.listFiles();
        if (childFiles == null) return;
        for (File file : childFiles) {
            scanDirectory(file);
        }
    }

    // 列表适配器（内部类，和三周前完全一致）
    static class MusicAdapter extends RecyclerView.Adapter<MusicAdapter.ViewHolder> {

        private final List<MusicFile> dataList;

        public MusicAdapter(List<MusicFile> dataList) {
            this.dataList = dataList;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View itemView = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_music, parent, false);
            return new ViewHolder(itemView);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            MusicFile musicFile = dataList.get(position);
            holder.tvFileName.setText(musicFile.getFileName());
            holder.tvFilePath.setText(musicFile.getFilePath());
        }

        @Override
        public int getItemCount() {
            return dataList.size();
        }

        // 列表项控件绑定
        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvFileName;
            TextView tvFilePath;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvFileName = itemView.findViewById(R.id.tv_file_name);
                tvFilePath = itemView.findViewById(R.id.tv_file_path);
            }
        }
    }
}
