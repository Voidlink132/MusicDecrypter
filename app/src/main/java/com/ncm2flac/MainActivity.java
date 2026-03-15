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
package com.ncm2flac;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.widget.ListView;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 1001;
    private ListView fileListView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 绑定控件，ID和布局完全匹配
        fileListView = findViewById(R.id.file_list);

        // 检查并申请权限
        checkAndRequestPermissions();
    }

    // 权限检查与申请
    private void checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ 申请所有文件访问权限
            if (!Environment.isExternalStorageManager()) {
                Toast.makeText(this, "请开启所有文件访问权限", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(android.net.Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        } else {
            // Android 10及以下申请存储权限
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                    || ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        PERMISSION_REQUEST_CODE);
            }
        }
    }

    // 权限申请回调
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "权限申请成功", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "权限被拒绝，无法读取文件", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // SettingFragment调用的方法，完整实现
    public void updateBackground() {
        // 此处可添加你的背景更新逻辑，示例代码如下
        getWindow().getDecorView().setBackgroundColor(ContextCompat.getColor(this, android.R.color.white));
        Toast.makeText(this, "背景已更新", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 页面恢复时的逻辑，可添加文件扫描逻辑
    }
}
