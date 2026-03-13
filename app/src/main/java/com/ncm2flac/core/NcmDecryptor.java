package com.ncm2flac.core;

import com.ncm2flac.utils.CryptoUtils;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NcmDecryptor {
    // NCM标准魔数 (0x4354454E4644414D) -> "MADFETC" 反向
    private static final long NCM_MAGIC = 0x4D4144464554434AL;
    private static final Pattern META_PATTERN = Pattern.compile("\\{.*\\}");

    private final ByteBuffer ncmBuffer;
    private byte[] audioRawData;
    private String audioFormat;
    private Map<String, Object> metadata = new HashMap<>();
    private byte[] coverImage;

    // 构造方法：初始化小端序ByteBuffer（ncmc标准写法）
    public NcmDecryptor(byte[] ncmData) {
        this.ncmBuffer = ByteBuffer.wrap(ncmData);
        this.ncmBuffer.order(ByteOrder.LITTLE_ENDIAN);
    }

    // 核心解密方法（和ncmc一致的执行流程）
    public boolean decrypt() {
        try {
            // 1. 魔数校验（ncmc核心校验逻辑）
            if (ncmBuffer.getLong() != NCM_MAGIC) {
                throw new IllegalArgumentException("不是有效的NCM文件：魔数校验失败");
            }

            // 2. 跳过版本号 + 解密RC4密钥（ncmc标准步骤）
            ncmBuffer.skipBytes(2); // 跳过2字节版本
            int keyLen = ncmBuffer.getInt();
            byte[] encKey = new byte[keyLen];
            ncmBuffer.get(encKey);
            byte[] rc4Key = decryptRC4Key(encKey);

            // 3. 解密元数据（适配ncmc的AES+Base64解析）
            int metaLen = ncmBuffer.getInt();
            if (metaLen > 0) {
                byte[] encMeta = new byte[metaLen];
                ncmBuffer.get(encMeta);
                parseMetadata(encMeta);
            }

            // 4. 跳过CRC32 + 预留字段（ncmc标准偏移）
            ncmBuffer.skipBytes(4); // CRC32
            int gapLen = ncmBuffer.getInt();
            ncmBuffer.skipBytes(gapLen); // 预留间隙

            // 5. 解密音频流（ncmc的RC4流式解密）
            int audioLen = ncmBuffer.remaining();
            byte[] encAudio = new byte[audioLen];
            ncmBuffer.get(encAudio);
            audioRawData = CryptoUtils.rc4KeyDecrypt(encAudio, rc4Key);

            // 6. 识别音频格式（FLAC/MP3，ncmc自动识别）
            identifyAudioFormat();

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // 解密RC4密钥（ncmc的AES-128-ECB + 异或0x64）
    private byte[] decryptRC4Key(byte[] encKey) throws Exception {
        // 第一步：所有字节异或0x64（ncmc必做步骤）
        for (int i = 0; i < encKey.length; i++) {
            encKey[i] ^= 0x64;
        }
        // 第二步：AES-128-ECB解密（ncmc标准加密）
        byte[] decKey = CryptoUtils.aes128EcbDecrypt(encKey, CryptoUtils.getNcmCoreKey());
        // 第三步：剔除头部固定字符串 "neteasecloudmusic"（22个字符）
        return new String(decKey).substring(22).getBytes();
    }

    // 解析元数据（ncmc的AES+Base64+正则提取JSON）
    private void parseMetadata(byte[] encMeta) throws Exception {
        // 第一步：所有字节异或0x63（ncmc必做步骤）
        for (int i = 0; i < encMeta.length; i++) {
            encMeta[i] ^= 0x63;
        }
        // 第二步：Base64解码（ncmc的URL安全解码）
        byte[] base64Meta = Base64.getDecoder().decode(encMeta);
        // 第三步：AES-128-ECB解密
        byte[] decMeta = CryptoUtils.aes128EcbDecrypt(base64Meta, CryptoUtils.getNcmMetaKey());
        // 第四步：剔除头部"music:" + 正则提取JSON（ncmc解决元数据乱码）
        String metaStr = new String(decMeta).substring(6);
        Matcher matcher = META_PATTERN.matcher(metaStr);
        if (matcher.find()) {
            metaStr = matcher.group(0);
            // 简易解析核心元数据（和ncmc保持一致的提取字段）
            metadata.put("title", getJsonValue(metaStr, "musicName"));
            metadata.put("artist", getJsonValue(metaStr, "artist"));
            metadata.put("album", getJsonValue(metaStr, "album"));
        }

        // 解析封面（ncmc标准读取方式）
        int coverLen = ncmBuffer.getInt();
        if (coverLen > 0 && coverLen < ncmBuffer.remaining()) {
            coverImage = new byte[coverLen];
            ncmBuffer.get(coverImage);
        }
    }

    // 识别音频格式（ncmc的文件头特征识别）
    private void identifyAudioFormat() {
        if (audioRawData == null || audioRawData.length < 4) {
            audioFormat = "flac";
            return;
        }
        // FLAC头：0x66 0x4C 0x61 0x43 ("fLaC")
        if (audioRawData[0] == 0x66 && audioRawData[1] == 0x4C && audioRawData[2] == 0x61 && audioRawData[3] == 0x43) {
            audioFormat = "flac";
        }
        // MP3头：ID3标识 0x49 0x44 0x33
        else if (audioRawData[0] == 0x49 && audioRawData[1] == 0x44 && audioRawData[2] == 0x33) {
            audioFormat = "mp3";
        }
        // 兜底为FLAC（ncmc默认）
        else {
            audioFormat = "flac";
        }
    }

    // 简易JSON值提取（ncmc轻量解析，避免引入GSON依赖）
    private String getJsonValue(String json, String key) {
        String keyStr = "\"" + key + "\":";
        int start = json.indexOf(keyStr);
        if (start == -1) return "未知";
        start += keyStr.length();
        // 处理字符串/数组类型（适配歌手数组）
        if (json.charAt(start) == '[') {
            int end = json.indexOf(']', start) + 1;
            return json.substring(start, end).replace("[", "").replace("]", "").replace("\"", "");
        } else if (json.charAt(start) == '"') {
            start++;
            int end = json.indexOf("\"", start);
            return json.substring(start, end);
        } else {
            int end = json.indexOf(",", start);
            if (end == -1) end = json.indexOf("}", start);
            return json.substring(start, end);
        }
    }

    // Getter方法
    public byte[] getAudioRawData() {
        return audioRawData;
    }

    public String getAudioFormat() {
        return audioFormat;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public byte[] getCoverImage() {
        return coverImage;
    }
}
