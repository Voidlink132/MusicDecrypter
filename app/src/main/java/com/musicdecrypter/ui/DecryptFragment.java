package com.musicdecrypter.ui;

import android.app.DownloadManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
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
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.musicdecrypter.R;
import com.musicdecrypter.model.Song;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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

    private List<Song> songList = new ArrayList<>();
    private SongAdapter adapter;
    private final OkHttpClient client = new OkHttpClient();

    private static final String PLATFORM_NETEASE = "网易云";
    private static final String PLATFORM_QQ = "QQ";
    private static final String PLATFORM_KUGOU = "酷狗";

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

        int tabIndex = tabLayout.getSelectedTabPosition();
        String platform = PLATFORM_NETEASE;
        if (tabIndex == 1) platform = PLATFORM_QQ;
        else if (tabIndex == 2) platform = PLATFORM_KUGOU;

        loadingBar.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);
        songList.clear();
        adapter.notifyDataSetChanged();

        if (PLATFORM_NETEASE.equals(platform)) {
            searchNetease(keyword);
        } else if (PLATFORM_QQ.equals(platform)) {
            searchQQ(keyword);
        } else {
            searchKugou(keyword);
        }
    }

    private void searchNetease(String keyword) {
        String url = "https://music.163.com/api/search/get?s=" + Uri.encode(keyword) + "&type=1&limit=30";
        Request.Builder builder = new Request.Builder().url(url);
        Request request = addHeaders(builder, "netease").build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) { showError("搜索失败: " + e.getMessage()); }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    String body = response.body().string();
                    JsonObject json = JsonParser.parseString(body).getAsJsonObject();
                    if (json.has("code") && json.get("code").getAsInt() == 200) {
                        if (json.has("result") && !json.get("result").isJsonNull()) {
                            JsonObject result = json.getAsJsonObject("result");
                            if (result.has("songs") && result.get("songs").isJsonArray()) {
                                JsonArray songs = result.getAsJsonArray("songs");
                                for (int i = 0; i < songs.size(); i++) {
                                    JsonObject s = songs.get(i).getAsJsonObject();
                                    String id = s.get("id").getAsString();
                                    String name = s.get("name").getAsString();
                                    
                                    String artist = "未知歌手";
                                    if (s.has("artists") && s.get("artists").isJsonArray()) {
                                        JsonArray artists = s.getAsJsonArray("artists");
                                        if (artists.size() > 0) artist = artists.get(0).getAsJsonObject().get("name").getAsString();
                                    }
                                    
                                    String album = "未知专辑";
                                    if (s.has("album") && !s.get("album").isJsonNull()) {
                                        album = s.getAsJsonObject("album").get("name").getAsString();
                                    }
                                    
                                    songList.add(new Song(id, name, artist, album, "netease"));
                                }
                            }
                        }
                    }
                    updateUI();
                } catch (Exception e) { 
                    Log.e("NeteaseSearch", "Parse error", e);
                    showError("解析失败: " + e.getMessage()); 
                }
            }
        });
    }

    private void searchQQ(String keyword) {
        String url = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp?ct=24&qqmusic_ver=1298&new_json=1&remoteplace=txt.yqq.song&t=0&aggr=1&cr=1&catZhida=1&lossless=0&flag_qc=0&p=1&n=20&w=" + Uri.encode(keyword) + "&format=json";
        Request.Builder builder = new Request.Builder().url(url);
        Request request = addHeaders(builder, "qq").build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) { showError("搜索失败: " + e.getMessage()); }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    String body = response.body().string();
                    if (body.contains("callback(")) {
                        body = body.substring(body.indexOf("(") + 1, body.lastIndexOf(")"));
                    }
                    JsonObject json = JsonParser.parseString(body).getAsJsonObject();
                    if (json.has("data") && !json.get("data").isJsonNull()) {
                        JsonObject data = json.getAsJsonObject("data");
                        if (data.has("song") && !data.get("song").isJsonNull()) {
                            JsonArray list = data.getAsJsonObject("song").getAsJsonArray("list");
                            for (int i = 0; i < list.size(); i++) {
                                JsonObject s = list.get(i).getAsJsonObject();
                                String mid = s.get("mid").getAsString();
                                String name = s.get("name").getAsString();
                                
                                String artist = "未知歌手";
                                if (s.has("singer") && s.get("singer").isJsonArray()) {
                                    JsonArray singers = s.getAsJsonArray("singer");
                                    if (singers.size() > 0) artist = singers.get(0).getAsJsonObject().get("name").getAsString();
                                }
                                
                                String album = "未知专辑";
                                if (s.has("album") && !s.get("album").isJsonNull()) {
                                    album = s.getAsJsonObject("album").get("name").getAsString();
                                }
                                
                                songList.add(new Song(mid, name, artist, album, "qq"));
                            }
                        }
                    }
                    updateUI();
                } catch (Exception e) { 
                    Log.e("QQSearch", "Parse error", e);
                    showError("解析失败: " + e.getMessage()); 
                }
            }
        });
    }

    private void searchKugou(String keyword) {
        String url = "http://mobilecdn.kugou.com/api/v3/search/song?keyword=" + Uri.encode(keyword) + "&page=1&pagesize=30&format=json";
        Request request = new Request.Builder().url(url).build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) { showError("搜索失败"); }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    String body = response.body().string();
                    JsonObject json = JsonParser.parseString(body).getAsJsonObject();
                    if (json.has("data") && !json.get("data").isJsonNull()) {
                        JsonObject data = json.getAsJsonObject("data");
                        if (data.has("info") && data.get("info").isJsonArray()) {
                            JsonArray info = data.getAsJsonArray("info");
                            for (int i = 0; i < info.size(); i++) {
                                JsonObject s = info.get(i).getAsJsonObject();
                                String hash = s.get("hash").getAsString();
                                String name = s.get("songname").getAsString();
                                String artist = s.has("singername") ? s.get("singername").getAsString() : "未知歌手";
                                String album = s.has("album_name") ? s.get("album_name").getAsString() : "未知专辑";
                                songList.add(new Song(hash, name, artist, album, "kugou"));
                            }
                        }
                    }
                    updateUI();
                } catch (Exception e) { 
                    Log.e("KugouSearch", "Parse error", e);
                    showError("解析失败: " + e.getMessage()); 
                }
            }
        });
    }

    private void showQualityDialog(Song song) {
        if (!"netease".equals(song.getPlatform())) {
            downloadSong(song, "128000"); 
            return;
        }

        String[] qualities = {"标准 (128kbps)", "较高 (192kbps)", "极高 (320kbps)", "无损 (FLAC)"};
        String[] brs = {"128000", "192000", "320000", "999000"};

        new AlertDialog.Builder(requireContext())
                .setTitle("选择下载音质")
                .setItems(qualities, (dialog, which) -> {
                    downloadSong(song, brs[which]);
                })
                .show();
    }

    private void downloadSong(Song song, String br) {
        if (song.getPlatform().equals("netease")) {
            fetchNeteasePlayUrl(song, br);
        } else if (song.getPlatform().equals("qq")) {
            fetchQQPlayUrl(song);
        } else {
            fetchKugouPlayUrl(song);
        }
    }

    private void fetchNeteasePlayUrl(Song song, String br) {
        String url = "https://music.163.com/api/song/enhance/player/url?id=" + song.getId() + "&ids=[" + song.getId() + "]&br=" + br;
        Request.Builder builder = new Request.Builder().url(url);
        Request request = addHeaders(builder, "netease").build();
        
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) { showError("网络错误: " + e.getMessage()); }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    String body = response.body().string();
                    JsonObject json = JsonParser.parseString(body).getAsJsonObject();
                    if (json.has("data") && json.get("data").isJsonArray()) {
                        JsonArray data = json.getAsJsonArray("data");
                        if (data.size() > 0) {
                            JsonObject obj = data.get(0).getAsJsonObject();
                            if (obj.has("url") && !obj.get("url").isJsonNull()) {
                                String downloadUrl = obj.get("url").getAsString();
                                String ext = br.equals("999000") ? ".flac" : ".mp3";
                                startDownloadTask(song.getName() + " - " + song.getArtist() + ext, downloadUrl);
                                return;
                            }
                        }
                    }
                    showError("获取链接失败 (Cookie已失效或无版权)");
                } catch (Exception e) { 
                    Log.e("NeteaseDownload", "Parse error", e);
                    showError("解析下载链接失败"); 
                }
            }
        });
    }

    private void fetchQQPlayUrl(Song song) {
        String mid = song.getId();
        String data = "{\"req\":{\"module\":\"vkey.GetVkeyServer\",\"method\":\"CgiGetVkey\",\"param\":{\"guid\":\"10000\",\"songmid\":[\"" + mid + "\"],\"songtype\":[0],\"uin\":\"0\",\"loginflag\":1,\"platform\":\"20\"}},\"comm\":{\"uin\":0,\"format\":\"json\",\"ct\":24,\"cv\":0}}";
        String url = "https://u.y.qq.com/cgi-bin/musicu.fcg?data=" + Uri.encode(data);
        
        Request.Builder builder = new Request.Builder().url(url);
        Request request = addHeaders(builder, "qq").build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) { showError("获取链接失败"); }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    String body = response.body().string();
                    JsonObject json = JsonParser.parseString(body).getAsJsonObject();
                    if (json.has("req") && json.getAsJsonObject("req").has("data")) {
                        JsonObject vkeyData = json.getAsJsonObject("req").getAsJsonObject("data");
                        if (vkeyData.has("midurlinfo") && vkeyData.get("midurlinfo").isJsonArray()) {
                            JsonArray midUrlInfo = vkeyData.getAsJsonArray("midurlinfo");
                            if (midUrlInfo.size() > 0) {
                                String purl = midUrlInfo.get(0).getAsJsonObject().get("purl").getAsString();
                                if (!purl.isEmpty()) {
                                    String downloadUrl = "http://ws.stream.qqmusic.qq.com/" + purl;
                                    startDownloadTask(song.getName() + " - " + song.getArtist() + ".mp3", downloadUrl);
                                    return;
                                }
                            }
                        }
                    }
                    showError("版权受限或链接获取失败");
                } catch (Exception e) { showError("获取链接失败"); }
            }
        });
    }

    private void fetchKugouPlayUrl(Song song) {
        String url = "http://m.kugou.com/app/i/getSongInfo.php?cmd=playInfo&hash=" + song.getId();
        Request request = new Request.Builder().url(url).build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) { showError("获取链接失败"); }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    String body = response.body().string();
                    JsonObject json = JsonParser.parseString(body).getAsJsonObject();
                    if (json.has("url") && !json.get("url").isJsonNull()) {
                        String playUrl = json.get("url").getAsString();
                        if (!playUrl.isEmpty()) {
                            startDownloadTask(song.getName() + " - " + song.getArtist() + ".mp3", playUrl);
                            return;
                        }
                    }
                    showError("获取链接失败");
                } catch (Exception e) { showError("解析链接失败"); }
            }
        });
    }

    private void startDownloadTask(String fileName, String url) {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(() -> Toast.makeText(getContext(), "开始下载: " + fileName, Toast.LENGTH_SHORT).show());
        
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        request.setTitle("正在下载: " + fileName);
        request.setDescription("MusicDecrypter 自动下载");
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, "MusicDecrypter/" + fileName);
        
        String platform = url.contains("qqmusic") ? "qq" : (url.contains("163.com") ? "netease" : "");
        if (!platform.isEmpty()) {
            String cookie = getPlatformCookie(platform);
            if (!cookie.isEmpty()) {
                request.addRequestHeader("Cookie", cookie);
            }
        }
        request.addRequestHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");

        DownloadManager dm = (DownloadManager) getContext().getSystemService(Context.DOWNLOAD_SERVICE);
        if (dm != null) dm.enqueue(request);
    }

    private void updateUI() {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(() -> {
            loadingBar.setVisibility(View.GONE);
            adapter.notifyDataSetChanged();
            tvEmpty.setVisibility(songList.isEmpty() ? View.VISIBLE : View.GONE);
            if (songList.isEmpty()) tvEmpty.setText("未找到相关歌曲");
        });
    }

    private void showError(String msg) {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(() -> {
            loadingBar.setVisibility(View.GONE);
            Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
        });
    }

    static class SongAdapter extends RecyclerView.Adapter<SongAdapter.ViewHolder> {
        private final List<Song> data;
        private final OnDownloadClickListener listener;

        interface OnDownloadClickListener { void onClick(Song song); }

        SongAdapter(List<Song> data, OnDownloadClickListener listener) {
            this.data = data;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_music_file, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Song song = data.get(position);
            holder.tvName.setText(song.getName());
            holder.tvInfo.setText(song.getArtist() + " - " + song.getAlbum());
            holder.btnDownload.setText("下载");
            holder.btnDownload.setOnClickListener(v -> listener.onClick(song));
            
            int color = 0xFF2196F3;
            if ("netease".equals(song.getPlatform())) color = 0xFFFF4081;
            else if ("qq".equals(song.getPlatform())) color = 0xFF4CAF50;
            holder.btnDownload.setTextColor(color);
        }

        @Override
        public int getItemCount() { return data.size(); }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvInfo;
            android.widget.Button btnDownload;

            ViewHolder(View v) {
                super(v);
                tvName = v.findViewById(R.id.tv_file_name);
                tvInfo = v.findViewById(R.id.tv_file_path);
                btnDownload = v.findViewById(R.id.btn_decrypt);
            }
        }
    }
}
