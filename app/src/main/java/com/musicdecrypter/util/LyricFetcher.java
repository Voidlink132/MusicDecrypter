package com.musicdecrypter.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.musicdecrypter.model.Song;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class LyricFetcher {
    private static final String TAG = "LyricFetcher";
    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();

    public interface LyricCallback {
        void onSuccess(File lyricFile);
        void onError(String msg);
    }

    /**
     * 通过 Song 对象精准获取歌词 —— 直接用平台 ID 查询，不再用关键词搜索，
     * 避免搜索结果错配、歌词与歌曲不对应的问题。
     * 歌词文件名直接取自 fileName 参数（与歌曲文件名一致），保证同名配对。
     */
    public static void fetchLyricBySong(Context context, Song song, String songFileName, File saveDir, LyricCallback callback) {
        String platform = song.getPlatform();
        String id = song.getId();

        // 歌词文件名与歌曲保持完全一致（去扩展名后加 .lrc/.srt）
        String baseName = songFileName;
        if (baseName.contains(".")) {
            baseName = baseName.substring(0, baseName.lastIndexOf("."));
        }
        final String finalBaseName = baseName;

        if ("netease".equals(platform)) {
            // 网易云：直接用 song ID 获取歌词，精确匹配
            long songId;
            try {
                songId = Long.parseLong(id);
            } catch (NumberFormatException e) {
                callback.onError("网易云歌曲ID格式异常: " + id);
                return;
            }
            downloadNeteaseLyric(context, songId, finalBaseName, saveDir, callback);
        } else if ("qq".equals(platform)) {
            // QQ音乐：用 songmid 获取歌词
            fetchQQLyric(context, id, finalBaseName, saveDir, callback);
        } else if ("kugou".equals(platform)) {
            // 酷狗：用 hash 获取歌词
            fetchKugouLyric(context, id, song.getAlbum(), finalBaseName, saveDir, callback);
        } else {
            // 未知平台，回退到关键词搜索
            fetchLyric(context, songFileName, saveDir, callback);
        }
    }

    // ==================== 网易云歌词（按 ID 精确获取） ====================

    private static void downloadNeteaseLyric(Context context, long songId, String finalBaseName, File saveDir, LyricCallback callback) {
        String lyricUrl = "https://music.163.com/api/song/lyric?id=" + songId + "&lv=1&kv=1&tv=-1";
        Request request = new Request.Builder()
                .url(lyricUrl)
                .addHeader("User-Agent", "Mozilla/5.0")
                .addHeader("Referer", "https://music.163.com/")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError("获取网易云歌词失败: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    callback.onError("歌词响应错误: " + response.code());
                    return;
                }
                try {
                    String body = response.body().string();
                    JsonObject json = JsonParser.parseString(body).getAsJsonObject();
                    processLyric(context, json, finalBaseName, saveDir, callback);
                } catch (Exception e) {
                    callback.onError("解析歌词失败: " + e.getMessage());
                }
            }
        });
    }

    // ==================== QQ音乐歌词 ====================

    private static void fetchQQLyric(Context context, String songmid, String finalBaseName, File saveDir, LyricCallback callback) {
        String data = "{\"songmid\":\"" + songmid + "\",\"g_tk\":5381,\"format\":\"json\",\"inCharset\":\"utf-8\",\"outCharset\":\"utf-8\",\"platform\":\"mac\"}";
        String url = "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_yqq.fcg?data=" + Uri.encode(data);
        Request request = new Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0")
                .addHeader("Referer", "https://y.qq.com/")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError("获取QQ歌词失败: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    callback.onError("QQ歌词响应错误: " + response.code());
                    return;
                }
                try {
                    String body = response.body().string();
                    // QQ歌词可能返回 JSONP，需要解析
                    if (body.startsWith("callback(") || body.startsWith("MusicJsonCallback")) {
                        body = body.substring(body.indexOf("(") + 1, body.lastIndexOf(")"));
                    }
                    JsonObject json = JsonParser.parseString(body).getAsJsonObject();

                    String originalLrc = "";
                    if (json.has("lyric") && !json.get("lyric").isJsonNull()) {
                        // QQ返回的是 Base64 编码的歌词
                        String encoded = json.get("lyric").getAsString();
                        originalLrc = decodeBase64(encoded);
                    }

                    String translatedLrc = "";
                    if (json.has("trans") && !json.get("trans").isJsonNull()) {
                        String encoded = json.get("trans").getAsString();
                        translatedLrc = decodeBase64(encoded);
                    }

                    // 构造与网易云相同格式的 JSON，复用 processLyric
                    JsonObject lyricObj = new JsonObject();
                    JsonObject lrcObj = new JsonObject();
                    lrcObj.addProperty("lyric", originalLrc);
                    lyricObj.add("lrc", lrcObj);
                    if (!TextUtils.isEmpty(translatedLrc)) {
                        JsonObject tlyricObj = new JsonObject();
                        tlyricObj.addProperty("lyric", translatedLrc);
                        lyricObj.add("tlyric", tlyricObj);
                    }
                    processLyric(context, lyricObj, finalBaseName, saveDir, callback);
                } catch (Exception e) {
                    callback.onError("解析QQ歌词失败: " + e.getMessage());
                }
            }
        });
    }

    private static String decodeBase64(String encoded) {
        try {
            byte[] bytes = android.util.Base64.decode(encoded, android.util.Base64.DEFAULT);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return encoded; // 如果不是 Base64，直接返回原文
        }
    }

    // ==================== 酷狗歌词 ====================

    private static void fetchKugouLyric(Context context, String hash, String album, String finalBaseName, File saveDir, LyricCallback callback) {
        String albumId = "0";
        if (album != null && album.contains("__ID__")) {
            String[] parts = album.split("__ID__");
            if (parts.length > 1) albumId = parts[1];
        }
        // 酷狗获取播放信息时，data 中有 lyrics 字段
        String url = "https://www.kugou.com/yy/index.php?r=play/getdata&hash=" + hash + "&album_id=" + albumId;
        Request request = new Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0")
                .addHeader("Referer", "https://www.kugou.com/")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError("获取酷狗歌词失败: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    callback.onError("酷狗歌词响应错误: " + response.code());
                    return;
                }
                try {
                    String body = response.body().string();
                    JsonObject json = JsonParser.parseString(body).getAsJsonObject();

                    String lyrics = "";
                    if (json.has("data") && !json.get("data").isJsonNull()) {
                        JsonObject data = json.getAsJsonObject("data");
                        if (data.has("lyrics") && !data.get("lyrics").isJsonNull()) {
                            lyrics = data.get("lyrics").getAsString();
                        }
                    }

                    if (TextUtils.isEmpty(lyrics)) {
                        callback.onError("酷狗未提供歌词");
                        return;
                    }

                    // 构造统一格式
                    JsonObject lyricObj = new JsonObject();
                    JsonObject lrcObj = new JsonObject();
                    lrcObj.addProperty("lyric", lyrics);
                    lyricObj.add("lrc", lrcObj);
                    processLyric(context, lyricObj, finalBaseName, saveDir, callback);
                } catch (Exception e) {
                    callback.onError("解析酷狗歌词失败: " + e.getMessage());
                }
            }
        });
    }

    // ==================== 旧版关键词搜索（作为回退） ====================

    public static void fetchLyric(Context context, String fileName, File saveDir, LyricCallback callback) {
        String baseName = fileName;
        if (baseName.contains(".")) {
            baseName = baseName.substring(0, baseName.lastIndexOf("."));
        }
        final String finalTargetFileName = baseName;

        String keyword = finalTargetFileName.replaceAll("\\[.*?\\]", "")
                .replaceAll("\\(.*?\\)", "")
                .replaceAll("(?i)copy", "")
                .replaceAll("(?i)lyrics", "")
                .trim();

        searchNetease(context, keyword, finalTargetFileName, saveDir, callback);
    }

    private static void searchNetease(Context context, String keyword, String finalTargetFileName, File saveDir, LyricCallback callback) {
        String searchUrl = "https://music.163.com/api/search/get?s=" + UriUtils.encode(keyword) + "&type=1&limit=1";
        Request request = new Request.Builder()
                .url(searchUrl)
                .addHeader("User-Agent", "Mozilla/5.0")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError("搜索失败: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    callback.onError("搜索响应错误: " + response.code());
                    return;
                }
                try {
                    String body = response.body().string();
                    JsonObject json = JsonParser.parseString(body).getAsJsonObject();
                    if (json.has("result")) {
                        JsonObject result = json.getAsJsonObject("result");
                        if (result != null && result.has("songs")) {
                            JsonArray songs = result.getAsJsonArray("songs");
                            if (songs != null && songs.size() > 0) {
                                JsonObject song = songs.get(0).getAsJsonObject();
                                long id = song.get("id").getAsLong();
                                downloadNeteaseLyric(context, id, finalTargetFileName, saveDir, callback);
                                return;
                            }
                        }
                    }
                    callback.onError("未找到匹配歌曲");
                } catch (Exception e) {
                    callback.onError("解析搜索结果失败: " + e.getMessage());
                }
            }
        });
    }

    // ==================== 通用歌词处理 ====================

    private static void processLyric(Context context, JsonObject json, String finalBaseName, File saveDir, LyricCallback callback) {
        SharedPreferences sp = context.getSharedPreferences("config", Context.MODE_PRIVATE);
        boolean bilingual = sp.getBoolean("bilingual_lyric", false);
        String encoding = sp.getString("lyric_encoding", "UTF-8");
        String format = sp.getString("lyric_format", "LRC");
        String bilingualType = sp.getString("bilingual_type", "合并");
        String combineSymbol = sp.getString("combine_symbol", "/");

        String originalLrc = "";
        if (json.has("lrc") && json.getAsJsonObject("lrc").has("lyric")) {
            originalLrc = json.getAsJsonObject("lrc").get("lyric").getAsString();
        }

        String translatedLrc = "";
        if (json.has("tlyric") && json.getAsJsonObject("tlyric").has("lyric")) {
            translatedLrc = json.getAsJsonObject("tlyric").get("lyric").getAsString();
        }

        String finalContent = "";
        if (bilingual && translatedLrc != null && !translatedLrc.trim().isEmpty()) {
            finalContent = mergeLyrics(originalLrc, translatedLrc, bilingualType, combineSymbol);
        } else {
            finalContent = originalLrc;
        }

        if (finalContent == null || finalContent.trim().isEmpty()) {
            callback.onError("未获取到有效歌词内容");
            return;
        }

        if ("SRT".equals(format)) {
            finalContent = convertLrcToSrt(finalContent);
        }

        String extension = format.toLowerCase();
        // 关键点：强制使用与歌曲完全一致的基础文件名
        File lrcFile = new File(saveDir, finalBaseName + "." + extension);
        
        Charset charset = "UTF-16 LE".equals(encoding) ? StandardCharsets.UTF_16LE : StandardCharsets.UTF_8;
        
        // 确保目录存在
        if (!saveDir.exists()) {
            saveDir.mkdirs();
        }

        try (FileOutputStream fos = new FileOutputStream(lrcFile)) {
            if (StandardCharsets.UTF_16LE.equals(charset)) {
                fos.write(new byte[]{(byte)0xFF, (byte)0xFE}); // BOM
            }
            fos.write(finalContent.getBytes(charset));
            fos.flush();
            fos.getFD().sync(); // 彻底防止 0B 文件
            callback.onSuccess(lrcFile);
        } catch (IOException e) {
            callback.onError("保存歌词文件失败: " + e.getMessage());
        }
    }

    private static String mergeLyrics(String main, String trans, String type, String symbol) {
        Map<String, String> mainMap = parseLrcToMap(main);
        Map<String, String> transMap = parseLrcToMap(trans);
        
        if (transMap.isEmpty()) return main;

        StringBuilder sb = new StringBuilder();
        List<String> times = new ArrayList<>(mainMap.keySet());
        Collections.sort(times);

        for (String time : times) {
            String mainLine = mainMap.get(time);
            String transLine = transMap.get(time);

            if (transLine == null || transLine.isEmpty()) {
                sb.append(time).append(mainLine).append("\n");
                continue;
            }

            if ("合并".equals(type)) {
                sb.append(time).append(mainLine).append(" ").append(symbol).append(" ").append(transLine).append("\n");
            } else if ("交错".equals(type)) {
                sb.append(time).append(mainLine).append("\n");
                sb.append(time).append(transLine).append("\n");
            } else if ("独立".equals(type)) {
                sb.append(time).append(mainLine).append("\n");
                sb.append(time).append(transLine).append("\n");
            }
        }
        return sb.toString();
    }

    private static Map<String, String> parseLrcToMap(String lrc) {
        Map<String, String> map = new TreeMap<>();
        if (lrc == null) return map;
        String[] lines = lrc.split("\\r?\\n");
        Pattern pattern = Pattern.compile("\\[(\\d{2}:\\d{2}\\.\\d{2,3})\\]");
        for (String line : lines) {
            Matcher matcher = pattern.matcher(line);
            while (matcher.find()) {
                String time = matcher.group(0);
                String content = line.replaceAll("\\[\\d{2}:\\d{2}\\.\\d{2,3}\\]", "").trim();
                if (!content.isEmpty()) {
                    map.put(time, content);
                }
            }
        }
        return map;
    }

    private static String convertLrcToSrt(String lrc) {
        StringBuilder srt = new StringBuilder();
        Map<String, String> map = parseLrcToMap(lrc);
        List<String> times = new ArrayList<>(map.keySet());
        Collections.sort(times);
        
        for (int i = 0; i < times.size(); i++) {
            String startLrc = times.get(i);
            String startSrt = lrcTimeToSrtTime(startLrc);
            String endSrt;
            if (i < times.size() - 1) {
                endSrt = lrcTimeToSrtTime(times.get(i+1));
            } else {
                endSrt = addSecondsToSrtTime(startSrt, 3);
            }
            
            srt.append(i + 1).append("\n");
            srt.append(startSrt).append(" --> ").append(endSrt).append("\n");
            srt.append(map.get(startLrc)).append("\n\n");
        }
        return srt.toString();
    }

    private static String lrcTimeToSrtTime(String lrcTime) {
        try {
            String t = lrcTime.substring(1, lrcTime.length() - 1);
            String[] parts = t.split(":");
            int min = Integer.parseInt(parts[0]);
            String[] sm = parts[1].split("\\.");
            int sec = Integer.parseInt(sm[0]);
            String ms = sm[1];
            if (ms.length() == 2) ms += "0";
            
            int h = min / 60;
            min = min % 60;
            return String.format("%02d:%02d:%02d,%s", h, min, sec, ms);
        } catch (Exception e) {
            return "00:00:00,000";
        }
    }

    private static String addSecondsToSrtTime(String srtTime, int seconds) {
        return srtTime.substring(0, 6) + String.format("%02d", Math.min(59, Integer.parseInt(srtTime.substring(6, 8)) + seconds)) + srtTime.substring(8);
    }

    private static class UriUtils {
        public static String encode(String input) {
            try {
                return java.net.URLEncoder.encode(input, "UTF-8");
            } catch (Exception e) {
                return input;
            }
        }
    }
}
