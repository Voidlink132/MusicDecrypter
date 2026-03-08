package com.ncmconverter.app;

import android.content.Context;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class LrcTool {
    // 静态方法：下载LRC歌词，直接调用即可
    public static void download(final String songName, final File outDir, final Context context) {
        // 后台下载，不卡UI
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!outDir.exists()) outDir.mkdirs();
                    File lrcFile = new File(outDir, songName + ".lrc");

                    // 1. 调用网易云API，通过歌名获取歌曲ID
                    String searchUrl = "https://music.163.com/api/search/pc?s=" + songName + "&type=1&limit=1";
                    URL url = new URL(searchUrl);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                    InputStreamReader reader = new InputStreamReader(conn.getInputStream());
                    char[] buf = new char[10240];
                    int n = reader.read(buf);
                    String jsonStr = new String(buf, 0, n);
                    reader.close();
                    conn.disconnect();

                    // 解析歌曲ID
                    JSONObject root = new JSONObject(jsonStr);
                    if (root.getInt("code") != 200) return;
                    long songId = root.getJSONObject("result")
                            .getJSONArray("songs")
                            .getJSONObject(0)
                            .getLong("id");

                    // 2. 通过歌曲ID获取LRC歌词
                    String lrcUrl = "https://music.163.com/api/song/lyric?id=" + songId + "&lv=-1";
                    URL lrcApiUrl = new URL(lrcUrl);
                    HttpURLConnection lrcConn = (HttpURLConnection) lrcApiUrl.openConnection();
                    lrcConn.setRequestMethod("GET");
                    InputStreamReader lrcReader = new InputStreamReader(lrcConn.getInputStream());
                    char[] lrcBuf = new char[20480];
                    int ln = lrcReader.read(lrcBuf);
                    String lrcJsonStr = new String(lrcBuf, 0, ln);
                    lrcReader.close();
                    lrcConn.disconnect();

                    // 解析歌词并写入文件
                    JSONObject lrcRoot = new JSONObject(lrcJsonStr);
                    String lrcText = lrcRoot.getJSONObject("lrc").getString("lyric");
                    FileOutputStream fos = new FileOutputStream(lrcFile);
                    fos.write(lrcText.getBytes());
                    fos.flush();
                    fos.close();

                    // 主线程提示
                    ((MainActivity)context).runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(context, "LRC歌词下载成功", Toast.LENGTH_SHORT).show();
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    // 失败提示
                    ((MainActivity)context).runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(context, "未找到对应LRC歌词", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }
}
