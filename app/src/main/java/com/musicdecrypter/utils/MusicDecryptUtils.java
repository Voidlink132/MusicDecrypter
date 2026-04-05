package com.musicdecrypter.utils;

import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class MusicDecryptUtils {
    private static final String TAG = "MusicDecryptUtils";

    // 支持格式
    public static final String FORMAT_NCM = "ncm";
    public static final String FORMAT_QMC = "qmc";
    public static final String FORMAT_KGM = "kgm";

    /**
     * 统一解密入口
     */
    public static DecryptResult decrypt(String filePath) throws Exception {
        File file = new File(filePath);
        if (!file.exists() || !file.canRead()) {
            throw new Exception("文件不存在或无读取权限");
        }
        String fileName = file.getName();
        String ext = getFileExtension(fileName).toLowerCase();

        // 读取文件数据
        FileInputStream fis = new FileInputStream(file);
        byte[] data = new byte[(int) file.length()];
        fis.read(data);
        fis.close();

        // 按格式解密
        switch (ext) {
            case FORMAT_NCM:
                return decryptNCM(data, fileName);
            case "mgg":
            case "mflac":
            case "qmc0":
            case "qmc1":
            case "qmc2":
            case "qmc3":
            case "qmcflac":
                return decryptQMC(data, fileName, ext);
            case "kgm":
            case "kgma":
                return decryptKGM(data, fileName);
            default:
                throw new Exception("不支持的格式：" + ext);
        }
    }

    /**
     * 保存解密后的文件
     */
    public static void saveDecryptResult(DecryptResult result, String saveDirPath) throws Exception {
        File saveDir = new File(saveDirPath);
        if (!saveDir.exists()) {
            saveDir.mkdirs();
        }
        File outFile = new File(saveDir, result.getOutFileName());
        FileOutputStream fos = new FileOutputStream(outFile);
        fos.write(result.getDecryptedData());
        fos.flush();
        fos.close();
        Log.d(TAG, "文件已保存到：" + outFile.getAbsolutePath());
    }

    // 获取文件后缀
    private static String getFileExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        return lastDot == -1 ? "" : fileName.substring(lastDot + 1);
    }

    // ==================== NCM解密（网易云音乐） ====================
    private static final byte[] NCM_CORE_KEY = {0x68, 0x7A, 0x48, 0x52, 0x41, 0x6D, 0x73, 0x6F, 0x35, 0x6B, 0x49, 0x6E, 0x62, 0x61, 0x77, 0x5F};
    private static final byte[] NCM_EMPTY_IV = new byte[16];

    private static DecryptResult decryptNCM(byte[] data, String fileName) throws Exception {
        // 校验NCM文件头（CTENFAM）
        if (data.length < 7 || data[0] != 0x43 || data[1] != 0x54 || data[2] != 0x45 || data[3] != 0x4E || data[4] != 0x46 || data[5] != 0x41 || data[6] != 0x4D) {
            throw new Exception("无效NCM文件");
        }

        int offset = 10;
        // 读取密钥长度
        int keyLen = readUInt32LE(data, offset);
        offset += 4;
        if (keyLen <= 0) throw new Exception("NCM密钥长度错误");

        // 读取加密的RC4密钥，异或0x64
        byte[] encKey = Arrays.copyOfRange(data, offset, offset + keyLen);
        offset += keyLen;
        byte[] xorKey = new byte[encKey.length];
        for (int i = 0; i < encKey.length; i++) {
            xorKey[i] = (byte) (encKey[i] ^ 0x64);
        }

        // AES解密RC4密钥
        byte[] rc4Key = aesDecrypt(xorKey, NCM_CORE_KEY, NCM_EMPTY_IV);
        rc4Key = Arrays.copyOfRange(rc4Key, 17, rc4Key.length); // 截取有效密钥部分

        // 跳过元数据、CRC、封面
        int metaLen = readUInt32LE(data, offset);
        offset += 4;
        offset += 5; // 跳过固定字节
        offset += 4; // 跳过CRC32
        int coverLen = readUInt32LE(data, offset);
        offset += 4;
        offset += coverLen; // 跳过封面数据

        // RC4解密音频数据
        byte[] audioData = Arrays.copyOfRange(data, offset, data.length);
        byte[] decAudio = rc4Decrypt(audioData, rc4Key);

        // 识别格式（FLAC/MP3）
        String format = (decAudio.length >= 4 && decAudio[0] == 0x66 && decAudio[1] == 0x4C && decAudio[2] == 0x61 && decAudio[3] == 0x43) ? "flac" : "mp3";
        String outFileName = fileName.replace(".ncm", "." + format);
        return new DecryptResult(decAudio, outFileName, format);
    }

    // ==================== QMC解密（QQ音乐/MGG/MFLAC） ====================
    // QMC核心密钥表（与JS完全一致）
    private static final byte[] QMC_KEY_MAP = {
            0x77, 0x48, 0x32, 0x73, (byte) 0xDE, (byte) 0xF2, (byte) 0xC0, (byte) 0x8F, (byte) 0x99, 0x1F, (byte) 0xEE, 0x1F, 0x55, 0x0A, 0x79, 0x1D,
            0x29, 0x4D, 0x5F, 0x3A, 0x1E, 0x47, 0x23, 0x3D, (byte) 0xBD, 0x6E, 0x79, 0x5D, 0x7B, (byte) 0xF9, 0x03,
            0x1D, (byte) 0xC1, 0x74, (byte) 0x99, 0x15, 0x0B, (byte) 0xCB, (byte) 0x9F, 0x5A, 0x2F, 0x5E, (byte) 0x95, (byte) 0x98, 0x5F, 0x12,
            0x04, (byte) 0x9C, 0x7E, (byte) 0x85, 0x2E, 0x6A, 0x13, (byte) 0x9D, 0x3E, 0x6D, 0x32, 0x1E, 0x52, 0x36, (byte) 0x93,
            0x31, 0x00, 0x0E, (byte) 0xED, 0x12, (byte) 0x8E, 0x10, 0x7D, (byte) 0xE4, 0x4A, (byte) 0x90, 0x36, (byte) 0xB2, (byte) 0x99, (byte) 0xDD,
            (byte) 0xDD, 0x5D, (byte) 0xDD, 0x59, 0x53, (byte) 0x98, 0x52, 0x44, 0x4B, (byte) 0x9E, 0x32, 0x1C, 0x57, 0x38, 0x3C,
            0x5E, 0x5D, (byte) 0x9B, 0x15, (byte) 0xF5, 0x1E, 0x5C, (byte) 0x96, 0x59, 0x16, 0x2E, 0x32, 0x53, (byte) 0xD8, 0x54,
            (byte) 0x8D, 0x13, 0x43, (byte) 0xCB, (byte) 0x99, 0x15, 0x0B, (byte) 0xCB, (byte) 0x9F, 0x5A, 0x2F, 0x5E, (byte) 0x95, (byte) 0x98, 0x5F
    };

    private static DecryptResult decryptQMC(byte[] data, String fileName, String ext) throws Exception {
        // 确定输出格式（MP3/FLAC）
        String format = ("mflac".equals(ext) || "flac".equals(ext)) ? "flac" : "mp3";

        // 异或解密核心逻辑
        byte[] decrypted = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            byte key;
            if (i > 0x7FFF) {
                key = QMC_KEY_MAP[(i % 0x7FFF) % 0x80];
            } else {
                key = QMC_KEY_MAP[i % 0x80];
            }
            decrypted[i] = (byte) (data[i] ^ key);
        }

        // 生成输出文件名
        String outFileName = fileName.replaceAll("\\.(mgg|mflac|qmc\\d+|qmcflac)", "." + format);
        return new DecryptResult(decrypted, outFileName, format);
    }

    // ==================== KGM解密（酷狗音乐） ====================
    private static final byte[] KGM_CORE_KEY = {0x58, 0x21, 0x52, 0x4D, 0x41, 0x21, 0x5A, 0x58, 0x49, 0x4E, 0x47, 0x4F, 0x52, 0x4B, 0x45, 0x59};
    private static final byte[] KGM_FILE_HEADER = {0x4B, 0x47, 0x4D, 0x41}; // KGM/A文件头

    private static DecryptResult decryptKGM(byte[] data, String fileName) throws Exception {
        // 校验KGM文件头
        if (data.length < 4 || !Arrays.equals(Arrays.copyOfRange(data, 0, 4), KGM_FILE_HEADER)) {
            throw new Exception("无效KGM/KGMA文件");
        }

        // 跳过文件头（0x10字节），解密音频数据
        byte[] audioData = Arrays.copyOfRange(data, 0x10, data.length);
        byte[] decrypted = new byte[audioData.length];
        for (int i = 0; i < audioData.length; i++) {
            decrypted[i] = (byte) (audioData[i] ^ KGM_CORE_KEY[i % KGM_CORE_KEY.length]);
        }

        // 识别格式（FLAC/MP3）
        String format = (decrypted.length >= 4 && decrypted[0] == 0x66 && decrypted[1] == 0x4C && decrypted[2] == 0x61 && decrypted[3] == 0x43) ? "flac" : "mp3";
        String outFileName = fileName.replaceAll("\\.(kgm|kgma)", "." + format);
        return new DecryptResult(decrypted, outFileName, format);
    }

    // ==================== 辅助加密方法 ====================
    /**
     * 小端读取32位无符号整数
     */
    private static int readUInt32LE(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8) | ((data[offset + 2] & 0xFF) << 16) | ((data[offset + 3] & 0xFF) << 24);
    }

    /**
     * AES-CBC解密
     */
    private static byte[] aesDecrypt(byte[] data, byte[] key, byte[] iv) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
        return cipher.doFinal(data);
    }

    /**
     * RC4解密
     */
    private static byte[] rc4Decrypt(byte[] data, byte[] key) {
        int keyLen = key.length;
        int dataLen = data.length;
        byte[] result = new byte[dataLen];

        // 初始化密钥表
        byte[] sBox = new byte[256];
        for (int i = 0; i < 256; i++) {
            sBox[i] = (byte) i;
        }
        int j = 0;
        for (int i = 0; i < 256; i++) {
            j = (j + sBox[i] + (key[i % keyLen] & 0xFF)) & 0xFF;
            // 交换sBox[i]和sBox[j]
            byte temp = sBox[i];
            sBox[i] = sBox[j];
            sBox[j] = temp;
        }

        // 流解密
        int x = 0, y = 0;
        for (int i = 0; i < dataLen; i++) {
            x = (x + 1) & 0xFF;
            y = (y + sBox[x]) & 0xFF;
            // 交换sBox[x]和sBox[y]
            byte temp = sBox[x];
            sBox[x] = sBox[y];
            sBox[y] = temp;
            // 异或生成密文
            result[i] = (byte) (data[i] ^ sBox[(sBox[x] + sBox[y]) & 0xFF]);
        }
        return result;
    }

    // ==================== 解密结果实体类 ====================
    public static class DecryptResult {
        private final byte[] decryptedData;
        private final String outFileName;
        private final String format;

        public DecryptResult(byte[] decryptedData, String outFileName, String format) {
            this.decryptedData = decryptedData;
            this.outFileName = outFileName;
            this.format = format;
        }

        public byte[] getDecryptedData() {
            return decryptedData;
        }

        public String getOutFileName() {
            return outFileName;
        }

        public String getFormat() {
            return format;
        }
    }
}
