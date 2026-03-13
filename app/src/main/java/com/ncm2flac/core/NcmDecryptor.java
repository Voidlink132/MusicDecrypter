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
    // NCM标准魔数
    private static final long NCM_MAGIC = 0x4D4144464554434AL;
    private static final Pattern META_PATTERN = Pattern.compile("\\{.*\\}");

    private final ByteBuffer ncmBuffer;
    private byte[] audioRawData;
    private String audioFormat;
    private Map<String, Object> metadata = new HashMap<>();
    private byte[] coverImage;

    public NcmDecryptor(byte[] ncmData) {
        this.ncmBuffer = ByteBuffer.wrap(ncmData);
        this.ncmBuffer.order(ByteOrder.LITTLE_ENDIAN);
    }

    // 核心解密方法，修复所有字节跳过逻辑
    public boolean decrypt() {
        try {
            // 1. 魔数校验
            if (ncmBuffer.getLong() != NCM_MAGIC) {
                throw new IllegalArgumentException("不是有效的NCM文件：魔数校验失败");
            }

            // 2. 跳过版本号 + 解密RC4密钥（修复skipBytes）
            ncmBuffer.position(ncmBuffer.position() + 2); // 跳过2字节版本号
            int keyLen = ncmBuffer.getInt();
            byte[] encKey = new byte[keyLen];
            ncmBuffer.get(encKey);
            byte[] rc4Key = decryptRC4Key(encKey);

            // 3. 解密元数据
            int metaLen = ncmBuffer.getInt();
            if (metaLen > 0) {
                byte[] encMeta = new byte[metaLen];
                ncmBuffer.get(encMeta);
                parseMetadata(encMeta);
            }

            // 4. 跳过CRC32 + 预留字段（修复skipBytes）
            ncmBuffer.position(ncmBuffer.position() + 4); // 跳过CRC32
            int gapLen = ncmBuffer.getInt();
            ncmBuffer.position(ncmBuffer.position() + gapLen); // 跳过预留间隙

            // 5. 解密音频流
            int audioLen = ncmBuffer.remaining();
            byte[] encAudio = new byte[audioLen];
            ncmBuffer.get(encAudio);
            audioRawData = CryptoUtils.rc4KeyDecrypt(encAudio, rc4Key);

            // 6. 识别音频格式
            identifyAudioFormat();

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private byte[] decryptRC4Key(byte[] encKey) throws Exception {
        for (int i = 0; i < encKey.length; i++) {
            encKey[i] ^= 0x64;
        }
        byte[] decKey = CryptoUtils.aes128EcbDecrypt(encKey, CryptoUtils.getNcmCoreKey());
        return new String(decKey).substring(22).getBytes();
    }

    private void parseMetadata(byte[] encMeta) throws Exception {
        for (int i = 0; i < encMeta.length; i++) {
            encMeta[i] ^= 0x63;
        }
        byte[] base64Meta = Base64.getDecoder().decode(encMeta);
        byte[] decMeta = CryptoUtils.aes128EcbDecrypt(base64Meta, CryptoUtils.getNcmMetaKey());
        String metaStr = new String(decMeta).substring(6);
        Matcher matcher = META_PATTERN.matcher(metaStr);
        if (matcher.find()) {
            metaStr = matcher.group(0);
            metadata.put("title", getJsonValue(metaStr, "musicName"));
            metadata.put("artist", getJsonValue(metaStr, "artist"));
            metadata.put("album", getJsonValue(metaStr, "album"));
        }

        // 读取封面（仅读取，不写入，避免API报错）
        int coverLen = ncmBuffer.getInt();
        if (coverLen > 0 && coverLen < ncmBuffer.remaining()) {
            coverImage = new byte[coverLen];
            ncmBuffer.get(coverImage);
        }
    }

    private void identifyAudioFormat() {
        if (audioRawData == null || audioRawData.length < 4) {
            audioFormat = "flac";
            return;
        }
        if (audioRawData[0] == 0x66 && audioRawData[1] == 0x4C && audioRawData[2] == 0x61 && audioRawData[3] == 0x43) {
            audioFormat = "flac";
        } else if (audioRawData[0] == 0x49 && audioRawData[1] == 0x44 && audioRawData[2] == 0x33) {
            audioFormat = "mp3";
        } else {
            audioFormat = "flac";
        }
    }

    private String getJsonValue(String json, String key) {
        String keyStr = "\"" + key + "\":";
        int start = json.indexOf(keyStr);
        if (start == -1) return "未知";
        start += keyStr.length();
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
