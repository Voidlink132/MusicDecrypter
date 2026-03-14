package com.ncm2flac;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;

public class MainActivity extends Activity {

    // 【原有变量名完全保留，未做任何修改】
    private static final int REQUEST_PERMISSION = 1001;
    private static final int REQUEST_FILE_SELECT = 1002;
    private String scanPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/Download/netease/cloudmusic/Music/";
    private String outputDir = Environment.getExternalStorageDirectory().getAbsolutePath() + "/NCM2FLAC/";
    private ListView fileListView;
    private ArrayList<String> ncmFileList = new ArrayList<>();
    private ArrayAdapter<String> listAdapter;
    private File selectedManualFile;

    // 新增ncmc二进制相关变量，不影响原有逻辑
    private String ncmcExecPath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 【原有UI初始化逻辑完全保留】
        fileListView = findViewById(R.id.file_list);
        listAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, ncmFileList);
        fileListView.setAdapter(listAdapter);

        // 初始化ncmc二进制，不影响原有流程
        initNcmcBinary();

        // 【原有权限检查逻辑完全保留】
        checkPermission();

        // 【原有自动扫描点击逻辑完全保留】
        fileListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String filePath = scanPath + ncmFileList.get(position);
                startDecrypt(filePath, outputDir + ncmFileList.get(position).replace(".ncm", ".flac"));
            }
        });

        // 【原有手动选择文件逻辑完全保留】
        Button selectFileBtn = findViewById(R.id.btn_select_file);
        selectFileBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                startActivityForResult(intent, REQUEST_FILE_SELECT);
            }
        });

        // 【原有手动转换逻辑完全保留】
        Button convertBtn = findViewById(R.id.btn_convert);
        convertBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (selectedManualFile == null) {
                    Toast.makeText(MainActivity.this, "请先选择NCM文件", Toast.LENGTH_SHORT).show();
                    return;
                }
                String outputPath = outputDir + selectedManualFile.getName().replace(".ncm", ".flac");
                startDecrypt(selectedManualFile.getAbsolutePath(), outputPath);
            }
        });
    }

    // 初始化ncmc二进制：根据CPU架构复制对应文件到私有目录，设置执行权限
    private void initNcmcBinary() {
        try {
            String abi = Build.CPU_ABI;
            String assetFileName;
            if (abi.contains("arm64")) {
                assetFileName = "ncmc-arm64";
            } else if (abi.contains("armeabi") || abi.contains("armv7")) {
                assetFileName = "ncmc-armv7";
            } else {
                Toast.makeText(this, "不支持的设备架构", Toast.LENGTH_LONG).show();
                return;
            }

            File execFile = new File(getFilesDir(), assetFileName);
            ncmcExecPath = execFile.getAbsolutePath();

            // 仅首次运行复制文件
            if (!execFile.exists()) {
                InputStream is = getAssets().open(assetFileName);
                FileOutputStream fos = new FileOutputStream(execFile);
                byte[] buffer = new byte[1024];
                int len;
                while ((len = is.read(buffer)) > 0) {
                    fos.write(buffer, 0, len);
                }
                is.close();
                fos.close();
                // 设置执行权限
                execFile.setExecutable(true);
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "初始化解密组件失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // 【原有函数名完全保留，仅替换内部解密逻辑】
    private void startDecrypt(String inputPath, String outputPath) {
        // 检查输出目录
        File outputFolder = new File(outputDir);
        if (!outputFolder.exists()) {
            outputFolder.mkdirs();
        }

        // 核心解密逻辑替换为ncmc调用，彻底解决0b文件问题
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // 调用ncmc命令行解密，和网页版逻辑完全一致
                    Process process = Runtime.getRuntime().exec(
                        new String[]{ncmcExecPath, inputPath, "-o", outputPath}
                    );
                    // 等待解密完成
                    int exitCode = process.waitFor();

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (exitCode == 0) {
                                File outputFile = new File(outputPath);
                                if (outputFile.exists() && outputFile.length() > 0) {
                                    Toast.makeText(MainActivity.this, "转换成功！文件已保存到" + outputDir, Toast.LENGTH_LONG).show();
                                } else {
                                    Toast.makeText(MainActivity.this, "转换失败：输出文件为空", Toast.LENGTH_LONG).show();
                                }
                            } else {
                                Toast.makeText(MainActivity.this, "转换失败：解密组件执行错误", Toast.LENGTH_LONG).show();
                            }
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "转换异常：" + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        }).start();
    }

    // 【原有权限检查逻辑完全保留，未做任何修改】
    private void checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                new AlertDialog.Builder(this)
                    .setTitle("权限申请")
                    .setMessage("本应用需要所有文件访问权限，才能扫描和转换NCM文件")
                    .setPositiveButton("去设置", (dialog, which) -> {
                        Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                        intent.setData(Uri.parse("package:" + getPackageName()));
                        startActivityForResult(intent, REQUEST_PERMISSION);
                    })
                    .setCancelable(false)
                    .show();
            } else {
                scanNcmFiles();
            }
        } else {
            if (checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{
                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                }, REQUEST_PERMISSION);
            } else {
                scanNcmFiles();
            }
        }
    }

    // 【原有NCM文件扫描逻辑完全保留，未做任何修改】
    private void scanNcmFiles() {
        ncmFileList.clear();
        File scanDir = new File(scanPath);
        if (scanDir.exists() && scanDir.isDirectory()) {
            File[] files = scanDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.getName().endsWith(".ncm")) {
                        ncmFileList.add(file.getName());
                    }
                }
            }
        }
        listAdapter.notifyDataSetChanged();
        if (ncmFileList.isEmpty()) {
            Toast.makeText(this, "未扫描到NCM文件，请检查目录或手动选择文件", Toast.LENGTH_SHORT).show();
        }
    }

    // 【原有权限回调逻辑完全保留，未做任何修改】
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PERMISSION) {
            checkPermission();
        } else if (requestCode == REQUEST_FILE_SELECT && resultCode == RESULT_OK) {
            if (data != null) {
                Uri uri = data.getData();
                String path = uri.getPath();
                selectedManualFile = new File(path);
                TextView fileNameTv = findViewById(R.id.tv_file_name);
                fileNameTv.setText("已选择：" + selectedManualFile.getName());
            }
        }
    }

    // 【原有权限请求回调逻辑完全保留，未做任何修改】
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                scanNcmFiles();
            } else {
                Toast.makeText(this, "权限被拒绝，无法使用核心功能", Toast.LENGTH_LONG).show();
            }
        }
    }
}
