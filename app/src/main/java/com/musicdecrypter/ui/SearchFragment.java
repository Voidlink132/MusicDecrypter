package com.musicdecrypter.ui;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.musicdecrypter.R;
import com.musicdecrypter.utils.FileScannerUtils;

import java.util.ArrayList;
import java.util.List;

public class SearchFragment extends Fragment {

    private RecyclerView rvMusicFiles;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // 普通加载布局，不用 DataBinding
        return inflater.inflate(R.layout.fragment_search, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        rvMusicFiles = view.findViewById(R.id.rv_music_files);
        
        checkStoragePermission();
        initRecyclerView();
    }

    // 请求全部文件权限（读取NCM等）
    private void checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(android.net.Uri.parse("package:" + requireContext().getPackageName()));
                startActivity(intent);
            }
        }
    }

    private void initRecyclerView() {
        rvMusicFiles.setLayoutManager(new LinearLayoutManager(requireContext()));

        List<String> allMusic = new ArrayList<>();
        List<FileScannerUtils.MusicDir> dirs = FileScannerUtils.MUSIC_DIR_LIST;

        for (FileScannerUtils.MusicDir d : dirs) {
            List<String> files = FileScannerUtils.scanMusicFiles(d.getPath());
            if (files.isEmpty()) {
                allMusic.add("【" + d.getName() + "】暂无加密文件");
            } else {
                for (String f : files) {
                    allMusic.add("【" + d.getName() + "】" + f);
                }
            }
        }

        MusicAdapter adapter = new MusicAdapter(allMusic);
        rvMusicFiles.setAdapter(adapter);
    }

    static class MusicAdapter extends RecyclerView.Adapter<MusicAdapter.VH> {
        private final List<String> list;

        MusicAdapter(List<String> list) {
            this.list = list;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(android.R.layout.simple_list_item_1, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            holder.text.setText(list.get(position));
            holder.text.setPadding(40,20,40,20);
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            TextView text;
            VH(View itemView) {
                super(itemView);
                text = itemView.findViewById(android.R.id.text1);
            }
        }
    }
}
