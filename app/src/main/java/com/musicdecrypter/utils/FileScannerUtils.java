package com.musicdecrypter.utils;

import android.os.Environment;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    // 严格匹配你指定的3个核心音乐目录
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
    }

    // 支持的全量加密格式后缀
    private static final String[] SUPPORT_EXTS = {
            ".ncm", ".mflac", ".mgg", ".kgm", ".kgma",
            ".qmc0", ".qmc1", ".qmc2", ".qmc3", ".qmcflac", ".qmcogg",
            ".tm0", ".tm2", ".tm3", ".tm6", ".bkcmp3", ".bkcm4a"
    };

    // 音乐文件信息实体（用于去重）
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

    // 核心：带自动去重的全量扫描
    public static List<MusicFileInfo> scanAllMusicFiles() {
        // 用文件绝对路径唯一去重
        Set<String> scannedFilePaths = new HashSet<>();
        List<MusicFileInfo> resultList = new ArrayList<>();

        for (MusicDir dir : MUSIC_DIR_LIST) {
            List<MusicFileInfo> dirFiles = scanSingleDir(dir);
            for (MusicFileInfo file : dirFiles) {
                if (!scannedFilePaths.contains(file.fullPath)) {
                    scannedFilePaths.add(file.fullPath);
                    resultList.add(file);
                }
            }
        }
        return resultList;
    }

    // 扫描单个目录
    private static List<MusicFileInfo> scanSingleDir(MusicDir dir) {
        List<MusicFileInfo> fileList = new ArrayList<>();
        File rootDir = new File(dir.getPath());
        if (!rootDir.exists() || !rootDir.isDirectory() || !rootDir.canRead()) {
            return fileList;
        }
        scanDirRecursive(rootDir, dir.getName(), fileList);
        return fileList;
    }

    // 递归扫描子目录
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

    // 兼容旧方法
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
                List<String> subFiles = scanMusicFiles(f.getAbsolutePath());
                res.addAll(subFiles);
            }
        }
        return res;
    }
}
