package com.musicdecrypter.ui;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.musicdecrypter.R;
import com.musicdecrypter.databinding.FragmentDecryptBinding;
import com.musicdecrypter.utils.DecryptBridge;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class DecryptFragment extends Fragment implements DecryptBridge.DecryptCallback {

    private FragmentDecryptBinding binding;
    private final List<Uri> pendingFileUris = new ArrayList<>();
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger failedCount = new AtomicInteger(0);
    private int totalFileCount = 0;

    // 文件选择器
    private final ActivityResultLauncher<Intent> fileChooserLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) return;

                pendingFileUris.clear();
                successCount.set(0);
                failedCount.set(0);

                if (result.getData().getClipData() != null) {
                    int count = result.getData().getClipData().getItemCount();
                    for (int i = 0; i < count; i++) {
                        pendingFileUris.add(result.getData().getClipData().getItemAt(i).getUri());
                    }
                } else if (result.getData().getData() != null) {
                    pendingFileUris.add(result.getData().getData());
                }

                totalFileCount = pendingFileUris.size();
                if (totalFileCount == 0) {
                    Toast.makeText(requireContext(), "未选择任何文件", Toast.LENGTH_SHORT).show();
                    return;
                }
                startDecryptQueue();
            }
    );

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentDecryptBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 打开解密目录按钮
        binding.btnDownload.setOnClickListener(v -> openSaveDir());

        // 选择文件按钮
        binding.btnSelectFile.setOnClickListener(v -> openFileChooser());

        // 初始化状态
        binding.tvStatus.setText("解密引擎已就绪，可选择加密音乐文件");
    }

    // 打开文件选择器
    private void openFileChooser() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        fileChooserLauncher.launch(intent);
    }

    // 解密队列
    private void startDecryptQueue() {
        if (pendingFileUris.isEmpty()) {
            requireActivity().runOnUiThread(() -> {
                binding.progressBar.setVisibility(View.GONE);
                binding.tvStatus.setText(String.format("解密完成！成功：%d 个，失败：%d 个", successCount.get(), failedCount.get()));
                binding.btnDownload.setVisibility(View.VISIBLE);
            });
            return;
        }

        Uri fileUri = pendingFileUris.remove(0);
        int currentIndex = totalFileCount - pendingFileUris.size();

        try {
            String fileName = getFileNameFromUri(fileUri);
            String filePath = getFilePathFromUri(fileUri);

            requireActivity().runOnUiThread(() -> {
                binding.progressBar.setVisibility(View.VISIBLE);
                binding.tvStatus.setText(String.format("正在解密(%d/%d)：%s", currentIndex, totalFileCount, fileName));
            });

            // 调用全局解密方法
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).startDecrypt(filePath, fileName, this);
            }

        } catch (Exception e) {
            failedCount.incrementAndGet();
            requireActivity().runOnUiThread(() -> {
                Toast.makeText(requireContext(), "读取文件失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
            startDecryptQueue();
        }
    }

    // 解密成功回调
    @Override
    public void onDecryptSuccess(String fileName, byte[] fileData) {
        try {
            saveToDownloadDir(fileName, fileData);
            successCount.incrementAndGet();
        } catch (Exception e) {
            failedCount.incrementAndGet();
            requireActivity().runOnUiThread(() -> {
                Toast.makeText(requireContext(), "保存失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
        }
        startDecryptQueue();
    }

    // 解密失败回调
    @Override
    public void onDecryptFailed(String errorMsg) {
        failedCount.incrementAndGet();
        requireActivity().runOnUiThread(() -> {
            Toast.makeText(requireContext(), "解密失败：" + errorMsg, Toast.LENGTH_SHORT).show();
        });
        startDecryptQueue();
    }

    @Override
    public void onDecryptProgress(int current, int total) {
        // 可选：更新进度条
    }

    // 工具方法：获取文件名
    private String getFileNameFromUri(Uri uri) {
        String name = "unknown_audio";
        if ("content".equals(uri.getScheme())) {
            try (Cursor c = requireContext().getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (c != null && c.moveToFirst()) {
                    int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (idx > -1) name = c.getString(idx);
                }
            }
        } else if (uri.getPath() != null) {
            name = new File(uri.getPath()).getName();
        }
        return name;
    }

    // 工具方法：获取文件绝对路径
    private String getFilePathFromUri(Uri uri) {
        String path = "";
        if ("content".equals(uri.getScheme())) {
            try (Cursor c = requireContext().getContentResolver().query(uri, new String[]{"_data"}, null, null, null)) {
                if (c != null && c.moveToFirst()) {
                    int idx = c.getColumnIndex("_data");
                    if (idx > -1) path = c.getString(idx);
                }
            }
        } else if (uri.getPath() != null) {
            path = uri.getPath();
        }
        return path;
    }

    // 工具方法：保存文件到下载目录
    private void saveToDownloadDir(String fileName, byte[] data) throws Exception {
        File root = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "MusicDecrypter");
        if (!root.exists()) root.mkdirs();
        File outFile = new File(root, fileName);
        FileOutputStream fos = new FileOutputStream(outFile);
        fos.write(data);
        fos.flush();
        fos.close();
        requireContext().sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, android.net.Uri.fromFile(outFile)));
    }

    // 工具方法：打开保存目录
    private void openSaveDir() {
        File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "MusicDecrypter");
        if (!dir.exists()) dir.mkdirs();
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(android.net.Uri.parse(dir.getAbsolutePath()), "*/*");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(Intent.createChooser(intent, "打开解密文件夹"));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
