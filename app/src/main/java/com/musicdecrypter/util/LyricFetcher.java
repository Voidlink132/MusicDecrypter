package com.musicdecrypter.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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

    public static void fetchLyric(Context context, String fileName, File saveDir, LyricCallback callback) {
        String keyword = fileName;
        if (keyword.contains(".")) {
            keyword = keyword.substring(0, keyword.lastIndexOf("."));
        }
        // 更精准的关键词提取：移除常见的各种后缀和无用信息
        keyword = keyword.replaceAll("\\[.*?\\]", "")
                .replaceAll("\\(.*?\\)", "")
                .replaceAll("(?i)copy", "")
                .replaceAll("(?i)lyrics", "")
                .trim();

        searchNetease(context, keyword, saveDir, callback);
    }

    private static void searchNetease(Context context, String keyword, File saveDir, LyricCallback callback) {
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
                                String songName = song.get("name").getAsString();
                                downloadLyric(context, id, songName, saveDir, callback);
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

    private static void downloadLyric(Context context, long id, String songName, File saveDir, LyricCallback callback) {
        // NetEase API: lv=1 (original), tv=-1 (translation), kv=1 (metadata)
        String lyricUrl = "https://music.163.com/api/song/lyric?id=" + id + "&lv=1&kv=1&tv=-1";
        Request request = new Request.Builder()
                .url(lyricUrl)
                .addHeader("User-Agent", "Mozilla/5.0")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError("获取歌词失败: " + e.getMessage());
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
                    processLyric(context, json, songName, saveDir, callback);
                } catch (Exception e) {
                    callback.onError("解析歌词失败: " + e.getMessage());
                }
            }
        });
    }

    private static void processLyric(Context context, JsonObject json, String songName, File saveDir, LyricCallback callback) {
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
        File lrcFile = new File(saveDir, songName + "." + extension);
        
        Charset charset = "UTF-16 LE".equals(encoding) ? StandardCharsets.UTF_16LE : StandardCharsets.UTF_8;
        
        try (FileOutputStream fos = new FileOutputStream(lrcFile)) {
            if (StandardCharsets.UTF_16LE.equals(charset)) {
                fos.write(new byte[]{(byte)0xFF, (byte)0xFE}); // BOM
            }
            fos.write(finalContent.getBytes(charset));
            callback.onSuccess(lrcFile);
        } catch (IOException e) {
            callback.onError("保存文件失败: " + e.getMessage());
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
                // 通常独立指分文件，这里按顺序排列
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
