package com.zy.ppmusic.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.util.ArrayMap;
import android.util.Log;

import com.zy.ppmusic.entity.MusicInfoEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 数据转换
 */
public class DataTransform {
    private static final String TAG = "DataTransform";
    private volatile List<MusicInfoEntity> musicInfoEntities;//可存放本地的数据
    private volatile List<MediaSessionCompat.QueueItem> queueItemList;
    private volatile List<MediaBrowserCompat.MediaItem> mediaItemList;
    private volatile ArrayMap<String, MediaMetadataCompat> mapMetadataArray;
    private volatile ArrayMap<Integer, String> indexMediaArray;
    private volatile ArrayList<String> pathList;
    private List<String> mediaIdList;

    private static class Inner {
        private static DataTransform transform = new DataTransform();
    }

    private DataTransform() {
        pathList = new ArrayList<>();
        musicInfoEntities = new ArrayList<>();
        mapMetadataArray = new ArrayMap<>();
        indexMediaArray = new ArrayMap<>();
        queueItemList = new ArrayList<>();
        mediaItemList = new ArrayList<>();
        mediaIdList = new ArrayList<>();
    }

    public static DataTransform getInstance() {
        return Inner.transform;
    }

    /**
     * 从本地扫描得到数据转换
     */
    public void transFormData(Context context, ArrayList<String> pathList) {
        clearData();
        if (this.pathList.size() > 0) {
            this.pathList.clear();
            this.pathList.addAll(pathList);
        } else {
            this.pathList.addAll(pathList);
        }
        queryResolver(context);
    }

    private void clearData() {
        Log.d(TAG, "clearData() called");
        musicInfoEntities.clear();
        mapMetadataArray.clear();
        indexMediaArray.clear();
        queueItemList.clear();
        mediaIdList.clear();
        mediaItemList.clear();
    }

    private void queryResolver(Context context) {
        ContentResolver contentResolver = context.getContentResolver();
        Uri oldUri = null;
        int index = 0;
        for (String itemPath : pathList) {
            indexMediaArray.put(index, String.valueOf(itemPath.hashCode()));
            mediaIdList.add(String.valueOf(itemPath.hashCode()));
            index++;
            //根据音频地址获取uri，区分为内部存储和外部存储
            Uri audioUri = MediaStore.Audio.Media.getContentUriForPath(itemPath);
            Cursor query = contentResolver.query(audioUri, null,
                    null, null, null);
            MediaMetadataCompat.Builder builder = null;
            if (query != null) {
                //判断如果是上次扫描的uri则跳过，系统分为内部存储uri的音频和外部存储的uri
                if (oldUri != null && oldUri.equals(audioUri)) {
                    query.close();
                    continue;
                } else {
                    //遍历得到内部或者外部存储的所有媒体文件的信息
                    while (query.moveToNext()) {
                        String name = query.getString(query.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME));
                        String title = query.getString(query.getColumnIndex(MediaStore.Audio.Media.TITLE));
                        String artist = query.getString(query.getColumnIndex(MediaStore.Audio.Media.ARTIST));
                        long duration = query.getLong(query.getColumnIndex(MediaStore.Audio.Media.DURATION));
                        String size = query.getString(query.getColumnIndex(MediaStore.Audio.Media.SIZE));
                        String queryPath = query.getString(query.getColumnIndex(MediaStore.Audio.Media.DATA));
                        builder = new MediaMetadataCompat.Builder();
                        //唯一id
                        builder.putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, String.valueOf(queryPath.hashCode()));
                        //文件路径
                        builder.putString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI, queryPath);
                        //显示名称
                        builder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, title);
                        //作者
                        builder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, artist);
                        //作者
                        builder.putString(MediaMetadataCompat.METADATA_KEY_AUTHOR, artist);
                        //时长
                        builder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration);

                        MediaMetadataCompat metadataCompatItem = builder.build();

                        mapMetadataArray.put(String.valueOf(queryPath.hashCode()), metadataCompatItem);

                        MediaSessionCompat.QueueItem queueItem = new MediaSessionCompat.QueueItem(
                                metadataCompatItem.getDescription(), queryPath.hashCode());
                        queueItemList.add(queueItem);

                        MediaBrowserCompat.MediaItem mediaItem = new MediaBrowserCompat.MediaItem(
                                metadataCompatItem.getDescription(), MediaBrowserCompat.MediaItem.FLAG_PLAYABLE);
                        mediaItemList.add(mediaItem);

                        MusicInfoEntity infoEntity = new MusicInfoEntity(String.valueOf(queryPath.hashCode()),
                                title, artist, queryPath, Long.parseLong(size), duration);
                        musicInfoEntities.add(infoEntity);
                    }
                    query.close();
                }
            } else {//如果本地媒体库未发现文件则创建默认的
                builder = new MediaMetadataCompat.Builder();
                String musicName = getMusicName(itemPath);
                builder.putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, String.valueOf(itemPath.hashCode()));
                builder.putString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI, itemPath);
                builder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, musicName);

                MediaMetadataCompat metadataCompatItem = builder.build();

                mapMetadataArray.put(String.valueOf(itemPath.hashCode()), metadataCompatItem);

                MediaSessionCompat.QueueItem queueItem = new MediaSessionCompat.QueueItem(
                        metadataCompatItem.getDescription(), itemPath.hashCode());
                queueItemList.add(queueItem);

                MediaBrowserCompat.MediaItem mediaItem = new MediaBrowserCompat.MediaItem(
                        metadataCompatItem.getDescription(), MediaBrowserCompat.MediaItem.FLAG_PLAYABLE);
                mediaItemList.add(mediaItem);

                MusicInfoEntity infoEntity = new MusicInfoEntity(String.valueOf(itemPath.hashCode()),
                        musicName, "未知", itemPath, 0, 0);
                musicInfoEntities.add(infoEntity);


            }
            oldUri = audioUri;
        }

        Log.d(TAG, "queryResolver() called with: context = [" + context + "]");
    }

    /**
     * 从本地缓存得到的数据转换
     *
     * @param localList
     */
    public void transFormData(List<MusicInfoEntity> localList) {
        this.musicInfoEntities = localList;
        MediaMetadataCompat metadataCompatItem;
        for (int i = 0; i < musicInfoEntities.size(); i++) {
            MusicInfoEntity itemEntity = musicInfoEntities.get(i);

            indexMediaArray.put(i, itemEntity.getMediaId());
            mediaIdList.add(itemEntity.getMediaId());

            MediaMetadataCompat.Builder itemBuilder = new MediaMetadataCompat.Builder();
            //唯一id
            itemBuilder.putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, itemEntity.getMediaId());
            //文件路径
            itemBuilder.putString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI, itemEntity.getQueryPath());
            //显示名称
            itemBuilder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, itemEntity.getMusicName());
            //作者
            itemBuilder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, itemEntity.getArtist());
            //作者
            itemBuilder.putString(MediaMetadataCompat.METADATA_KEY_AUTHOR, itemEntity.getArtist());

            itemBuilder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, itemEntity.getDuration());

            metadataCompatItem = itemBuilder.build();

            mapMetadataArray.put(itemEntity.getMediaId(), metadataCompatItem);

            MediaSessionCompat.QueueItem queueItem = new MediaSessionCompat.QueueItem(
                    metadataCompatItem.getDescription(), Long.parseLong(itemEntity.getMediaId()));
            queueItemList.add(queueItem);

            MediaBrowserCompat.MediaItem mediaItem = new MediaBrowserCompat.MediaItem(
                    metadataCompatItem.getDescription(), MediaBrowserCompat.MediaItem.FLAG_PLAYABLE);
            mediaItemList.add(mediaItem);
        }
    }

    private String getMusicName(String path) {
        if (path != null) {
            return path.substring((path.lastIndexOf("/") + 1), path.lastIndexOf("."));
        } else {
            return null;
        }
    }

    public List<MusicInfoEntity> getMusicInfoEntities() {
        return musicInfoEntities;
    }

    public List<MediaSessionCompat.QueueItem> getQueueItemList() {
        return queueItemList;
    }

    public List<MediaBrowserCompat.MediaItem> getMediaItemList() {
        return mediaItemList;
    }

    public List<String> getPathList() {
        return pathList;
    }

    public List<String> getMediaIdList() {
        return mediaIdList;
    }

    public int getMediaIndex(String mediaId) {
        if (mediaIdList.contains(mediaId)) {
            return mediaIdList.indexOf(mediaId);
        }
        return 0;
    }

    public MediaMetadataCompat getMetadataItem(String mediaId) {
        System.out.println("mediaId=" + mediaId + ",data=" + mapMetadataArray.toString());
        if (mapMetadataArray.containsKey(mediaId)) {
            return mapMetadataArray.get(mediaId);
        }
        return null;
    }

    public Map<String, MediaMetadataCompat> getMetadataCompatList() {
        return mapMetadataArray;
    }
}
