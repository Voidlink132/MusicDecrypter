package com.musicdecrypter.ui;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.musicdecrypter.MainActivity;
import com.musicdecrypter.R;
import com.musicdecrypter.model.MusicFile;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SearchFragment extends Fragment {

    private RecyclerView recyclerView;
    private TextView tvEmpty;
    
    // 进度卡片相关
    private ProgressBar progressBar;
    private TextView tvProgressMsg;
    private TextView tvProgressPercent;
    private TextView tvSavePathSmall;
    private View layoutPathInfo;
    
    private MusicAdapter adapter;
    private List<Object> displayList = new ArrayList<>();
    private SharedPreferences sp;

    private static final String[] SUPPORT_EXTENSIONS = {
            ".ncm", ".mgg", ".mflac", ".kgm", ".kgma", ".qmc0", ".qmcflac", ".qmc3", ".qmcogg", ".tkm", ".kwm",
            ".mp3", ".flac", ".ogg", ".wav", ".m4a", ".aac"
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_search, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        sp = requireContext().getSharedPreferences("config", Context.MODE_PRIVATE);
        
        recyclerView = view.findViewById(R.id.recyclerView);
        tvEmpty = view.findViewById(R.id.tv_empty);
        
        // 初始化卡片控件
        progressBar = view.findViewById(R.id.progressBar);
        tvProgressMsg = view.findViewById(R.id.tv_progress_msg);
        tvProgressPercent = view.findViewById(R.id.tv_progress_percent);
        tvSavePathSmall = view.findViewById(R.id.tv_save_path_small);
        layoutPathInfo = view.findViewById(R.id.layout_path_info);

        String defaultPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).getAbsolutePath() + "/MusicDecrypter";
        tvSavePathSmall.setText(defaultPath);

        layoutPathInfo.setOnClickListener(v -> openMusicDirectory(tvSavePathSmall.getText().toString()));

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new MusicAdapter(displayList, new OnFileClickListener() {
            @Override
            public void onDecryptClick(MusicFile file) {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).startDecryption(new File(file.getFilePath()));
                }
            }

            @Override
            public void onManualUploadClick() {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).triggerManualFilePicker();
                }
            }
        });
        recyclerView.setAdapter(adapter);
    }

    private void openMusicDirectory(String path) {
        File file = new File(path);
        if (!file.exists()) file.mkdirs();
        
        try {
            // 定向唤起系统文件管理器
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri uri = Uri.parse("content://com.android.externalstorage.documents/document/primary%3AMusic%2FMusicDecrypter");
            intent.setDataAndType(uri, "vnd.android.cursor.dir/document");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            String[] fmPackages = {"com.google.android.documentsui", "com.android.documentsui", "com.android.fileexplorer", "com.miui.explorer"};
            boolean launched = false;
            for (String pkg : fmPackages) {
                try {
                    intent.setPackage(pkg);
                    startActivity(intent);
                    launched = true;
                    break;
                } catch (Exception ignored) {}
            }

            if (!launched) {
                intent.setPackage(null);
                startActivity(Intent.createChooser(intent, "查看文件夹内容"));
            }
        } catch (Exception e) {
            Toast.makeText(getContext(), "无法唤起文件管理器", Toast.LENGTH_SHORT).show();
        }
    }

    public void updateProgress(boolean visible, String step, int percent) {
        if (progressBar == null) return;
        
        if (!visible) {
            // 任务结束，恢复默认状态 (等待下载...)
            tvProgressMsg.setText("等待下载...");
            progressBar.setProgress(0);
            tvProgressPercent.setText("0%");
        } else {
            if (step != null) tvProgressMsg.setText(step);
            progressBar.setProgress(percent);
            tvProgressPercent.setText(percent + "%");
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        startScanMusic();
    }

    private void startScanMusic() {
        boolean showOthers = sp.getBoolean("show_others", false);
        
        new Thread(() -> {
            Map<String, List<MusicFile>> groupedFiles = new HashMap<>();
            scan(Environment.getExternalStorageDirectory(), groupedFiles);
            scanAndroidData(groupedFiles);

            List<Object> newList = new ArrayList<>();
            String[] prioritySources = {"网易云音乐", "QQ音乐", "酷狗音乐", "酷我音乐", "浏览器下载", "其他来源"};

            for (String source : prioritySources) {
                if (!showOthers && (source.equals("其他来源") || source.equals("浏览器下载"))) continue;

                List<MusicFile> files = groupedFiles.get(source);
                if (files != null && !files.isEmpty()) {
                    newList.add(source);
                    Collections.sort(files, (f1, f2) -> f1.getFileName().compareToIgnoreCase(f2.getFileName()));
                    newList.addAll(files);
                }
            }
            
            newList.add(new FooterItem());

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    displayList.clear();
                    displayList.addAll(newList);
                    adapter.notifyDataSetChanged();
                    tvEmpty.setVisibility(displayList.isEmpty() ? View.VISIBLE : View.GONE);
                });
            }
        }).start();
    }

    private void scan(File dir, Map<String, List<MusicFile>> groupedFiles) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) {
                String dirName = file.getName();
                if (dirName.startsWith(".") || dirName.equalsIgnoreCase("Android") || 
                    dirName.equalsIgnoreCase("Pictures") || dirName.equalsIgnoreCase("DCIM")) continue;
                scan(file, groupedFiles);
            } else {
                checkAndAddFile(file, groupedFiles);
            }
        }
    }

    private void scanAndroidData(Map<String, List<MusicFile>> groupedFiles) {
        File dataDir = new File(Environment.getExternalStorageDirectory(), "Android/data");
        if (!dataDir.exists() || !dataDir.isDirectory()) return;
        String[] targetPkgs = {"com.netease.cloudmusic", "com.tencent.qqmusic", "com.kugou.android", "cn.kuwo.player"};
        for (String pkg : targetPkgs) {
            File pkgDir = new File(dataDir, pkg);
            if (pkgDir.exists()) scanDeep(pkgDir, groupedFiles);
        }
    }

    private void scanDeep(File dir, Map<String, List<MusicFile>> groupedFiles) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) scanDeep(file, groupedFiles);
            else checkAndAddFile(file, groupedFiles);
        }
    }

    private void checkAndAddFile(File file, Map<String, List<MusicFile>> groupedFiles) {
        String name = file.getName().toLowerCase();
        for (String ext : SUPPORT_EXTENSIONS) {
            if (name.endsWith(ext)) {
                String source = getSource(file.getAbsolutePath());
                if (!groupedFiles.containsKey(source)) groupedFiles.put(source, new ArrayList<>());
                boolean exists = false;
                for (MusicFile mf : groupedFiles.get(source)) {
                    if (mf.getFilePath().equals(file.getAbsolutePath())) { exists = true; break; }
                }
                if (!exists) groupedFiles.get(source).add(new MusicFile(file.getName(), file.getAbsolutePath()));
                break;
            }
        }
    }

    private String getSource(String path) {
        String p = path.toLowerCase();
        if (p.contains("cloudmusic") || p.contains("netease")) return "网易云音乐";
        if (p.contains("qqmusic") || p.contains("tencent") || p.contains("mqms")) return "QQ音乐";
        if (p.contains("kgmusic") || p.contains("kugou")) return "酷狗音乐";
        if (p.contains("kuwo")) return "酷我音乐";
        if (p.contains("download")) return "浏览器下载";
        return "其他来源";
    }

    static class FooterItem {}

    interface OnFileClickListener { 
        void onDecryptClick(MusicFile file); 
        void onManualUploadClick();
    }

    static class MusicAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int TYPE_HEADER = 0;
        private static final int TYPE_ITEM = 1;
        private static final int TYPE_FOOTER = 2;
        private final List<Object> data;
        private final OnFileClickListener listener;

        MusicAdapter(List<Object> data, OnFileClickListener listener) {
            this.data = data;
            this.listener = listener;
        }

        @Override
        public int getItemViewType(int position) { 
            if (data.get(position) instanceof String) return TYPE_HEADER;
            if (data.get(position) instanceof FooterItem) return TYPE_FOOTER;
            return TYPE_ITEM;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == TYPE_HEADER) return new HeaderViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_music_header, parent, false));
            if (viewType == TYPE_FOOTER) return new FooterViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_music_footer, parent, false));
            return new ItemViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_music_file, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (holder instanceof HeaderViewHolder) {
                HeaderViewHolder h = (HeaderViewHolder) holder;
                String source = (String) data.get(position);
                h.tvHeader.setText(source);
                h.tvHeader.setTextColor(getPlatformColor(source));
            } else if (holder instanceof ItemViewHolder) {
                MusicFile file = (MusicFile) data.get(position);
                ItemViewHolder h = (ItemViewHolder) holder;
                h.tvName.setText(file.getFileName());
                h.tvPath.setText(file.getFilePath());
                String source = getSourceFromPath(file.getFilePath());
                int color = getPlatformColor(source);
                String name = file.getFileName().toLowerCase();
                h.btnDecrypt.setText((name.endsWith(".mp3") || name.endsWith(".ogg") || name.endsWith(".flac") || name.endsWith(".wav") || name.endsWith(".m4a")) ? "保存" : "解密");
                h.btnDecrypt.setTextColor(color);
                h.btnDecrypt.setOnClickListener(v -> { if (listener != null) listener.onDecryptClick(file); });
            } else if (holder instanceof FooterViewHolder) {
                holder.itemView.setOnClickListener(v -> { if (listener != null) listener.onManualUploadClick(); });
            }
        }

        private int getPlatformColor(String source) {
            switch (source) {
                case "网易云音乐": return Color.parseColor("#FF4081");
                case "QQ音乐": return Color.parseColor("#4CAF50");
                case "酷狗音乐": return Color.parseColor("#2196F3");
                case "酷我音乐": return Color.parseColor("#FF9800");
                case "浏览器下载": return Color.parseColor("#607D8B");
                default: return Color.BLACK;
            }
        }

        private String getSourceFromPath(String path) {
            String p = path.toLowerCase();
            if (p.contains("cloudmusic") || p.contains("netease")) return "网易云音乐";
            if (p.contains("qqmusic") || p.contains("tencent") || p.contains("mqms")) return "QQ音乐";
            if (p.contains("kgmusic") || p.contains("kugou")) return "酷狗音乐";
            if (p.contains("kuwo")) return "酷我音乐";
            if (p.contains("download")) return "浏览器下载";
            return "其他来源";
        }

        @Override
        public int getItemCount() { return data.size(); }

        static class HeaderViewHolder extends RecyclerView.ViewHolder {
            TextView tvHeader;
            HeaderViewHolder(View v) { super(v); tvHeader = v.findViewById(R.id.tv_header); v.setPadding(v.getPaddingLeft(), 32, v.getPaddingRight(), 8); }
        }

        static class ItemViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvPath;
            Button btnDecrypt;
            ItemViewHolder(View v) { super(v); tvName = v.findViewById(R.id.tv_file_name); tvPath = v.findViewById(R.id.tv_file_path); btnDecrypt = v.findViewById(R.id.btn_decrypt); v.setPadding(v.getPaddingLeft(), 16, v.getPaddingRight(), 16); }
        }

        static class FooterViewHolder extends RecyclerView.ViewHolder {
            FooterViewHolder(View v) { super(v); }
        }
    }
}
