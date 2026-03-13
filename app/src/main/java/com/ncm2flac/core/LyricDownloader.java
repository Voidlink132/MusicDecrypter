package com.ncm2flac.core;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;

public class LyricDownloader {
    // 网易云音乐歌词API（和LyricCrawler同源）
    private static final String LRC_API = "https://music.163.com/api/song/lyric?lv=1&kv=1&tv=-1&id=";
    private static final OkHttpClient client = new OkHttpClient();

    /**
     * 下载歌词并保存为同名LRC文件
     * @param songId 网易云歌曲ID
     * @param lrcFile 要保存的LRC文件对象
     * @return 下载成功返回true，失败返回false
     */
    public static boolean downloadAndSaveLrc(String songId, File lrcFile) {
        // 校验歌曲ID
        if (songId == null || songId.isEmpty()) {
            return false;
        }
        try {
            // 构建请求，模拟浏览器请求
            Request request = new Request.Builder()
                    .url(LRC_API + songId)
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .addHeader("Referer", "https://music.163.com/")
                    .build();

            // 执行请求
            try (Response response = client.newCall(request).execute()) {
                // 请求失败直接返回
                if (!response.isSuccessful() || response.body() == null) {
                    return false;
                }
                // 解析JSON结果
                String jsonStr = response.body().string();
                JSONObject json = new JSONObject(jsonStr);

                // 提取歌词原文
                if (!json.has("lrc")) return false;
                JSONObject lrcObj = json.getJSONObject("lrc");
                if (!lrcObj.has("lyric")) return false;
                String lyric = lrcObj.getString("lyric");

                // 保存为LRC文件
                FileOutputStream fos = new FileOutputStream(lrcFile);
                fos.write(lyric.getBytes());
                fos.flush();
                fos.close();

                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
