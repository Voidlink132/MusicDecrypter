package com.musicdecrypter.utils;

import android.os.Environment;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class FileScannerUtils {

    public static class MusicDir {
        private final String name;
        private final String path;

        public MusicDir(String name, String path) {
            this.name = name;
            this.path = path;
        }

        public String getName() { return name; }
        public String getPath() { return path; }
    }

    // 严格按照你指定的目录配置，优先扫描核心路径
    public static final List<MusicDir> MUSIC_DIR_LIST = new ArrayList<>();
    static {
        // 1. 网易云音乐 精确目录
        MUSIC_DIR_LIST.add(new MusicDir("网易云音乐",
                Environment.getExternalStorageDirectory() + "/Download/netease/cloudmusic/Music/"));
        // 2. QQ音乐 精确目录
        MUSIC_DIR_LIST.add(new MusicDir("QQ音乐",
                Environment.getExternalStorageDirectory() + "/Music/qqmusic/song/"));
        // 3. 酷狗音乐 精确目录
        MUSIC_DIR_LIST.add(new MusicDir("酷狗音乐",
                Environment.getExternalStorageDirectory() + "/Download/kgmusic/download/"));

        // 额外补充通用兜底目录，避免漏扫
        MUSIC_DIR_LIST.add(new MusicDir("下载目录",
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/"));
        MUSIC_DIR_LIST.add(new MusicDir("音乐目录",
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC) + "/"));
    }

    // 支持的全量加密格式后缀
    private static final String[] SUPPORT_EXTS = {
            ".ncm", ".mflac", ".mgg", ".kgm", ".kgma",
            ".qmc0", ".qmc1", ".qmc2", ".qmc3", ".qmcflac", ".qmcogg",
            ".tm0", ".tm2", ".tm3", ".tm6", ".bkcmp3", ".bkcm4a"
    };

    // 递归扫描（子目录内的加密文件也能正常识别）
    public static List<String> scanMusicFiles(String dirPath) {
        List<String> res = new ArrayList<>();
        File dir = new File(dirPath);
        if (!dir.exists() || !dir.isDirectory() || !dir.canRead()) return res;

        File[] files = dir.listFiles();
        if (files == null) return res;

        for (File f : files) {
            if (f.isFile()) {
                String name = f.getName().toLowerCase();
                for (String ext : SUPPORT_EXTS) {
                    if (name.endsWith(ext)) {
                        res.add(f.getName());
                        break;
                    }
                }
            } else if (f.isDirectory()) {
                // 递归扫描子目录
                List<String> subFiles = scanMusicFiles(f.getAbsolutePath());
                res.addAll(subFiles);
            }
        }
        return res;
    }
}
