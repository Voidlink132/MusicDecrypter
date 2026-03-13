package com.ncm2flac.utils;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class CryptoUtils {
    // 【对齐ncmc】NCM官方固定密钥，不可修改
    public static final byte[] NCM_CORE_KEY = "hijklmnopqrstuvw".getBytes();
    public static final byte[] NCM_META_KEY = "qqqqqqqqqqqqqqqq".getBytes();

    // AES-128-ECB解密，对齐ncmc的实现
    public static byte[] aes128EcbDecrypt(byte[] data, byte[] key) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, keySpec);
        return cipher.doFinal(data);
    }

    // RC4解密，对齐ncmc的标准实现
    public static byte[] rc4KeyDecrypt(byte[] data, byte[] key) {
        if (data == null || key == null || key.length == 0) return data;
        byte[] box = new byte[256];
        byte[] result = new byte[data.length];

        // 初始化S盒
        for (int i = 0; i < 256; i++) box[i] = (byte) i;
        int j = 0;
        for (int i = 0; i < 256; i++) {
            j = (j + box[i] + key[i % key.length]) & 0xFF;
            byte temp = box[i];
            box[i] = box[j];
            box[j] = temp;
        }

        // RC4流解密
        int i = 0;
        j = 0;
        for (int k = 0; k < data.length; k++) {
            i = (i + 1) & 0xFF;
            j = (j + box[i]) & 0xFF;
            byte temp = box[i];
            box[i] = box[j];
            box[j] = temp;
            result[k] = (byte) (data[k] ^ box[(box[i] + box[j]) & 0xFF]);
        }
        return result;
    }

    public static byte[] getNcmCoreKey() { return NCM_CORE_KEY; }
    public static byte[] getNcmMetaKey() { return NCM_META_KEY; }
}
