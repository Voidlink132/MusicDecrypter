package com.musicdecrypter.ui;

import android.app.DownloadManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.tabs.TabLayout;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.musicdecrypter.R;
import com.musicdecrypter.model.Song;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class DecryptFragment extends Fragment {

    private EditText etSearch;
    private ImageButton btnSearch;
    private TabLayout tabLayout;
    private RecyclerView recyclerView;
    private ProgressBar loadingBar;
    private TextView tvEmpty;

    private final List<Song> songList = new ArrayList<>();
    private SongAdapter adapter;
    private final OkHttpClient client = new OkHttpClient();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_decrypt_webview, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        etSearch = view.findViewById(R.id.et_search);
        btnSearch = view.findViewById(R.id.btn_do_search);
        tabLayout = view.findViewById(R.id.tab_platform);
        recyclerView = view.findViewById(R.id.rv_search_results);
        loadingBar = view.findViewById(R.id.loading_bar);
        tvEmpty = view.findViewById(R.id.tv_search_empty);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new SongAdapter(songList, this::showQualityDialog);
        recyclerView.setAdapter(adapter);

        btnSearch.setOnClickListener(v -> performSearch());
        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch();
                return true;
            }
            return false;
        });

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (!etSearch.getText().toString().trim().isEmpty()) {
                    performSearch();
                }
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private String getPlatformCookie(String platform) {
        if (getContext() == null) return "";
        SharedPreferences sp = getContext().getSharedPreferences("config", Context.MODE_PRIVATE);
        if ("netease".equals(platform)) return sp.getString("cookie_netease", "");
        if ("qq".equals(platform)) return sp.getString("cookie_qq", "");
        return "";
    }

    private Request.Builder addHeaders(Request.Builder builder, String platform) {
        String cookie = getPlatformCookie(platform);
        builder.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        if ("netease".equals(platform)) {
            builder.addHeader("Referer", "https://music.163.com/");
        } else if ("qq".equals(platform)) {
            builder.addHeader("Referer", "https://y.qq.com/");
        } else {
            builder.addHeader("Referer", "https://www.kugou.com/");
        }
        if (!cookie.isEmpty()) {
            builder.addHeader("Cookie", cookie);
        }
        return builder;
    }

    private void performSearch() {
        String keyword = etSearch.getText().toString().trim();
        if (keyword.isEmpty()) {
            Toast.makeText(getContext(), "请输入搜索关键词", Toast.LENGTH_SHORT).show();
            return;
        }

        loadingBar.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);
        songList.clear();
        adapter.notifyDataSetChanged();

        int tabIndex = tabLayout.getSelectedTabPosition();
        if (tabIndex == 0) searchNetease(keyword);
        else if (tabIndex == 1) searchQQ(keyword);
        else searchKugou(keyword);
    }

    private void searchNetease(String keyword) {
        String url = "https://music.163.com/api/search/get?s=" + Uri.encode(keyword) + "&type=1&limit=30";
        Request request = addHeaders(new Request.Builder().url(url), "netease").build();
        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) { showError("搜索失败: " + e.getMessage()); }
            @Override public void onResponse(Call call, Response response) throws IOException {
                try {
                    String body = response.body().string();
                    JsonObject json = JsonParser.parseString(body).getAsJsonObject();
                    List<Song> results = new ArrayList<>();
                    if (json.has("code") && json.get("code").getAsInt() == 200 && json.has("result")) {
                        JsonObject res = json.getAsJsonObject("result");
                        if (res.has("songs") && res.get("songs").isJsonArray()) {
                            JsonArray songs = res.getAsJsonArray("songs");
                            for (int i = 0; i < songs.size(); i++) {
                                JsonObject s = songs.get(i).getAsJsonObject();
                                String id = s.get("id").getAsString();
                                String name = s.get("name").getAsString();
                                String artist = "未知歌手";
                                if (s.has("artists") && s.get("artists").isJsonArray()) {
                                    JsonArray artists = s.getAsJsonArray("artists");
                                    if (artists.size() > 0) artist = artists.get(0).getAsJsonObject().get("name").getAsString();
                                }
                                String album = s.has("album") ? s.getAsJsonObject("album").get("name").getAsString() : "未知专辑";
                                results.add(new Song(id, name, artist, album, "netease"));
                            }
                        }
                    }
                    updateUI(results);
                } catch (Exception e) { showError("网易云解析失败"); }
            }
        });
    }

    private void searchQQ(String keyword) {
        String url = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp?p=1&n=20&w=" + Uri.encode(keyword) + "&format=json";
        Request request = addHeaders(new Request.Builder().url(url), "qq").build();
        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) { showError("QQ搜索请求失败"); }
            @Override public void onResponse(Call call, Response response) throws IOException {
                try {
                    String body = response.body().string().trim();
                    if (body.startsWith("callback(") || body.startsWith("jsonp")) {
                        body = body.substring(body.indexOf("(") + 1, body.lastIndexOf(")"));
                    }
                    JsonObject json = JsonParser.parseString(body).getAsJsonObject();
                    List<Song> results = new ArrayList<>();
                    if (json.has("data") && !json.get("data").isJsonNull()) {
                        JsonObject data = json.getAsJsonObject("data");
                        if (data.has("song")) {
                            JsonArray list = data.getAsJsonObject("song").getAsJsonArray("list");
                            for (int i = 0; i < list.size(); i++) {
                                JsonObject s = list.get(i).getAsJsonObject();
                                String mid = s.get("songmid").getAsString();
                                String name = s.get("songname").getAsString();
                                String artist = "未知歌手";
                                if (s.has("singer") && s.get("singer").isJsonArray()) {
                                    JsonArray singers = s.getAsJsonArray("singer");
                                    if (singers.size() > 0) artist = singers.get(0).getAsJsonObject().get("name").getAsString();
                                }
                                String album = s.has("albumname") ? s.get("albumname").getAsString() : "";
                                results.add(new Song(mid, name, artist, album, "qq"));
                            }
                        }
                    }
                    updateUI(results);
                } catch (Exception e) { showError("QQ解析异常"); }
            }
        });
    }

    private void searchKugou(String keyword) {
        String url = "http://songsearch.kugou.com/song_search_v2?keyword=" + Uri.encode(keyword) + "&page=1&pagesize=30&platform=WebFilter&format=json";
        Request request = addHeaders(new Request.Builder().url(url), "kugou").build();
        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) { showError("酷狗搜索失败"); }
            @Override public void onResponse(Call call, Response response) throws IOException {
                try {
                    String body = response.body().string();
                    JsonObject json = JsonParser.parseString(body).getAsJsonObject();
                    List<Song> results = new ArrayList<>();
                    if (json.has("data") && !json.get("data").isJsonNull()) {
                        JsonObject data = json.getAsJsonObject("data");
                        if (data.has("lists") && data.get("lists").isJsonArray()) {
                            JsonArray lists = data.getAsJsonArray("lists");
                            for (int i = 0; i < lists.size(); i++) {
                                JsonObject s = lists.get(i).getAsJsonObject();
                                String hash = s.get("FileHash").getAsString();
                                String name = s.get("SongName").getAsString();
                                String artist = s.get("SingerName").getAsString();
                                String album = s.get("AlbumName").getAsString();
                                String albumId = s.has("AlbumID") ? s.get("AlbumID").getAsString() : "0";
                                results.add(new Song(hash, name, artist, album + "__ID__" + albumId, "kugou"));
                            }
                        }
                    }
                    updateUI(results);
                } catch (Exception e) { showError("酷狗解析失败"); }
            }
        });
    }

    private void showQualityDialog(Song song) {
        if (!"netease".equals(song.getPlatform())) {
            downloadSong(song, "128000"); 
            return;
        }
        String[] brs = {"128000", "192000", "320000", "999000"};
        downloadSong(song, brs[0]);
    }

    private void downloadSong(Song song, String br) {
        if (song.getPlatform().equals("netease")) fetchNeteasePlayUrl(song, br);
        else if (song.getPlatform().equals("qq")) fetchQQPlayUrl(song);
        else fetchKugouPlayUrl(song);
    }

    private void fetchNeteasePlayUrl(Song song, String br) {
        String url = "https://music.163.com/api/song/enhance/player/url?id=" + song.getId() + "&ids=[" + song.getId() + "]&br=" + br;
        Request request = addHeaders(new Request.Builder().url(url), "netease").build();
        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) { showError("获取失败"); }
            @Override public void onResponse(Call call, Response response) throws IOException {
                try {
                    JsonObject json = JsonParser.parseString(response.body().string()).getAsJsonObject();
                    JsonArray data = json.getAsJsonArray("data");
                    if (data.size() > 0 && !data.get(0).getAsJsonObject().get("url").isJsonNull()) {
                        startDownloadTask(song.getName() + " - " + song.getArtist() + ".mp3", data.get(0).getAsJsonObject().get("url").getAsString());
                    } else showError("无权限下载");
                } catch (Exception e) { showError("链接失效"); }
            }
        });
    }

    private String getUinFromCookie(String cookie) {
        if (cookie == null || cookie.isEmpty()) return "0";
        Pattern pattern = Pattern.compile("uin=o?(\\d+)");
        Matcher matcher = pattern.matcher(cookie);
        if (matcher.find()) return matcher.group(1);
        return "0";
    }

    private void fetchQQPlayUrl(Song song) {
        String mid = song.getId();
        String guid = "12345678"; 
        String cookie = getPlatformCookie("qq");
        String uin = getUinFromCookie(cookie);
        String data = "{\"req\":{\"module\":\"vkey.GetVkeyServer\",\"method\":\"CgiGetVkey\",\"param\":{\"guid\":\"" + guid + "\",\"songmid\":[\"" + mid + "\"],\"songtype\":[0],\"uin\":\"" + uin + "\",\"loginflag\":1,\"platform\":\"20\"}},\"comm\":{\"uin\":\"" + uin + "\",\"format\":\"json\",\"ct\":24,\"cv\":0}}";
        String url = "https://u.y.qq.com/cgi-bin/musicu.fcg?data=" + Uri.encode(data);
        Request request = addHeaders(new Request.Builder().url(url), "qq").build();
        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) { showError("获取QQ链接失败"); }
            @Override public void onResponse(Call call, Response response) throws IOException {
                try {
                    JsonObject json = JsonParser.parseString(response.body().string()).getAsJsonObject();
                    JsonObject req = json.getAsJsonObject("req");
                    JsonObject dataObj = req.getAsJsonObject("data");
                    JsonArray midUrlInfo = dataObj.getAsJsonArray("midurlinfo");
                    String purl = midUrlInfo.get(0).getAsJsonObject().get("purl").getAsString();
                    if (purl != null && !purl.isEmpty()) {
                        startDownloadTask(song.getName() + " - " + song.getArtist() + ".mp3", "http://ws.stream.qqmusic.qq.com/" + purl);
                    } else showError("版权限制或建议登录");
                } catch (Exception e) { showError("QQ解析链接失败"); }
            }
        });
    }

    private void fetchKugouPlayUrl(Song song) {
        String hash = song.getId();
        String albumId = "0";
        if (song.getAlbum() != null && song.getAlbum().contains("__ID__")) {
            String[] parts = song.getAlbum().split("__ID__");
            if (parts.length > 1) albumId = parts[1];
        }
        String url = "https://www.kugou.com/yy/index.php?r=play/getdata&hash=" + hash + "&album_id=" + albumId;
        Request request = addHeaders(new Request.Builder().url(url), "kugou").build();
        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) { showError("获取酷狗链接失败"); }
            @Override public void onResponse(Call call, Response response) throws IOException {
                try {
                    JsonObject json = JsonParser.parseString(response.body().string()).getAsJsonObject();
                    JsonObject data = json.getAsJsonObject("data");
                    String dUrl = data.get("play_url").getAsString();
                    if (!dUrl.isEmpty()) {
                        startDownloadTask(song.getName() + " - " + song.getArtist() + ".mp3", dUrl);
                    } else showError("资源不存在或需要VIP");
                } catch (Exception e) { showError("酷狗解析链接失败"); }
            }
        });
    }

    private void startDownloadTask(String fileName, String url) {
        if (getActivity() == null || getContext() == null) return;
        getActivity().runOnUiThread(() -> {
            Toast.makeText(getContext(), "开始下载: " + fileName, Toast.LENGTH_SHORT).show();
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, "MusicDecrypter/" + fileName);
            DownloadManager dm = (DownloadManager) getContext().getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm != null) dm.enqueue(request);
        });
    }

    private void updateUI(List<Song> results) {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(() -> {
            loadingBar.setVisibility(View.GONE);
            songList.clear();
            songList.addAll(results);
            adapter.notifyDataSetChanged();
            tvEmpty.setVisibility(songList.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    private void showError(String msg) {
        if (getActivity() == null || getContext() == null) return;
        getActivity().runOnUiThread(() -> {
            loadingBar.setVisibility(View.GONE);
            Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
        });
    }

    static class SongAdapter extends RecyclerView.Adapter<SongAdapter.ViewHolder> {
        private final List<Song> data;
        private final OnDownloadClickListener listener;
        interface OnDownloadClickListener { void onClick(Song song); }
        SongAdapter(List<Song> data, OnDownloadClickListener listener) { this.data = data; this.listener = listener; }
        @NonNull @Override public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_music_file, parent, false));
        }
        @Override public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            if (data == null || position < 0 || position >= data.size()) return;
            Song song = data.get(position);
            String albumName = song.getAlbum() != null ? song.getAlbum().split("__ID__")[0] : "";
            holder.tvName.setText(song.getName());
            holder.tvInfo.setText(song.getArtist() + " - " + albumName);
            holder.btnDownload.setText("下载");
            holder.btnDownload.setOnClickListener(v -> listener.onClick(song));
            int color = "netease".equals(song.getPlatform()) ? 0xFFFF4081 : ("qq".equals(song.getPlatform()) ? 0xFF4CAF50 : 0xFF2196F3);
            holder.btnDownload.setTextColor(color);
        }
        @Override public int getItemCount() { return data == null ? 0 : data.size(); }
        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvInfo; android.widget.Button btnDownload;
            ViewHolder(View v) { super(v); tvName = v.findViewById(R.id.tv_file_name); tvInfo = v.findViewById(R.id.tv_file_path); btnDownload = v.findViewById(R.id.btn_decrypt); }
        }
    }
}
