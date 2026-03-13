package com.ncm2flac.core;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import java.io.File;
import java.util.Map;

public class MetadataHandler {
    // 极简版，无任何API兼容问题，优先保证编译通过
    public static void writeMetadata(File audioFile, Map<String, Object> metadata) {
        try {
            if (!audioFile.exists() || audioFile.length() == 0) {
                return;
            }
            AudioFile af = AudioFileIO.read(audioFile);
            Tag tag = af.getTagOrCreateDefault();

            // 仅写入核心元数据，无任何额外依赖
            tag.setField(FieldKey.TITLE, metadata.getOrDefault("title", "未知歌曲").toString());
            tag.setField(FieldKey.ARTIST, metadata.getOrDefault("artist", "未知歌手").toString());
            tag.setField(FieldKey.ALBUM, metadata.getOrDefault("album", "未知专辑").toString());

            af.commit();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
