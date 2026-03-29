package com.musicdecrypter.ui;

import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.musicdecrypter.databinding.FragmentSearchBinding;
import com.musicdecrypter.utils.FileScannerUtils;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SearchFragment extends Fragment {

    private FragmentSearchBinding binding;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private static final List<FileScannerUtils.MusicDir> MUSIC_DIRS = List.of(
            new FileScannerUtils.MusicDir("netease", "/storage/emulated/0/Download/netease/cloudmusic/Music/", R.id.table_netease),
            new FileScannerUtils.MusicDir("qq", "/storage/emulated/0/Music/qqmusic/song/", R.id.table_qq),
            new FileScannerUtils.MusicDir("kugou", "/storage/emulated/0/Download/kgmusic/download/", R.id.table_kugou)
    );

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSearchBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        loadMusicFiles();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadMusicFiles();
    }

    private void loadMusicFiles() {
        executor.execute(() -> {
            for (FileScannerUtils.MusicDir dir : MUSIC_DIRS) {
                List<String> fileList = FileScannerUtils.scanMusicFiles(dir.getPath());
                requireActivity().runOnUiThread(() -> {
                    renderFileTable(dir.getTableId(), fileList);
                });
            }
        });
    }

    private void renderFileTable(int tableId, List<String> fileList) {
        var table = binding.getRoot().findViewById<android.widget.TableLayout>(tableId);
        table.removeAllViews();

        if (fileList.isEmpty()) {
            TableRow row = new TableRow(requireContext());
            TextView emptyText = new TextView(requireContext());
            emptyText.setText(R.string.file_empty);
            emptyText.setPadding(8, 8, 8, 8);
            emptyText.setGravity(Gravity.CENTER);
            row.addView(emptyText);
            table.addView(row);
            return;
        }

        for (String fileName : fileList) {
            TableRow row = new TableRow(requireContext());
            TextView nameText = new TextView(requireContext());
            nameText.setText(fileName);
            nameText.setPadding(8, 8, 8, 8);
            nameText.setMaxLines(1);
            nameText.setSingleLine(true);
            nameText.setEllipsize(android.text.TextUtils.TruncateAt.END);
            row.addView(nameText);
            table.addView(row);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        executor.shutdown();
    }
}
