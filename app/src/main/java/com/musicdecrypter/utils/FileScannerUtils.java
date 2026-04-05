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

    // 核心优化：全路径覆盖，兼容大小写、多APP版本、沙盒目录
    public static final List<MusicDir> MUSIC_DIR_LIST = new ArrayList<>();
    static {
        String absoluteRoot = "/storage/emulated/0";
        String envRoot = Environment.getExternalStorageDirectory().getAbsolutePath();

        // 网易云音乐（大小写+多路径）
        addMusicDir("网易云音乐", absoluteRoot + "/Download/netease/cloudmusic/Music/");
        addMusicDir("网易云音乐", absoluteRoot + "/Download/Netease/CloudMusic/Music/");
        addMusicDir("网易云音乐", absoluteRoot + "/netease/cloudmusic/Music/");
        addMusicDir("网易云音乐", absoluteRoot + "/Netease/CloudMusic/Music/");
        addMusicDir("网易云音乐", absoluteRoot + "/Android/data/com.netease.cloudmusic/files/Download/");

        // QQ音乐（大小写+多路径）
        addMusicDir("QQ音乐", absoluteRoot + "/Music/qqmusic/song/");
        addMusicDir("QQ音乐", absoluteRoot + "/QQMusic/song/");
        addMusicDir("QQ音乐", absoluteRoot + "/tencent/QQMusic/song/");
        addMusicDir("QQ音乐", absoluteRoot + "/Android/data/com.tencent.qqmusic/files/Music/");

        // 酷狗音乐（大小写+多路径）
        addMusicDir("酷狗音乐", absoluteRoot + "/Download/kgmusic/download/");
        addMusicDir("酷狗音乐", absoluteRoot + "/KuGou/download/");
        addMusicDir("酷狗音乐", absoluteRoot + "/Android/data/com.kugou.android/files/download/");

        // 酷我音乐
        addMusicDir("酷我音乐", absoluteRoot + "/kuwo/download/music/");
        addMusicDir("酷我音乐", absoluteRoot + "/Android/data/cn.kuwo.player/files/download/");

        // 通用兜底目录
        addMusicDir("下载目录", absoluteRoot + "/Download/");
        addMusicDir("音乐目录", absoluteRoot + "/Music/");
        addMusicDir("根目录", absoluteRoot + "/");

        // 兼容Environment路径
        addMusicDir("网易云音乐", envRoot + "/netease/cloudmusic/Music/");
        addMusicDir("QQ音乐", envRoot + "/QQMusic/song/");
        addMusicDir("酷狗音乐", envRoot + "/KuGou/download/");
    }

    // 去重添加目录，避免重复扫描
    private static void addMusicDir(String name, String path) {
        for (MusicDir dir : MUSIC_DIR_LIST) {
            if (dir.getPath().equals(path)) return;
        }
        MUSIC_DIR_LIST.add(new MusicDir(name, path));
    }

    // 支持的加密格式（覆盖主流平台）
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

    // 核心扫描方法：遍历所有目录，递归扫描子目录
    public static List<MusicFileInfo> scanAllMusicFiles() {
        Set<String> scannedPaths = new HashSet<>();
        List<MusicFileInfo> resultList = new ArrayList<>();

        Log.d(TAG, "扫描目录总数：" + MUSIC_DIR_LIST.size());
        for (MusicDir dir : MUSIC_DIR_LIST) {
            File targetDir = new File(dir.getPath());
            if (!targetDir.exists()) {
                Log.d(TAG, "目录不存在：" + dir.getPath());
                continue;
            }
            if (!targetDir.isDirectory()) {
                Log.d(TAG, "非目录：" + dir.getPath());
                continue;
            }
            if (!targetDir.canRead()) {
                Log.e(TAG, "无权限：" + dir.getPath());
                continue;
            }

            // 递归扫描当前目录及子目录
            List<MusicFileInfo> dirFiles = scanDirRecursive(targetDir, dir.getName());
            for (MusicFileInfo file : dirFiles) {
                if (!scannedPaths.contains(file.fullPath)) {
                    scannedPaths.add(file.fullPath);
                    resultList.add(file);
                }
            }
        }
        Log.d(TAG, "扫描完成，去重后文件数：" + resultList.size());
        return resultList;
    }

    // 递归扫描子目录
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
                fileList.addAll(scanDirRecursive(f, platform));
            }
        }
        return fileList;
    }
}
