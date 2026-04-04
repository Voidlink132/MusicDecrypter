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

    public static final List<MusicDir> MUSIC_DIR_LIST = new ArrayList<>();
    static {
        MUSIC_DIR_LIST.add(new MusicDir("网易云音乐",
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        + "/netease/cloudmusic/Music/"));
        MUSIC_DIR_LIST.add(new MusicDir("QQ音乐",
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                        + "/qqmusic/song/"));
        MUSIC_DIR_LIST.add(new MusicDir("酷狗音乐",
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        + "/kugou/download/"));
    }

    public static List<String> scanMusicFiles(String dirPath) {
        List<String> res = new ArrayList<>();
        File dir = new File(dirPath);
        if (!dir.exists() || !dir.isDirectory()) return res;

        File[] files = dir.listFiles();
        if (files == null) return res;

        String[] exts = {".ncm", ".mflac", ".mgg", ".kgm", ".kgma", ".qmc0", ".qmcflac"};
        for (File f : files) {
            if (f.isFile()) {
                String name = f.getName();
                for (String ext : exts) {
                    if (name.endsWith(ext)) {
                        res.add(name);
                        break;
                    }
                }
            }
        }
        return res;
    }
}
