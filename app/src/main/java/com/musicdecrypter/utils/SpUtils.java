package com.musicdecrypter.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;

import java.io.File;

public class SpUtils {
    private static final String SP_NAME = "MusicDecrypterConfig";
    private static final String KEY_SAVE_PATH = "save_path";

    /**
     * 获取解密文件保存路径（默认：SD卡/MusicDecrypter）
     */
    public static String getSavePath(Context context) {
        SharedPreferences sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        String savePath = sp.getString(KEY_SAVE_PATH, "");
        
        // 若未配置，使用默认路径
        if (savePath.isEmpty() || !new File(savePath).canWrite()) {
            savePath = getDefaultSavePath();
            // 保存默认路径到SP
            sp.edit().putString(KEY_SAVE_PATH, savePath).apply();
        }
        return savePath;
    }

    /**
     * 设置自定义保存路径
     */
    public static void setSavePath(Context context, String path) {
        if (new File(path).canWrite()) {
            SharedPreferences sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
            sp.edit().putString(KEY_SAVE_PATH, path).apply();
        }
    }

    /**
     * 默认保存路径：SD卡/MusicDecrypter
     */
    private static String getDefaultSavePath() {
        File externalDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
        File saveDir = new File(externalDir, "MusicDecrypter");
        if (!saveDir.exists()) {
            saveDir.mkdirs();
        }
        return saveDir.getAbsolutePath();
    }
}
