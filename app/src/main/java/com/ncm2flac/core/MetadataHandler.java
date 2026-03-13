package com.ncm2flac.core;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import java.io.File;
import java.util.Map;

public class MetadataHandler {

    // 极简版，无任何API兼容问题，优先保证编译通过
    public static void writeMetadata(File audioFile, Map<String, Object> metadata, byte[] coverImage) throws Exception {
        AudioFile audioFileIO = AudioFileIO.read(audioFile);
        Tag tag = audioFileIO.getTagOrCreateDefault();

        // 仅保留核心元数据，无任何额外依赖
        tag.setField(FieldKey.TITLE, (String) metadata.getOrDefault("title", "未知歌曲"));
        tag.setField(FieldKey.ARTIST, (String) metadata.getOrDefault("artist", "未知歌手"));
        tag.setField(FieldKey.ALBUM, (String) metadata.getOrDefault("album", "未知专辑"));

        // 提交修改
        audioFileIO.commit();
    }
}
