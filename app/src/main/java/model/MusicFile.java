package com.musicdecrypter.model;

// 音乐文件实体类，和三周前完全一致
public class MusicFile {
    private String fileName;
    private String filePath;

    public MusicFile(String fileName, String filePath) {
        this.fileName = fileName;
        this.filePath = filePath;
    }

    // 获取文件名
    public String getFileName() {
        return fileName;
    }

    // 获取文件完整路径
    public String getFilePath() {
        return filePath;
    }
}
