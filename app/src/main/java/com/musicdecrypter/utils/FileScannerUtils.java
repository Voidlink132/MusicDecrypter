package com.musicdecrypter.utils;

import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FileScannerUtils {

    private static final String TAG = "MusicScanner";
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
        String absoluteRoot = "/storage/emulated/0";
        String envRoot = Environment.getExternalStorageDirectory().getAbsolutePath();

        // 网易云音乐
        addMusicDir("网易云音乐", absoluteRoot + "/Download/netease/cloudmusic/Music/");
        addMusicDir("网易云音乐", absoluteRoot + "/Download/Netease/CloudMusic/Music/");
        addMusicDir("网易云音乐", absoluteRoot + "/netease/cloudmusic/Music/");
        addMusicDir("网易云音乐", absoluteRoot + "/Netease/CloudMusic/Music/");
        addMusicDir("网易云音乐", absoluteRoot + "/Android/data/com.netease.cloudmusic/files/Download/");

        // QQ音乐
        addMusicDir("QQ音乐", absoluteRoot + "/Music/qqmusic/song/");
        addMusicDir("QQ音乐", absoluteRoot + "/QQMusic/song/");
        addMusicDir("QQ音乐", absoluteRoot + "/tencent/QQMusic/song/");
        addMusicDir("QQ音乐", absoluteRoot + "/Android/data/com.tencent.qqmusic/files/Music/");

        // 酷狗音乐
        addMusicDir("酷狗音乐", absoluteRoot + "/Download/kgmusic/download/");
        addMusicDir("酷狗音乐", absoluteRoot + "/KuGou/download/");
        addMusicDir("酷狗音乐", absoluteRoot + "/Android/data/com.kugou.android/files/download/");

        // 酷我音乐
        addMusicDir("酷我音乐", absoluteRoot + "/kuwo/download/music/");
        addMusicDir("酷我音乐", absoluteRoot + "/Android/data/cn.kuwo.player/files/download/");

        // 通用目录
        addMusicDir("下载目录", absoluteRoot + "/Download/");
        addMusicDir("音乐目录", absoluteRoot + "/Music/");
        addMusicDir("根目录", absoluteRoot + "/");

        addMusicDir("网易云音乐", envRoot + "/netease/cloudmusic/Music/");
        addMusicDir("QQ音乐", envRoot + "/QQMusic/song/");
        addMusicDir("酷狗音乐", envRoot + "/KuGou/download/");
    }

    private static void addMusicDir(String name, String path) {
        for (MusicDir dir : MUSIC_DIR_LIST) {
            if (dir.getPath().equals(path)) return;
        }
        MUSIC_DIR_LIST.add(new MusicDir(name, path));
    }

    private static final String[] SUPPORT_EXTS = {
            ".ncm", ".mflac", ".mgg", ".kgm", ".kgma", ".kwm",
            ".qmc0", ".qmc1", ".qmc2", ".qmc3", ".qmcflac", ".qmcogg",
            ".mp3", ".ogg", ".flac", ".wav", ".m4a" // 增加普通格式支持
    };

    public static class MusicFileInfo {
        public final String fileName;
        public final String fullPath;
        public final String platform;
        public MusicFileInfo(String fileName, String fullPath, String platform) {
            this.fileName = fileName;
            this.fullPath = fullPath;
            this.platform = platform;
        }
    }

    public static List<MusicFileInfo> scanAllMusicFiles() {
        Set<String> scannedPaths = new HashSet<>();
        List<MusicFileInfo> resultList = new ArrayList<>();

        for (MusicDir dir : MUSIC_DIR_LIST) {
            File targetDir = new File(dir.getPath());
            if (!targetDir.exists() || !targetDir.isDirectory() || !targetDir.canRead()) continue;

            List<MusicFileInfo> dirFiles = scanDirRecursive(targetDir, dir.getName());
            for (MusicFileInfo file : dirFiles) {
                if (!scannedPaths.contains(file.fullPath)) {
                    scannedPaths.add(file.fullPath);
                    resultList.add(file);
                }
            }
        }

        // 排序逻辑：网易云、QQ、酷狗、酷我 优先
        Collections.sort(resultList, new Comparator<MusicFileInfo>() {
            @Override
            public int compare(MusicFileInfo o1, MusicFileInfo o2) {
                int p1 = getPlatformPriority(o1.platform);
                int p2 = getPlatformPriority(o2.platform);
                if (p1 != p2) return p1 - p2;
                return o1.fileName.compareToIgnoreCase(o2.fileName);
            }

            private int getPlatformPriority(String platform) {
                switch (platform) {
                    case "网易云音乐": return 1;
                    case "QQ音乐": return 2;
                    case "酷狗音乐": return 3;
                    case "酷我音乐": return 4;
                    default: return 10;
                }
            }
        });

        return resultList;
    }

    private static List<MusicFileInfo> scanDirRecursive(File currentDir, String platform) {
        List<MusicFileInfo> fileList = new ArrayList<>();
        File[] files = currentDir.listFiles();
        if (files == null) return fileList;

        for (File f : files) {
            if (f.isFile()) {
                String fileName = f.getName().toLowerCase();
                for (String ext : SUPPORT_EXTS) {
                    if (fileName.endsWith(ext)) {
                        fileList.add(new MusicFileInfo(f.getName(), f.getAbsolutePath(), platform));
                        break;
                    }
                }
            } else if (f.isDirectory()) {
                // 排除 Android/data 目录下的非目标文件夹扫描，减少耗时
                if (currentDir.getAbsolutePath().endsWith("/Android/data") && 
                    !f.getAbsolutePath().contains("netease") && 
                    !f.getAbsolutePath().contains("tencent") && 
                    !f.getAbsolutePath().contains("kugou") && 
                    !f.getAbsolutePath().contains("kuwo")) {
                    continue;
                }
                fileList.addAll(scanDirRecursive(f, platform));
            }
        }
        return fileList;
    }
}
