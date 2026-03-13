package com.ncm2flac.utils;

import android.content.Context;
import android.net.Uri;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class FileUtils {

    // 从Uri读取文件为字节数组
    public static byte[] readUriToBytes(Context context, Uri uri) throws Exception {
        InputStream is = context.getContentResolver().openInputStream(uri);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024 * 8];
        int len;
        while ((len = is.read(buffer)) != -1) {
            bos.write(buffer, 0, len);
        }
        is.close();
        bos.close();
        return bos.toByteArray();
    }

    // 写入字节数组到文件
    public static void writeBytesToFile(byte[] data, File file) throws Exception {
        FileOutputStream fos = new FileOutputStream(file);
        fos.write(data);
        fos.flush();
        fos.close();
    }

    // 流式写入文件（大文件专用，避免OOM）
    public static void writeStreamToFile(InputStream is, OutputStream os) throws Exception {
        byte[] buffer = new byte[1024 * 8];
        int len;
        while ((len = is.read(buffer)) != -1) {
            os.write(buffer, 0, len);
        }
        is.close();
        os.flush();
        os.close();
    }

    // 获取文件扩展名
    public static String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
    }

    // 替换文件扩展名
    public static String replaceFileExtension(String fileName, String newExt) {
        if (fileName == null) return "";
        int dotIndex = fileName.lastIndexOf(".");
        if (dotIndex == -1) {
            return fileName + "." + newExt;
        }
        return fileName.substring(0, dotIndex) + "." + newExt;
    }
}
