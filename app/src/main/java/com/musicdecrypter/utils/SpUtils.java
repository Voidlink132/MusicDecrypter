package com.musicdecrypter.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;

import java.io.File;

public class SpUtils {
    private static final String SP_NAME = "music_decrypter_config";
    private static final String KEY_SAVE_PATH = "key_save_path";
    // 默认存储路径：下载/MusicDecrypter
    private static final String DEFAULT_PATH = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/MusicDecrypter/";

    private static SharedPreferences getSp(Context context) {
        return context.getApplicationContext().getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
    }

    // 获取当前存储路径（自动创建目录）
    public static String getSavePath(Context context) {
        String path = getSp(context).getString(KEY_SAVE_PATH, DEFAULT_PATH);
        if (!path.endsWith(File.separator)) path += File.separator;
        File dir = new File(path);
        if (!dir.exists()) dir.mkdirs();
        return path;
    }

    // 修改存储路径
    public static void setSavePath(Context context, String path) {
        getSp(context).edit().putString(KEY_SAVE_PATH, path).apply();
    }

    // 获取默认路径
    public static String getDefaultPath() {
        return DEFAULT_PATH;
    }
}
