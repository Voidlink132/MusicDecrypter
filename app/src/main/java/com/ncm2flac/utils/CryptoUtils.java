package com.ncm2flac.utils;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class CryptoUtils {
    // NCM格式固定AES密钥（官方固定值）
    private static final byte[] NCM_CORE_KEY = "hijklmnopqrstuvw".getBytes();
    private static final byte[] NCM_META_KEY = "qqqqqqqqqqqqqqqq".getBytes();

    // AES-128-ECB解密
    public static byte[] aes128EcbDecrypt(byte[] data, byte[] key) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, keySpec);
        return cipher.doFinal(data);
    }

    // RC4密钥生成与解密
    public static byte[] rc4KeyDecrypt(byte[] data, byte[] key) {
        byte[] result = new byte[data.length];
        byte[] box = new byte[256];
        for (int i = 0; i < 256; i++) box[i] = (byte) i;

        int j = 0;
        for (int i = 0; i < 256; i++) {
            j = (j + box[i] + key[i % key.length]) & 0xff;
            byte temp = box[i];
            box[i] = box[j];
            box[j] = temp;
        }

        int i = 0;
        j = 0;
        for (int k = 0; k < data.length; k++) {
            i = (i + 1) & 0xff;
            j = (j + box[i]) & 0xff;
            byte temp = box[i];
            box[i] = box[j];
            box[j] = temp;
            result[k] = (byte) (data[k] ^ box[(box[i] + box[j]) & 0xff]);
        }
        return result;
    }

    public static byte[] getNcmCoreKey() {
        return NCM_CORE_KEY;
    }

    public static byte[] getNcmMetaKey() {
        return NCM_META_KEY;
    }
}
