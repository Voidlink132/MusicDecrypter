package com.ncm2flac.core;

import com.ncm2flac.utils.FileUtils;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;

public class AudioPackager {

    // 写入解密后的音频数据到文件（无损，无二次编码）
    public static File writeAudioFile(byte[] audioData, String format, File outputFile) throws Exception {
        // 直接写入原始音频流，100%无损
        FileUtils.writeBytesToFile(audioData, outputFile);
        return outputFile;
    }

    // 流式写入大文件
    public static void writeAudioStream(byte[] audioData, File outputFile) throws Exception {
        ByteArrayInputStream bais = new ByteArrayInputStream(audioData);
        FileOutputStream fos = new FileOutputStream(outputFile);
        FileUtils.writeStreamToFile(bais, fos);
    }
}
