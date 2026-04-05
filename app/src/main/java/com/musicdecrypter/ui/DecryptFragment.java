package com.musicdecrypter.ui;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.musicdecrypter.MainActivity;
import com.musicdecrypter.R;
import com.musicdecrypter.databinding.FragmentDecryptBinding;
import com.musicdecrypter.utils.DecryptBridge;
import com.musicdecrypter.utils.SpUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class DecryptFragment extends Fragment implements MainActivity.OnEngineStateChangeListener, DecryptBridge.DecryptCallback {

    private FragmentDecryptBinding binding;
    private final List<Uri> pendingFileUris = new ArrayList<>();
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger failedCount = new AtomicInteger(0);
    private int totalFileCount = 0;
    private MainActivity mainActivity;

    private final String[] stepTexts = {
            "就绪",
            "正在读取文件...",
            "正在初始化解密引擎...",
            "正在解密文件...",
            "正在保存文件...",
            "解密完成"
    };

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
        binding.btnDownload.setOnClickListener(v -> openSaveDir());
        binding.btnSelectFile.setOnClickListener(v -> openFileChooser());
        binding.tvStatus.setText("解密引擎初始化中...");
        binding.btnSelectFile.setEnabled(false);
    }

    @Override
    public void onStart() {
        super.onStart();
        // 【关键修复】绑定引擎状态监听，解决卡初始化
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

    // 【关键修复】引擎状态回调，解决卡初始化
    @Override
    public void onEngineStateChange(int state, String message) {
        if (!isAdded() || getContext() == null) return;
        requireActivity().runOnUiThread(() -> {
            switch (state) {
                case MainActivity.ENGINE_STATE_LOADING:
                    binding.tvStatus.setText(message);
                    binding.btnSelectFile.setEnabled(false);
                    break;
                case MainActivity.ENGINE_STATE_READY:
                    binding.tvStatus.setText("解密引擎已就绪，可选择加密音乐文件");
                    binding.btnSelectFile.setEnabled(true);
                    break;
                case MainActivity.ENGINE_STATE_ERROR:
                case MainActivity.ENGINE_STATE_TIMEOUT:
                    binding.tvStatus.setText(message);
                    binding.btnSelectFile.setEnabled(false);
                    Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                    break;
            }
        });
    }

    private void openFileChooser() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        fileChooserLauncher.launch(intent);
    }

    private void startDecryptQueue() {
        if (pendingFileUris.isEmpty()) {
            if (isAdded() && getContext() != null) {
                requireActivity().runOnUiThread(() -> {
                    binding.llProgressArea.setVisibility(View.GONE);
                    binding.tvStatus.setText(String.format("解密完成！成功：%d 个，失败：%d 个", successCount.get(), failedCount.get()));
                    binding.btnDownload.setVisibility(View.VISIBLE);
                    binding.btnSelectFile.setEnabled(true);
                });
            }
            return;
        }

        Uri fileUri = pendingFileUris.remove(0);
        int currentIndex = totalFileCount - pendingFileUris.size();

        try {
            String fileName = getFileNameFromUri(fileUri);
            byte[] fileData = readFileDataFromUri(fileUri);

            if (isAdded() && getContext() != null) {
                requireActivity().runOnUiThread(() -> {
                    binding.btnSelectFile.setEnabled(false);
                    binding.btnDownload.setVisibility(View.GONE);
                    binding.llProgressArea.setVisibility(View.VISIBLE);
                    binding.tvDecryptStep.setText(String.format("正在解密(%d/%d)：%s", currentIndex, totalFileCount, fileName));
                    binding.tvProgressPercent.setText("0%");
                    binding.decryptProgressBar.setProgress(0);
                    binding.tvStatus.setText("解密中...");
                });
            }

            if (mainActivity != null) {
                File tempFile = createTempFile(fileData, fileName);
                mainActivity.startDecrypt(tempFile.getAbsolutePath(), fileName, this);
            } else {
                startDecryptQueue();
            }

        } catch (Exception e) {
            failedCount.incrementAndGet();
            if (isAdded() && getContext() != null) {
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(), "读取文件失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
            startDecryptQueue();
        }
    }

    private byte[] readFileDataFromUri(Uri uri) throws Exception {
        ContentResolver resolver = requireContext().getContentResolver();
        InputStream inputStream = resolver.openInputStream(uri);
        if (inputStream == null) throw new Exception("无法打开文件");

        byte[] buffer = new byte[1024 * 1024];
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            bos.write(buffer, 0, read);
        }
        inputStream.close();
        return bos.toByteArray();
    }

    private File createTempFile(byte[] data, String fileName) throws Exception {
        File tempDir = new File(requireContext().getCacheDir(), "decrypt_temp");
        if (!tempDir.exists()) tempDir.mkdirs();
        File[] oldFiles = tempDir.listFiles();
        if (oldFiles != null) {
            for (File f : oldFiles) f.delete();
        }
        File tempFile = new File(tempDir, fileName);
        FileOutputStream fos = new FileOutputStream(tempFile);
        fos.write(data);
        fos.flush();
        fos.close();
        return tempFile;
    }

    @Override
    public void onDecryptProgress(int current, int total, int step) {
        if (!isAdded() || getContext() == null) return;
        requireActivity().runOnUiThread(() -> {
            binding.decryptProgressBar.setProgress(current);
            binding.tvProgressPercent.setText(current + "%");
            if (step >= 0 && step < stepTexts.length) {
                binding.tvDecryptStep.setText(stepTexts[step]);
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

            successCount.incrementAndGet();
            requireActivity().runOnUiThread(() -> {
                Toast.makeText(requireContext(), "解密成功！文件已保存到：\n" + outFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
            });

        } catch (Exception e) {
            failedCount.incrementAndGet();
            requireActivity().runOnUiThread(() -> {
                Toast.makeText(requireContext(), "保存失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
        }
        startDecryptQueue();
    }

    @Override
    public void onDecryptFailed(String errorMsg) {
        failedCount.incrementAndGet();
        if (isAdded() && getContext() != null) {
            requireActivity().runOnUiThread(() -> {
                binding.llProgressArea.setVisibility(View.GONE);
                Toast.makeText(requireContext(), "解密失败：" + errorMsg, Toast.LENGTH_SHORT).show();
            });
        }
        startDecryptQueue();
    }

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

    private void openSaveDir() {
        if (!isAdded() || getContext() == null) return;
        String dirPath = SpUtils.getSavePath(requireContext());
        File dir = new File(dirPath);
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
        File tempDir = new File(requireContext().getCacheDir(), "decrypt_temp");
        if (tempDir.exists()) {
            File[] files = tempDir.listFiles();
            if (files != null) {
                for (File f : files) f.delete();
            }
        }
    }
}
