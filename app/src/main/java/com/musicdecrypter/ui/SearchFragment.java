package com.musicdecrypter.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.musicdecrypter.R;
import com.musicdecrypter.databinding.FragmentSearchBinding;
import com.musicdecrypter.utils.FileScannerUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SearchFragment extends Fragment {

    private FragmentSearchBinding binding;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // 适配Android 11+的公共目录路径
    private static final List<FileScannerUtils.MusicDir> MUSIC_DIRS = List.of(
            new FileScannerUtils.MusicDir("网易云音乐",
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/netease/cloudmusic/Music/"),
            new FileScannerUtils.MusicDir("QQ音乐",
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC) + "/qqmusic/song/"),
            new FileScannerUtils.MusicDir("酷狗音乐",
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/kgmusic/download/")
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
        checkStoragePermission();
    }

    // 检查并申请存储权限（Android 11+需要所有文件访问权限）
    private void checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + requireContext().getPackageName()));
                startActivityForResult(intent, 1001);
            } else {
                loadMusicFiles();
            }
        } else {
            if (requireContext().checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1002);
            } else {
                loadMusicFiles();
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1001) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    Toast.makeText(requireContext(), "权限已授予", Toast.LENGTH_SHORT).show();
                    loadMusicFiles();
                } else {
                    Toast.makeText(requireContext(), "未获取到文件访问权限，无法扫描文件", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1002) {
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                loadMusicFiles();
            } else {
                Toast.makeText(requireContext(), "未获取到存储权限，无法扫描文件", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // 加载音乐文件到列表
    private void loadMusicFiles() {
        executor.execute(() -> {
            List<String> allMusicFiles = new ArrayList<>();
            for (FileScannerUtils.MusicDir dir : MUSIC_DIRS) {
                List<String> files = FileScannerUtils.scanMusicFiles(dir.getPath());
                for (String file : files) {
                    allMusicFiles.add("[" + dir.getSourceName() + "] " + file);
                }
            }
            requireActivity().runOnUiThread(() -> setupRecyclerView(allMusicFiles));
        });
    }

    // 设置RecyclerView列表
    private void setupRecyclerView(List<String> musicFiles) {
        RecyclerView recyclerView = binding.rvMusicFiles;
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        MusicFileAdapter adapter = new MusicFileAdapter(musicFiles);
        recyclerView.setAdapter(adapter);

        if (musicFiles.isEmpty()) {
            Toast.makeText(requireContext(), "未扫描到任何音乐文件", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        executor.shutdown();
    }
}
