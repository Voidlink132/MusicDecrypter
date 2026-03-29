package com.musicdecrypter.utils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class FileScannerUtils {

    public static class MusicDir {
        private final String name;
        private final String path;
        private final int tableId;

        public MusicDir(String name, String path, int tableId) {
            this.name = name;
            this.path = path;
            this.tableId = tableId;
        }

        public String getName() { return name; }
        public String getPath() { return path; }
        public int getTableId() { return tableId; }
    }

    // 扫描指定目录下的所有音乐文件
    public static List<String> scanMusicFiles(String dirPath) {
        List<String> fileList = new ArrayList<>();
        File dir = new File(dirPath);
        if (!dir.exists() || !dir.isDirectory()) {
            return fileList;
        }

        File[] files = dir.listFiles();
        if (files == null) return fileList;

        for (File file : files) {
            if (file.isFile() && isMusicFile(file.getName())) {
                fileList.add(file.getName());
            }
        }
        return fileList;
    }

    // 判断是否为支持的音乐格式
    private static boolean isMusicFile(String fileName) {
        String[] exts = {".ncm", ".mflac", ".mgg", ".kgm", ".kgma", ".qmcogg", ".qmcflac", ".qmc0"};
        for (String ext : exts) {
            if (fileName.endsWith(ext)) return true;
        }
        return false;
    }
}
