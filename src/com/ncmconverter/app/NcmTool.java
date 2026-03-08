package com.ncmconverter.app;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.MessageDigest;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class NcmTool {
    // NCM解密核心配置（单独抽离，方便修改）
    private static final byte[] NCM_KEY = "163 key(Don't modify)".getBytes();
    private static final int HEADER_SKIP = 1024; // 修复偏移的核心值

    // 静态方法：NCM转FLAC，直接调用即可
    public static boolean convert(File ncmFile, File outDir) {
        try {
            // 检查文件+创建输出目录
            if (!ncmFile.exists() || !ncmFile.isFile()) return false;
            if (!outDir.exists()) outDir.mkdirs();
            File flacFile = new File(outDir, ncmFile.getName().replace(".ncm", ".flac"));

            // 初始化RC4解密
            byte[] md5Key = md5(NCM_KEY);
            SecretKeySpec keySpec = new SecretKeySpec(md5Key, "RC4");
            Cipher cipher = Cipher.getInstance("RC4/ECB/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec);

            // 读取NCM并解密写入FLAC
            FileInputStream fis = new FileInputStream(ncmFile);
            FileOutputStream fos = new FileOutputStream(flacFile);
            fis.skip(HEADER_SKIP); // 跳过头部，修复播放失败

            byte[] buffer = new byte[4096];
            int len;
            while ((len = fis.read(buffer)) != -1) {
                fos.write(cipher.update(buffer, 0, len));
            }
            fos.write(cipher.doFinal());

            // 关闭流
            fos.flush();
            fos.close();
            fis.close();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // 私有工具：MD5加密（解密用）
    private static byte[] md5(byte[] data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        return md.digest(data);
    }
}
