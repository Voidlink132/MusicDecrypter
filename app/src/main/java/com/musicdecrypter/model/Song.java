package com.musicdecrypter.model;

public class Song {
    private String id;
    private String name;
    private String artist;
    private String album;
    private String platform; // "netease", "qq", "kugou"
    private String downloadUrl;

    public Song(String id, String name, String artist, String album, String platform) {
        this.id = id;
        this.name = name;
        this.artist = artist;
        this.album = album;
        this.platform = platform;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getArtist() { return artist; }
    public String getAlbum() { return album; }
    public String getPlatform() { return platform; }
    public String getDownloadUrl() { return downloadUrl; }
    public void setDownloadUrl(String downloadUrl) { this.downloadUrl = downloadUrl; }
}
