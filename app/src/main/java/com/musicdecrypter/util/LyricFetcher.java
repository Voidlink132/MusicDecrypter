package com.musicdecrypter.util;

import android.util.Log;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class LyricFetcher {
    private static final String TAG = "LyricFetcher";
    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();

    public interface LyricCallback {
        void onSuccess(File lyricFile);
        void onError(String msg);
    }

    public static void fetchLyric(String fileName, File saveDir, LyricCallback callback) {
        // Clean file name to get search keyword
        String keyword = fileName;
        if (keyword.contains(".")) {
            keyword = keyword.substring(0, keyword.lastIndexOf("."));
        }
        // Remove common patterns like [mqms2] or (Live)
        keyword = keyword.replaceAll("\\[.*?\\]", "").replaceAll("\\(.*?\\)", "").trim();

        searchNetease(keyword, saveDir, callback);
    }

    private static void searchNetease(String keyword, File saveDir, LyricCallback callback) {
        String searchUrl = "https://music.163.com/api/search/get?s=" + keyword + "&type=1&limit=1";
        Request request = new Request.Builder().url(searchUrl).build();

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
                    JsonObject result = json.getAsJsonObject("result");
                    if (result != null) {
                        JsonArray songs = result.getAsJsonArray("songs");
                        if (songs != null && songs.size() > 0) {
                            long id = songs.get(0).getAsJsonObject().get("id").getAsLong();
                            String songName = songs.get(0).getAsJsonObject().get("name").getAsString();
                            downloadLyric(id, songName, saveDir, callback);
                            return;
                        }
                    }
                    callback.onError("未找到匹配歌曲");
                } catch (Exception e) {
                    callback.onError("解析搜索结果失败");
                }
            }
        });
    }

    private static void downloadLyric(long id, String songName, File saveDir, LyricCallback callback) {
        String lyricUrl = "https://music.163.com/api/song/lyric?id=" + id + "&lv=1";
        Request request = new Request.Builder().url(lyricUrl).build();

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
                    JsonObject lrc = json.getAsJsonObject("lrc");
                    if (lrc != null && lrc.has("lyric")) {
                        String lyricContent = lrc.get("lyric").getAsString();
                        if (lyricContent != null && !lyricContent.isEmpty()) {
                            File lrcFile = new File(saveDir, songName + ".lrc");
                            try (FileOutputStream fos = new FileOutputStream(lrcFile)) {
                                fos.write(lyricContent.getBytes());
                            }
                            callback.onSuccess(lrcFile);
                            return;
                        }
                    }
                    callback.onError("该歌曲暂无歌词");
                } catch (Exception e) {
                    callback.onError("解析歌词失败");
                }
            }
        });
    }
}
