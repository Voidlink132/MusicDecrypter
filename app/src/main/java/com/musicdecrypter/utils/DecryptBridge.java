package com.musicdecrypter.utils;

import android.webkit.JavascriptInterface;

import java.io.RandomAccessFile;

public class DecryptBridge {
    public interface DecryptCallback {
        void onDecryptSuccess(String fileName, byte[] fileData);
        void onDecryptFailed(String errorMsg);
        void onDecryptProgress(int current, int total);
    }

    private final DecryptCallback callback;
    private RandomAccessFile currentFile;

    public DecryptBridge(DecryptCallback callback) {
        this.callback = callback;
    }

    @JavascriptInterface
    public long openFile(String filePath) {
        try {
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

    @JavascriptInterface
    public void onDecryptSuccess(String fileName, String base64Data) {
        if (callback != null) {
            byte[] data = android.util.Base64.decode(base64Data, android.util.Base64.NO_WRAP);
            callback.onDecryptSuccess(fileName, data);
        }
    }

    @JavascriptInterface
    public void onDecryptFailed(String errorMsg) {
        if (callback != null) {
            callback.onDecryptFailed(errorMsg);
        }
    }

    @JavascriptInterface
    public void onDecryptProgress(int current, int total) {
        if (callback != null) {
            callback.onDecryptProgress(current, total);
        }
    }
}
