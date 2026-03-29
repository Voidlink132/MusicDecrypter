package com.musicdecrypter.utils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class FileScannerUtils {

    private static final List<String> SUPPORTED_FORMATS = List.of("mp3", "flac", "ncm", "qmc", "kgm", "mgg", "ogg");

    public static class MusicDir {
        private final String name;
        private final String path;
        private final int tableId;

        public MusicDir(String name, String path, int tableId) {
            this.name = name;
            this.path = path;
            this.tableId = tableId;
        }

        public String getPath() {
            return path;
        }

        public int getTableId() {
            return tableId;
        }
    }

    public static List<String> scanMusicFiles(String dirPath) {
        List<String> fileList = new ArrayList<>();
        File dir = new File(dirPath);

        if (!dir.exists() || !dir.isDirectory()) {
            return fileList;
        }

        File[] files = dir.listFiles();
        if (files == null) {
            return fileList;
        }

        for (File file : files) {
            if (file.isFile()) {
                String extension = getFileExtension(file.getName());
                if (SUPPORTED_FORMATS.contains(extension.toLowerCase())) {
                    fileList.add(file.getName());
                }
            }
        }

        return fileList;
    }

    private static String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex == -1) {
            return "";
        }
        return fileName.substring(lastDotIndex + 1);
    }
}
