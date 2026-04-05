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

    // 全量覆盖：Download、Android、Music三大根目录，所有主流音乐APP路径
    public static final List<MusicDir> MUSIC_DIR_LIST = new ArrayList<>();
    static {
        String rootPath = Environment.getExternalStorageDirectory().getAbsolutePath();

        // ==================== 网易云音乐 全路径 ====================
        MUSIC_DIR_LIST.add(new MusicDir("网易云音乐", rootPath + "/Download/netease/cloudmusic/Music/"));
        MUSIC_DIR_LIST.add(new MusicDir("网易云音乐", rootPath + "/Download/Netease/CloudMusic/Music/"));
        MUSIC_DIR_LIST.add(new MusicDir("网易云音乐", rootPath + "/netease/cloudmusic/Music/"));
        MUSIC_DIR_LIST.add(new MusicDir("网易云音乐", rootPath + "/Netease/CloudMusic/Music/"));
        MUSIC_DIR_LIST.add(new MusicDir("网易云音乐", rootPath + "/Music/netease/cloudmusic/"));
        MUSIC_DIR_LIST.add(new MusicDir("网易云音乐", rootPath + "/Android/data/com.netease.cloudmusic/files/Download/"));
        MUSIC_DIR_LIST.add(new MusicDir("网易云音乐", rootPath + "/Android/data/com.netease.cloudmusic/cache/Download/"));

        // ==================== QQ音乐 全路径 ====================
        MUSIC_DIR_LIST.add(new MusicDir("QQ音乐", rootPath + "/Music/qqmusic/song/"));
        MUSIC_DIR_LIST.add(new MusicDir("QQ音乐", rootPath + "/QQMusic/song/"));
        MUSIC_DIR_LIST.add(new MusicDir("QQ音乐", rootPath + "/Download/qqmusic/song/"));
        MUSIC_DIR_LIST.add(new MusicDir("QQ音乐", rootPath + "/tencent/QQMusic/song/"));
        MUSIC_DIR_LIST.add(new MusicDir("QQ音乐", rootPath + "/Android/data/com.tencent.qqmusic/files/Music/"));
        MUSIC_DIR_LIST.add(new MusicDir("QQ音乐", rootPath + "/Android/data/com.tencent.qqmusic/cache/song/"));

        // ==================== 酷狗音乐 全路径 ====================
        MUSIC_DIR_LIST.add(new MusicDir("酷狗音乐", rootPath + "/Download/kgmusic/download/"));
        MUSIC_DIR_LIST.add(new MusicDir("酷狗音乐", rootPath + "/KuGou/download/"));
        MUSIC_DIR_LIST.add(new MusicDir("酷狗音乐", rootPath + "/Music/kgmusic/"));
        MUSIC_DIR_LIST.add(new MusicDir("酷狗音乐", rootPath + "/Android/data/com.kugou.android/files/download/"));
        MUSIC_DIR_LIST.add(new MusicDir("酷狗音乐", rootPath + "/Android/data/com.kugou.android/cache/song/"));

        // ==================== 酷我音乐 全路径 ====================
        MUSIC_DIR_LIST.add(new MusicDir("酷我音乐", rootPath + "/kuwo/download/music/"));
        MUSIC_DIR_LIST.add(new MusicDir("酷我音乐", rootPath + "/Download/kuwo/music/"));
        MUSIC_DIR_LIST.add(new MusicDir("酷我音乐", rootPath + "/Music/kuwo/"));
        MUSIC_DIR_LIST.add(new MusicDir("酷我音乐", rootPath + "/Android/data/cn.kuwo.player/files/download/"));

        // ==================== 咪咕音乐 全路径 ====================
        MUSIC_DIR_LIST.add(new MusicDir("咪咕音乐", rootPath + "/MiGu/Song/"));
        MUSIC_DIR_LIST.add(new MusicDir("咪咕音乐", rootPath + "/Download/migu/music/"));
        MUSIC_DIR_LIST.add(new MusicDir("咪咕音乐", rootPath + "/Music/migu/"));
        MUSIC_DIR_LIST.add(new MusicDir("咪咕音乐", rootPath + "/Android/data/cmccwm.mobilemusic/files/download/"));

        // ==================== 通用兜底目录 ====================
        MUSIC_DIR_LIST.add(new MusicDir("下载目录", rootPath + "/Download/"));
        MUSIC_DIR_LIST.add(new MusicDir("音乐目录", rootPath + "/Music/"));
    }

    // 支持的全量加密格式后缀
    private static final String[] SUPPORT_EXTS = {
            ".ncm", ".mflac", ".mgg", ".kgm", ".kgma", ".kwm",
            ".qmc0", ".qmc1", ".qmc2", ".qmc3", ".qmcflac", ".qmcogg",
            ".tm0", ".tm2", ".tm3", ".tm6", ".bkcmp3", ".bkcm4a", ".mg3d"
    };

    // 音乐文件信息实体
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

    // 核心：带自动去重、日志调试的全量扫描
    public static List<MusicFileInfo> scanAllMusicFiles() {
        Set<String> scannedFilePaths = new HashSet<>();
        List<MusicFileInfo> resultList = new ArrayList<>();

        Log.d(TAG, "开始扫描音乐文件，共配置" + MUSIC_DIR_LIST.size() + "个目录");

        for (MusicDir dir : MUSIC_DIR_LIST) {
            File targetDir = new File(dir.getPath());
            // 目录校验：是否存在、是否是目录、是否可读
            if (!targetDir.exists()) {
                Log.d(TAG, "目录不存在：" + dir.getPath());
                continue;
            }
            if (!targetDir.isDirectory()) {
                Log.d(TAG, "不是目录：" + dir.getPath());
                continue;
            }
            if (!targetDir.canRead()) {
                Log.e(TAG, "无读取权限：" + dir.getPath());
                continue;
            }

            // 扫描该目录
            List<MusicFileInfo> dirFiles = scanSingleDir(dir);
            Log.d(TAG, "目录【" + dir.getName() + "】扫描到" + dirFiles.size() + "个文件，路径：" + dir.getPath());

            // 去重添加
            for (MusicFileInfo file : dirFiles) {
                if (!scannedFilePaths.contains(file.fullPath)) {
                    scannedFilePaths.add(file.fullPath);
                    resultList.add(file);
                }
            }
        }

        Log.d(TAG, "扫描完成，共去重后得到" + resultList.size() + "个加密音乐文件");
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
                // 匹配加密格式
                for (String ext : SUPPORT_EXTS) {
                    if (lowerName.endsWith(ext)) {
                        result.add(new MusicFileInfo(fileName, f.getAbsolutePath(), platform));
                        break;
                    }
                }
            } else if (f.isDirectory()) {
                // 递归扫描子目录
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
