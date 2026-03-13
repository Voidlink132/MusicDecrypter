package com.ncm2flac;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.ncm2flac.core.AudioPackager;
import com.ncm2flac.core.MetadataHandler;
import com.ncm2flac.core.NcmDecryptor;
import com.ncm2flac.utils.FileUtils;
import java.io.File;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final int FILE_SELECT_CODE = 1002;

    private TextView tvStatus;
    private Button btnSelectFile;
    private Uri selectedFileUri;
    private String selectedFileName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tv_status);
        btnSelectFile = findViewById(R.id.btn_select_file);

        // 检查权限
        checkPermission();

        // 选择文件按钮
        btnSelectFile.setOnClickListener(v -> {
            if (!checkPermission()) {
                Toast.makeText(this, "请先授予存储权限", Toast.LENGTH_SHORT).show();
                return;
            }
            openFileSelector();
        });

        // 处理外部打开的ncm文件
        handleIntent(getIntent());
    }

    // 检查权限
    private boolean checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            int readAudioPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO);
            int notificationPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS);
            if (readAudioPermission != PackageManager.PERMISSION_GRANTED || notificationPermission != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{
                        Manifest.permission.READ_MEDIA_AUDIO,
                        Manifest.permission.POST_NOTIFICATIONS
                }, PERMISSION_REQUEST_CODE);
                return false;
            }
        } else {
            int readPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE);
            int writePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
            if (readPermission != PackageManager.PERMISSION_GRANTED || writePermission != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                }, PERMISSION_REQUEST_CODE);
                return false;
            }
        }
        return true;
    }

    // 打开文件选择器
    private void openFileSelector() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"*/*"});
        startActivityForResult(Intent.createChooser(intent, "选择NCM文件"), FILE_SELECT_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_SELECT_CODE && resultCode == RESULT_OK && data != null) {
            selectedFileUri = data.getData();
            if (selectedFileUri != null) {
                selectedFileName = FileUtils.getFileExtension(selectedFileUri.getPath());
                tvStatus.setText("已选择文件：" + selectedFileUri.getLastPathSegment());
                startDecrypt();
            }
        }
    }

    // 开始解密转换
    private void startDecrypt() {
    tvStatus.setText("正在验证NCM文件...");
    new Thread(() -> {
        try {
            byte[] ncmData = FileUtils.readUriToBytes(this, selectedFileUri);
            NcmDecryptor decryptor = new NcmDecryptor(ncmData);
            
            // 增加解密结果判断，友好提示
            if (!decryptor.decrypt()) {
                runOnUiThread(() -> {
                    tvStatus.setText("解析失败：不是有效的NCM文件");
                    Toast.makeText(this, "文件无效，请选择标准NCM文件", Toast.LENGTH_LONG).show();
                });
                return;
            }

            // 后续的文件写入逻辑（不变）
            String fileName = FileUtils.getFileNameFromUri(this, selectedFileUri);
            String outputFileName = FileUtils.replaceFileExtension(fileName, decryptor.getAudioFormat());
            File outputDir = new File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "Ncm2Flac");
            if (!outputDir.exists()) outputDir.mkdirs();
            File outputFile = new File(outputDir, outputFileName);

            AudioPackager.writeAudioFile(decryptor.getAudioRawData(), decryptor.getAudioFormat(), outputFile);
            MetadataHandler.writeMetadata(outputFile, decryptor.getMetadata(), decryptor.getCoverImage());

            runOnUiThread(() -> {
                tvStatus.setText("转换完成！\n保存路径：" + outputFile.getAbsolutePath());
                Toast.makeText(this, "转换成功", Toast.LENGTH_LONG).show();
            });

        } catch (Exception e) {
            runOnUiThread(() -> {
                tvStatus.setText("转换失败：" + e.getMessage());
                Toast.makeText(this, "失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
            });
            e.printStackTrace();
        }
    }).start();
}
    // 处理外部打开的文件
    private void handleIntent(Intent intent) {
        if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction())) {
            selectedFileUri = intent.getData();
            if (selectedFileUri != null) {
                tvStatus.setText("已选择文件：" + selectedFileUri.getLastPathSegment());
                startDecrypt();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (!allGranted) {
                Toast.makeText(this, "权限被拒绝，无法使用转换功能", Toast.LENGTH_LONG).show();
            }
        }
    }
}
