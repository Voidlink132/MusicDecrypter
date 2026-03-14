package com.ncm2flac.core;

import com.ncm2flac.OnConvertListener;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class NcmDecryptCore {

    private final File inputFile;
    private final File outputFile;
    private static final int BUFFER_SIZE = 1024 * 1024; // 1MB缓冲，避免大文件OOM

    public NcmDecryptCore(File inputFile, String savePath) throws IOException {
        this.inputFile = inputFile;
        // 确保输出目录存在
        File saveDir = new File(savePath);
        if (!saveDir.exists()) {
            saveDir.mkdirs();
        }
        // 生成输出文件名，替换后缀为.flac
        String outputFileName = inputFile.getName().replace(".ncm", ".flac");
        this.outputFile = new File(saveDir, outputFileName);
    }

    public void startDecrypt(OnConvertListener listener) {
        if (listener == null) return;

        listener.onStart();
        FileInputStream fis = null;
        FileOutputStream fos = null;

        try {
            fis = new FileInputStream(inputFile);
            fos = new FileOutputStream(outputFile);

            // 获取文件总大小，计算进度
            long totalSize = inputFile.length();
            long readSize = 0;
            byte[] buffer = new byte[BUFFER_SIZE];
            int len;

            // --------------------------
            // 这里替换为你的真实NCM解密逻辑
            // 示例：循环读取文件，模拟解密进度
            // --------------------------
            while ((len = fis.read(buffer)) != -1) {
                // 这里执行你的解密操作：buffer = decrypt(buffer, len);
                fos.write(buffer, 0, len);
                readSize += len;

                // 计算进度，0-100
                int progress = (int) ((readSize * 100) / totalSize);
                listener.onProgress(progress);
            }

            // 刷新流，确保数据全部写入
            fos.flush();
            listener.onSuccess(outputFile);

        } catch (Exception e) {
            // 异常捕获，回调失败信息
            listener.onFail(e.getMessage());
            // 转换失败时删除不完整的输出文件
            if (outputFile.exists()) {
                outputFile.delete();
            }
        } finally {
            // 关闭流，避免内存泄漏
            try {
                if (fis != null) fis.close();
                if (fos != null) fos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
