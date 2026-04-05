package com.musicdecrypter.utils;

import android.util.Base64;
import android.util.Log;
import android.webkit.JavascriptInterface;

import java.io.File;
import java.io.FileInputStream;

public class DecryptBridge {
    private static final String TAG = "DecryptBridge";
    public static final int STEP_INIT_ENGINE = 2;
    public static final int STEP_READ_FILE = 1;
    public static final int STEP_DECRYPT = 3;
    public static final int STEP_SAVE_FILE = 4;

    private DecryptCallback callback;
    private FileInputStream currentFileStream;

    public interface DecryptCallback {
        void onDecryptProgress(int current, int total, int step);
        void onDecryptSuccess(String fileName, byte[] fileData);
        void onDecryptFailed(String errorMsg);
    }

    public DecryptBridge(DecryptCallback callback) {
        this.callback = callback;
    }

    // 【新增】更新回调，不重复注入桥接对象
    public void updateCallback(DecryptCallback callback) {
        this.callback = callback;
        Log.d(TAG, "桥接对象回调已更新");
    }

    @JavascriptInterface
    public int openFile(String filePath) {
        try {
            Log.d(TAG, "打开文件：" + filePath);
            File file = new File(filePath);
            if (!file.exists() || !file.canRead()) {
                Log.e(TAG, "文件不存在或无法读取");
                return -1;
            }
            currentFileStream = new FileInputStream(file);
            return (int) file.length();
        } catch (Exception e) {
            Log.e(TAG, "打开文件失败：" + e.getMessage());
            return -1;
        }
    }

    @JavascriptInterface
    public String readBlock(int blockSize) {
        try {
            if (currentFileStream == null) return null;
            byte[] buffer = new byte[blockSize];
            int readLen = currentFileStream.read(buffer);
            if (readLen <= 0) return null;
            if (readLen < blockSize) {
                byte[] realBuffer = new byte[readLen];
                System.arraycopy(buffer, 0, realBuffer, 0, readLen);
                return Base64.encodeToString(realBuffer, Base64.NO_WRAP);
            }
            return Base64.encodeToString(buffer, Base64.NO_WRAP);
        } catch (Exception e) {
            Log.e(TAG, "读取文件块失败：" + e.getMessage());
            return null;
        }
    }

    @JavascriptInterface
    public void closeFile() {
        try {
            if (currentFileStream != null) {
                currentFileStream.close();
                currentFileStream = null;
                Log.d(TAG, "文件已关闭");
            }
        } catch (Exception e) {
            Log.e(TAG, "关闭文件失败：" + e.getMessage());
        }
    }

    @JavascriptInterface
    public void onDecryptProgressUpdate(int progress) {
        if (callback != null) {
            callback.onDecryptProgress(progress, 100, STEP_DECRYPT);
        }
    }

    @JavascriptInterface
    public void onDecryptSuccess(String fileName, String base64Data) {
        Log.d(TAG, "解密成功，文件名：" + fileName);
        if (callback != null) {
            try {
                byte[] fileData = Base64.decode(base64Data, Base64.NO_WRAP);
                callback.onDecryptSuccess(fileName, fileData);
            } catch (Exception e) {
                callback.onDecryptFailed("解析解密文件失败：" + e.getMessage());
            }
        }
        closeFile();
    }

    @JavascriptInterface
    public void onDecryptFailed(String errorMsg) {
        Log.e(TAG, "解密失败：" + errorMsg);
        if (callback != null) {
            callback.onDecryptFailed(errorMsg);
        }
        closeFile();
    }
}
