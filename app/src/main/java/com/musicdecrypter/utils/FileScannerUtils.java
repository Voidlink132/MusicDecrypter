package com.musicdecrypter.utils;

import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
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

    // 全量覆盖所有路径，兼容大小写、不同APP版本
    public static final List<MusicDir> MUSIC_DIR_LIST = new ArrayList<>();
    static {
        String absoluteRoot = "/storage/emulated/0";
        String envRoot = Environment.getExternalStorageDirectory().getAbsolutePath();

        // 网易云音乐
        addMusicDir("网易云音乐", absoluteRoot + "/Download/netease/cloudmusic/Music/");
        addMusicDir("网易云音乐", absoluteRoot + "/Download/Netease/CloudMusic/Music/");
        addMusicDir("网易云音乐", absoluteRoot + "/netease/cloudmusic/Music/");
        addMusicDir("网易云音乐", absoluteRoot + "/Netease/CloudMusic/Music/");
        addMusicDir("网易云音乐", absoluteRoot + "/Music/netease/cloudmusic/");
        addMusicDir("网易云音乐", absoluteRoot + "/Android/data/com.netease.cloudmusic/files/Download/");
        addMusicDir("网易云音乐", envRoot + "/Download/netease/cloudmusic/Music/");

        // QQ音乐
        addMusicDir("QQ音乐", absoluteRoot + "/Music/qqmusic/song/");
        addMusicDir("QQ音乐", absoluteRoot + "/QQMusic/song/");
        addMusicDir("QQ音乐", absoluteRoot + "/Download/qqmusic/song/");
        addMusicDir("QQ音乐", absoluteRoot + "/tencent/QQMusic/song/");
        addMusicDir("QQ音乐", absoluteRoot + "/Android/data/com.tencent.qqmusic/files/Music/");
        addMusicDir("QQ音乐", envRoot + "/Music/qqmusic/song/");

        // 酷狗音乐
        addMusicDir("酷狗音乐", absoluteRoot + "/Download/kgmusic/download/");
        addMusicDir("酷狗音乐", absoluteRoot + "/KuGou/download/");
        addMusicDir("酷狗音乐", absoluteRoot + "/Music/kgmusic/");
        addMusicDir("酷狗音乐", absoluteRoot + "/Android/data/com.kugou.android/files/download/");
        addMusicDir("酷狗音乐", envRoot + "/Download/kgmusic/download/");

        // 通用兜底目录
        addMusicDir("下载目录", absoluteRoot + "/Download/");
        addMusicDir("音乐目录", absoluteRoot + "/Music/");
        addMusicDir("根目录", absoluteRoot + "/");
    }

    // 去重添加目录
    private static void addMusicDir(String name, String path) {
        for (MusicDir dir : MUSIC_DIR_LIST) {
            if (dir.getPath().equals(path)) return;
        }
        MUSIC_DIR_LIST.add(new MusicDir(name, path));
    }

    // 支持的加密格式
    private static final String[] SUPPORT_EXTS = {
            ".ncm", ".mflac", ".mgg", ".kgm", ".kgma", ".kwm",
            ".qmc0", ".qmc1", ".qmc2", ".qmc3", ".qmcflac", ".qmcogg"
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

    // 核心扫描方法
    public static List<MusicFileInfo> scanAllMusicFiles() {
        Set<String> scannedFilePaths = new HashSet<>();
        List<MusicFileInfo> resultList = new ArrayList<>();

        Log.d(TAG, "===== 开始扫描音乐文件 =====");
        Log.d(TAG, "共配置扫描目录数量：" + MUSIC_DIR_LIST.size());

        for (MusicDir dir : MUSIC_DIR_LIST) {
            File targetDir = new File(dir.getPath());
            if (!targetDir.exists()) {
                Log.d(TAG, "目录不存在：" + dir.getPath());
                continue;
            }
            if (!targetDir.isDirectory()) {
                Log.d(TAG, "不是有效目录：" + dir.getPath());
                continue;
            }
            if (!targetDir.canRead()) {
                Log.e(TAG, "无读取权限：" + dir.getPath());
                continue;
            }

            List<MusicFileInfo> dirFiles = scanSingleDir(dir);
            Log.d(TAG, "平台【" + dir.getName() + "】 | 路径：" + dir.getPath() + " | 扫描到文件数：" + dirFiles.size());

            for (MusicFileInfo file : dirFiles) {
                if (!scannedFilePaths.contains(file.fullPath)) {
                    scannedFilePaths.add(file.fullPath);
                    resultList.add(file);
                }
            }
        }

        Log.d(TAG, "===== 扫描完成 =====");
        Log.d(TAG, "去重后总文件数：" + resultList.size());
        return resultList;
    }

    private static List<MusicFileInfo> scanSingleDir(MusicDir dir) {
        List<MusicFileInfo> fileList = new ArrayList<>();
        File rootDir = new File(dir.getPath());
        if (!rootDir.exists() || !rootDir.isDirectory() || !rootDir.canRead()) {
            return fileList;
        }
        scanDirRecursive(rootDir, dir.getName(), fileList);
        return fileList;
    }

    private static void scanDirRecursive(File currentDir, String platform, List<MusicFileInfo> result) {
        File[] files = currentDir.listFiles();
        if (files == null) return;

        for (File f : files) {
            if (f.isFile()) {
                String fileName = f.getName();
                String lowerName = fileName.toLowerCase();
                for (String ext : SUPPORT_EXTS) {
                    if (lowerName.endsWith(ext)) {
                        result.add(new MusicFileInfo(fileName, f.getAbsolutePath(), platform));
                        break;
                    }
                }
            } else if (f.isDirectory()) {
                scanDirRecursive(f, platform, result);
            }
        }
    }
}
