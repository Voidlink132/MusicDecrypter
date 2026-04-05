package com.musicdecrypter.utils;

import android.webkit.JavascriptInterface;

import java.io.RandomAccessFile;

public class DecryptBridge {
    // 解密步骤常量（用于UI进度显示）
    public static final int STEP_IDLE = 0;
    public static final int STEP_READ_FILE = 1;
    public static final int STEP_INIT_ENGINE = 2;
    public static final int STEP_DECRYPTING = 3;
    public static final int STEP_SAVE_FILE = 4;
    public static final int STEP_FINISH = 5;

    public interface DecryptCallback {
        void onDecryptSuccess(String fileName, byte[] fileData);
        void onDecryptFailed(String errorMsg);
        // 新增：步骤+进度回调（current:当前进度, total:总进度, step:当前步骤）
        void onDecryptProgress(int current, int total, int step);
    }

    private final DecryptCallback callback;
    private RandomAccessFile currentFile;

    public DecryptBridge(DecryptCallback callback) {
        this.callback = callback;
    }

    @JavascriptInterface
    public long openFile(String filePath) {
        try {
            if (callback != null) {
                callback.onDecryptProgress(0, 100, STEP_READ_FILE);
            }
            currentFile = new RandomAccessFile(filePath, "r");
            return currentFile.length();
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }

    @JavascriptInterface
    public String readBlock(int blockSize) {
        try {
            byte[] buffer = new byte[blockSize];
            int read = currentFile.read(buffer);
            if (read <= 0) return null;

            byte[] actualData = read == blockSize ? buffer : java.util.Arrays.copyOf(buffer, read);
            return android.util.Base64.encodeToString(actualData, android.util.Base64.NO_WRAP);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @JavascriptInterface
    public void closeFile() {
        try {
            if (currentFile != null) {
                currentFile.close();
                currentFile = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 新增：JS回调解密进度
    @JavascriptInterface
    public void onDecryptProgressUpdate(int progress) {
        if (callback != null) {
            callback.onDecryptProgress(progress, 100, STEP_DECRYPTING);
        }
    }

    @JavascriptInterface
    public void onDecryptSuccess(String fileName, String base64Data) {
        if (callback != null) {
            callback.onDecryptProgress(95, 100, STEP_SAVE_FILE);
            byte[] data = android.util.Base64.decode(base64Data, android.util.Base64.NO_WRAP);
            callback.onDecryptSuccess(fileName, data);
            callback.onDecryptProgress(100, 100, STEP_FINISH);
        }
    }

    @JavascriptInterface
    public void onDecryptFailed(String errorMsg) {
        if (callback != null) {
            callback.onDecryptFailed(errorMsg);
        }
    }
}
