package com.zy.ppmusic.entity;

import java.io.Serializable;

public class MusicInfoEntity implements Serializable{
    private String mediaId;
    private String musicName;
    private String artist;
    private String queryPath;
    private long size;//文件大小
    private long duration;//时长

    public MusicInfoEntity() {
    }

    public MusicInfoEntity(String mediaId,String musicName, String artist, String queryPath, long size, long duration) {
        this.mediaId = mediaId;
        this.musicName = musicName;
        this.artist = artist;
        this.queryPath = queryPath;
        this.size = size;
        this.duration = duration;
    }

    public String getMusicName() {
        return musicName;
    }

    public void setMusicName(String musicName) {
        this.musicName = musicName;
    }

    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public String getQueryPath() {
        return queryPath;
    }

    public void setQueryPath(String queryPath) {
        this.queryPath = queryPath;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public String getMediaId() {
        return mediaId;
    }

    public void setMediaId(String mediaId) {
        this.mediaId = mediaId;
    }

    @Override
    public String toString() {
        return "MusicInfoEntity{" +
                "musicName='" + musicName + '\'' +
                ", artist='" + artist + '\'' +
                ", queryPath='" + queryPath + '\'' +
                ", size=" + size +
                ", length=" + duration +
                '}';
    }
}
