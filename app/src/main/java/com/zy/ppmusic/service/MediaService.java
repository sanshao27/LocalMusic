package com.zy.ppmusic.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaBrowserServiceCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaButtonReceiver;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.view.KeyEvent;

import com.zy.ppmusic.callback.AudioNoisyCallBack;
import com.zy.ppmusic.callback.TimeTikCallBack;
import com.zy.ppmusic.data.db.DBManager;
import com.zy.ppmusic.entity.MusicDbEntity;
import com.zy.ppmusic.mvp.view.MediaActivity;
import com.zy.ppmusic.receiver.AudioBecomingNoisyReceiver;
import com.zy.ppmusic.receiver.LoopReceiver;
import com.zy.ppmusic.utils.DataTransform;
import com.zy.ppmusic.utils.FileUtils;
import com.zy.ppmusic.utils.NotificationUtils;
import com.zy.ppmusic.utils.PlayBack;
import com.zy.ppmusic.utils.PrintOut;
import com.zy.ppmusic.utils.StringUtils;
import com.zy.ppmusic.utils.TimerUtils;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static com.zy.ppmusic.utils.NotificationUtils.NOTIFY_ID;

/**
 * @author ZhiTouPC
 */
public class MediaService extends MediaBrowserServiceCompat {
    /**
     * 播放指定id
     */
    public static final String ACTION_PLAY_WITH_ID = "PLAY_WITH_ID";
    /*-------------------play action--------------------------*/
    /**
     * 缓冲指定id
     */
    public static final String ACTION_PREPARED_WITH_ID = "PREPARED_WITH_ID";
    /**
     * 初始化播放器
     */
    public static final String ACTION_PLAY_INIT = "PLAY_INIT";
    /**
     * 快进
     */
    public static final String ACTION_SEEK_TO = "SEEK_TO";
    /**
     * 获取参数
     */
    public static final String ACTION_PARAM = "ACTION_PARAM";

    /*-------------------play action end--------------------------*/
    /**
     * 快进
     */
    public static final String SEEK_TO_POSITION_PARAM = "SEEK_TO_POSITION_PARAM";

    /*-------------------command action--------------------------*/
    /**
     * 开启循环
     */
    public static final String COMMAND_START_LOOP = "COMMAND_START_LOOP";

    /**
     * 关闭循环
     */
    public static final String COMMAND_STOP_LOOP = "COMMAND_STOP_LOOP";
    /**
     * 获取播放位置
     */
    public static final String COMMAND_POSITION = "COMMAND_POSITION";
    /**
     * 获取播放位置 resultCode
     */
    public static final int COMMAND_POSITION_CODE = 0x001;
    /**
     * 更新播放列表
     */
    public static final String COMMAND_UPDATE_QUEUE = "COMMAND_UPDATE_QUEUE";
    /**
     * 更新播放列表resultCode
     */
    public static final int COMMAND_UPDATE_QUEUE_CODE = 0x002;
    /*-------------------command action end--------------------------*/
    /*-------------------custom action start--------------------------*/
    /**
     * 播放列表为空，本地未搜索到曲目
     */
    public static final String ERROR_PLAY_QUEUE_EVENT = "ERROR_PLAY_QUEUE_EVENT";
    /**
     * 加载中...
     */
    public static final String LOADING_QUEUE_EVENT = "LOADING_QUEUE_EVENT";
    /**
     * 加载完成...
     */
    public static final String LOAD_COMPLETE_EVENT = "LOAD_COMPLETE_EVENT";
    /**
     * 加载本地缓存位置...
     */
    public static final String LOCAL_CACHE_POSITION_EVENT = "LOCAL_CACHE_POSITION_EVENT";
    /**
     * 更新播放位置...
     */
    public static final String UPDATE_POSITION_EVENT = "UPDATE_POSITION_EVENT";

    /**
     * 开始倒计时
     */
    public static final String ACTION_COUNT_DOWN_TIME = "ACTION_COUNT_DOWN_TIME";
    /**
     * 倒计时结束
     */
    public static final String ACTION_COUNT_DOWN_END = "ACTION_COUNT_DOWN_END";
    /**
     * 停止倒计时
     */
    public static final String ACTION_STOP_COUNT_DOWN = "ACTION_STOP_COUNT_DOWN";

    /**
     * -------------------custom action end--------------------------
     */
    private static final String TAG = "MediaService";
    /**
     * 保持后台运行且与前台进行通信
     */
    private MediaSessionCompat mMediaSessionCompat;
    /**
     * 播放器controller
     */
    private PlayBack mPlayBack;
    /**
     * 媒体id列表
     */
    private List<String> mPlayQueueMediaId;
    private List<MediaBrowserCompat.MediaItem> mMediaItemList = new ArrayList<>();
    private List<MediaSessionCompat.QueueItem> mQueueItemList = new ArrayList<>();
    /**
     * 当前播放的媒体
     */
    private MediaSessionCompat.QueueItem mCurrentMedia;
    /**
     * 音频监听
     */
    private AudioBecomingNoisyReceiver mAudioReceiver;
    private boolean mServiceStarted;
    /**
     * 倒计时
     */
    private TimerUtils mCountDownTimer;
    /**
     * 线程池
     */
    private ExecutorService executorService;
    /**
     * 更新当前播放的媒体信息
     */
    private UpdateRunnable mUpdateRunnable;
    /**
     * 更新播放列表
     */
    private UpdateQueueRunnable mUpdateQueueRunnable;
    /**
     * 错误曲目数量
     * 当无法播放曲目数量和列表数量相同时销毁播放器避免循环
     */
    private int mErrorTimes;
    private IntentFilter filter = new IntentFilter(LoopService.ACTION);
    private LoopReceiver receiver;

    private AudioNoisyCallBack audioCallBack = new AudioNoisyCallBack() {
        @Override
        public void comingNoisy() {
            handlePlayOrPauseRequest();
        }
    };
    /**
     * 倒计时监听
     */
    private TimeTikCallBack timeTikCallBack = new TimeTikCallBack() {
        @Override
        public void onTik(long mis) {
            //如果页面绑定时
            if (mMediaSessionCompat.getController() != null) {
                if (mis != 0) {
                    Bundle bundle = new Bundle();
                    bundle.putLong(ACTION_COUNT_DOWN_TIME, mis);
                    mMediaSessionCompat.sendSessionEvent(ACTION_COUNT_DOWN_TIME, bundle);
                } else {
                    if (mMediaSessionCompat.getController() != null) {
                        mMediaSessionCompat.sendSessionEvent(ACTION_COUNT_DOWN_END, null);
                    }
                    handleStopRequest(true);
                }
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        if (mMediaSessionCompat == null) {
            mMediaSessionCompat = new MediaSessionCompat(this, TAG);
        }
        mPlayQueueMediaId = new ArrayList<>();
        setSessionToken(mMediaSessionCompat.getSessionToken());
        PlaybackStateCompat.Builder mPlayBackStateBuilder = new PlaybackStateCompat.Builder();
        mPlayBackStateBuilder.setActions(PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_PAUSE
                | PlaybackStateCompat.ACTION_SEEK_TO | PlaybackStateCompat.ACTION_SKIP_TO_NEXT);
        mMediaSessionCompat.setPlaybackState(mPlayBackStateBuilder.build());
        mMediaSessionCompat.setCallback(new MediaSessionCallBack());
        mMediaSessionCompat.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS |
                MediaSessionCompat.FLAG_HANDLES_QUEUE_COMMANDS);

        mMediaSessionCompat.setRepeatMode(PlaybackStateCompat.REPEAT_MODE_NONE);

        if (!mServiceStarted) {
            startService(new Intent(getBaseContext(), MediaService.class));
            mServiceStarted = true;
        }

        if (!mMediaSessionCompat.isActive()) {
            mMediaSessionCompat.setActive(true);
        }

        Intent it = new Intent(getBaseContext(), MediaActivity.class);
        it.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(getBaseContext(), 1, it,
                PendingIntent.FLAG_UPDATE_CURRENT);
        mMediaSessionCompat.setSessionActivity(pendingIntent);

        mPlayBack = new PlayBack(this);
        mPlayBack.setCallBack(new PlayBack.CallBack() {
            @Override
            public void onCompletion() {
                if (mErrorTimes != 0) {
                    mErrorTimes = 0;
                }
                handleStopRequest(false);
            }

            @Override
            public void onPlayBackStateChange(int state) {
                onPlayStateChange();
            }

            @Override
            public void onError(int errorCode, String error) {
                mErrorTimes++;
                if (mErrorTimes < mMediaItemList.size()) {
                    onMediaChange(mPlayBack.onSkipToNext());
                }
            }
        });

        mAudioReceiver = new AudioBecomingNoisyReceiver(this);

        executorService = new ThreadPoolExecutor(2, 2,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<Runnable>(), new ThreadFactory() {
            @Override
            public Thread newThread(@NonNull Runnable r) {
                return new Thread(r, TAG);
            }
        });

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        //也可以在这接收通知按钮的event事件
        MediaButtonReceiver.handleIntent(mMediaSessionCompat, intent);
        return START_STICKY;
    }

    @Nullable
    @Override
    public BrowserRoot onGetRoot(@NonNull String clientPackageName, int clientUId, @Nullable Bundle bundle) {
        if (clientPackageName.equals(getPackageName())) {
            return new BrowserRoot(clientPackageName, bundle);
        } else {
            return null;
        }
    }

    @Override
    public void onLoadChildren(@NonNull String s, @NonNull final Result<List<MediaBrowserCompat.MediaItem>> result) {
        Log.d(TAG, "service onLoadChildren() called with: s = [" + s + "], result = [" + result + "]");
        if (s.equals(getPackageName())) {
            if (mMediaItemList.size() == 0) {
                result.detach();
                ArrayList<MediaBrowserCompat.MediaItem> list = DataTransform.getInstance().getMediaItemList();
                if (list != null) {
                    mMediaItemList = list;
                    PrintOut.print("load list size ... " + mMediaItemList.size());
                }
                result.sendResult(list);
            } else {
                result.sendResult(mMediaItemList);
            }
            if (mUpdateQueueRunnable == null) {
                mUpdateQueueRunnable = new UpdateQueueRunnable(this);
            }
            executorService.submit(mUpdateQueueRunnable);
        }
    }

    /**
     * 停止播放
     *
     * @param isNeedEnd 是否需要停止播放
     */
    private void handleStopRequest(boolean isNeedEnd) {
        Log.d(TAG, "handleStopRequest() called with: isNeedEnd = [" + isNeedEnd + "]");
        if (!isNeedEnd) {
            changeMediaByMode(true, true);
        } else {
            savePlayingRecord();
            executorService.submit(mUpdateQueueRunnable);
            NotificationUtils.cancelNotify(this, NotificationUtils.NOTIFY_ID);
            mMediaSessionCompat.setActive(false);
            stopForeground(true);
            System.exit(0);
        }
    }

    /**
     * 通过列表模式决定下一个播放的媒体
     *
     * @param isNext     是否是下一首操作
     * @param isComplete 调用是否来自歌曲播放完成
     */
    private void changeMediaByMode(boolean isNext, boolean isComplete) {
        Log.e(TAG, "changeMediaByMode: " + mMediaSessionCompat.getController().getRepeatMode());
        //判断重复模式，单曲重复，随机播放，列表播放
        switch (mMediaSessionCompat.getController().getRepeatMode()) {
            //随机播放：自动下一首  ----暂改为列表循环
            case PlaybackStateCompat.REPEAT_MODE_ALL:
                onMediaChange(mPlayBack.onSkipToNext());
                handlePlayOrPauseRequest();
                break;
            //单曲重复：重复当前的歌曲
            case PlaybackStateCompat.REPEAT_MODE_ONE:
                onMediaChange(mCurrentMedia.getDescription().getMediaId());
                handlePlayOrPauseRequest();
                break;
            //列表播放：判断是否播放到列表的最后
            case PlaybackStateCompat.REPEAT_MODE_NONE:
                if (isNext) {
                    int position = mQueueItemList.indexOf(mCurrentMedia);
                    //如果不是当前歌曲播放完成自动调用的话，就直接播放下一首
                    if (!isComplete || position < (mQueueItemList.size() - 1)) {
                        onMediaChange(mPlayBack.onSkipToNext());
                        handlePlayOrPauseRequest();
                    } else {
                        onMediaChange(mPlayQueueMediaId.get(mPlayQueueMediaId.size() - 1));
                        Log.e(TAG, "handleStopRequest: 已播放到最后一首曲目");
                    }
                } else {
                    onMediaChange(mPlayBack.onSkipToPrevious());
                    handlePlayOrPauseRequest();
                }
                break;
            default:
                break;
        }
    }

    /**
     * 保存播放记录到本地
     */
    public void savePlayingRecord() {
        //当前没有播放曲目
        if (mCurrentMedia == null) {
            return;
        }
        MusicDbEntity cacheEntity = new MusicDbEntity();
        cacheEntity.setLastMediaId(mPlayBack.getCurrentMediaId());
        if (mCurrentMedia != null && mCurrentMedia.getDescription() != null) {
            if (mCurrentMedia.getDescription().getMediaUri() != null) {
                cacheEntity.setLastMediaPath(mCurrentMedia.getDescription().getMediaUri().getPath());
            }
            cacheEntity.setLastPlayAuthor(String.valueOf(mCurrentMedia.getDescription().getSubtitle()));
            cacheEntity.setLastPlayName(String.valueOf(mCurrentMedia.getDescription().getTitle()));
        }
        cacheEntity.setLastPlayedPosition(mPlayBack.getCurrentStreamPosition());
        cacheEntity.setLastPlayIndex(mPlayQueueMediaId.indexOf(mPlayBack.getCurrentMediaId()));
        //删除已有的记录
        DBManager.getInstance().initDb(getApplicationContext()).deleteAll();
        DBManager.getInstance().insetEntity(cacheEntity);
    }

    /**
     * 当播放状态发生改变时
     */
    private void onPlayStateChange() {
        Log.d(TAG, "onPlayStateChange() called with: " + mPlayBack.getState());
        long position = PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN;
        if (mPlayBack != null) {
            position = mPlayBack.getCurrentStreamPosition();
        }
        long playbackActions = PlaybackStateCompat.ACTION_PLAY |
                PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID;
        if (mPlayBack.isPlaying()) {
            playbackActions |= PlaybackStateCompat.ACTION_PAUSE;
        }
        PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder()
                .setActions(playbackActions);
        int state = mPlayBack.getState();

        stateBuilder.setState(state, position, 1.0f, SystemClock.elapsedRealtime());
        if (mCurrentMedia != null) {
            stateBuilder.setActiveQueueItemId(mCurrentMedia.getQueueId());
        }
        mMediaSessionCompat.setPlaybackState(stateBuilder.build());
        Notification notification = NotificationUtils.createNotify(this, mMediaSessionCompat,
                mPlayBack.isPlaying());
        if (notification != null) {
            startForeground(NOTIFY_ID, notification);
        }
        if (state == PlaybackStateCompat.STATE_PLAYING) {
            mAudioReceiver.register(audioCallBack);
        } else {
            mAudioReceiver.unregister();
        }
    }

    /**
     * 播放曲目发生变化时
     *
     * @param mediaId 曲目id
     */
    public void onMediaChange(String mediaId) {
        if (mediaId != null && !DataTransform.getInstance().getPathList().isEmpty()) {
            if (mUpdateRunnable == null) {
                mUpdateRunnable = new UpdateRunnable(mediaId, mMediaSessionCompat);
            } else {
                mUpdateRunnable.setMediaId(mediaId);
            }
            executorService.submit(mUpdateRunnable);
            Log.e(TAG, "onMediaChange:" + DataTransform.getInstance().toString());
            int index = DataTransform.getInstance().getMediaIndex(mediaId);
            Log.e(TAG, "onMediaChange: index=" + index);
            mCurrentMedia = mQueueItemList.get(index);
            mPlayBack.preparedWithMediaId(mediaId);
        } else {
            mPlayBack.stopPlayer();
        }
    }

    private void removeQueueItemByDes(MediaDescriptionCompat des) {
        int index = getIndexByDes(des);
        removeQueueItemAt(index);
    }

    private int getIndexByDes(MediaDescriptionCompat des) {
        for (int i = 0; i < mQueueItemList.size(); i++) {
            MediaSessionCompat.QueueItem queueItem = mQueueItemList.get(i);
            if (queueItem != null && des != null) {
                if (StringUtils.Companion.ifEquals(queueItem.getDescription().getMediaId(),
                        des.getMediaId())) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * 移除列表中的item
     *
     * @param removeIndex 要移除item的位置
     */
    public void removeQueueItemAt(int removeIndex) {
        if (removeIndex == -1) {
            Log.e(TAG, "removeQueueItemAt: the index is " + removeIndex);
            return;
        }
        Log.e(TAG, "removeQueueItemAt: " + removeIndex);
        int state = mPlayBack.getState();
        if (mPlayBack.isPlaying()) {
            mPlayBack.pause();
        }
        //如果删除的是当前播放的歌曲，则播放新的曲目
        if (mPlayBack.getCurrentIndex() == removeIndex) {
            DataTransform.getInstance().removeItem(getApplicationContext(), removeIndex);
            executorService.submit(mUpdateQueueRunnable);
            if (mPlayQueueMediaId.size() > 0) {
                //删除的是前列表倒数第二个曲目的时候直接播放替代的曲目
                if (removeIndex <= mPlayQueueMediaId.size() - 1) {
                    onMediaChange(mPlayQueueMediaId.get(removeIndex));
                } else {//删除的是前列表最后一个曲目播放列表的第一个曲目
                    onMediaChange(mPlayQueueMediaId.get(0));
                }
                if (state == PlaybackStateCompat.STATE_PLAYING) {
                    handlePlayOrPauseRequest();
                }
            } else {
                mPlayBack.stopPlayer();
            }
        } else {//如果不是当前曲目，不能影响当前播放,记录下播放进度，更新列表后继续播放
            int currentIndex = mPlayBack.getCurrentIndex();
            int position = mPlayBack.getCurrentStreamPosition();
            DataTransform.getInstance().removeItem(getApplicationContext(), removeIndex);
            executorService.submit(mUpdateQueueRunnable);
            if (currentIndex < removeIndex) {
                onMediaChange(mPlayQueueMediaId.get(currentIndex));
            } else {
                onMediaChange(mPlayQueueMediaId.get(currentIndex - 1));
            }
            mPlayBack.seekTo(position, state == PlaybackStateCompat.STATE_PLAYING);
        }
    }

    /**
     * 处理播放或者暂停请求
     */
    public void handlePlayOrPauseRequest() {
        if (mCurrentMedia == null) {
            return;
        }
        mPlayBack.play();
    }

    @Override
    public void unbindService(ServiceConnection conn) {
        super.unbindService(conn);
        Log.d(TAG, "unbindService() called with: conn = [" + conn + "]");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy() called");
        NotificationUtils.cancelAllNotify(this);
        mAudioReceiver.unregister();
        mMediaSessionCompat.release();
        if (mCountDownTimer != null) {
            mCountDownTimer.stopTik();
            mCountDownTimer = null;
        }
        mPlayBack.stopPlayer();
        mPlayBack = null;
    }

    public void stopLoop() {
        if (receiver != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver);
        }
        stopService(new Intent(this, LoopService.class));
    }

    private void startLoop() {
        if (receiver == null) {
            receiver = new LoopReceiver(this);
        }
        startService(new Intent(this, LoopService.class));
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, filter);
    }

    /**
     * 进度更新到界面
     */
    public void updatePositionToSession() {
        try {
            if (mMediaSessionCompat.getController() == null) {
                stopLoop();
                return;
            }
            Bundle bundle = new Bundle();
            bundle.putInt(MediaService.UPDATE_POSITION_EVENT, mPlayBack.getCurrentStreamPosition());
            mMediaSessionCompat.sendSessionEvent(MediaService.UPDATE_POSITION_EVENT, bundle);
        } catch (Exception e) {
            stopLoop();
        }
    }

    private static class UpdateRunnable implements Runnable {
        private String mediaId;
        private WeakReference<MediaSessionCompat> mWeakSessionCompat;

        private UpdateRunnable(String mediaId, MediaSessionCompat sessionCompat) {
            this.mediaId = mediaId;
            this.mWeakSessionCompat = new WeakReference<>(sessionCompat);
        }

        private void setMediaId(String mediaId) {
            this.mediaId = mediaId;
        }

        @Override
        public void run() {
            //设置媒体信息
            MediaMetadataCompat track = DataTransform.getInstance().getMetadataCompatList().get(mediaId);
            //触发MediaControllerCompat.Callback->onMetadataChanged方法
            if (track != null && mWeakSessionCompat.get() != null) {
                mWeakSessionCompat.get().setMetadata(track);
            }
        }
    }

    public static class UpdateQueueRunnable implements Runnable {
        private WeakReference<MediaService> mWeakService;

        private UpdateQueueRunnable(MediaService mWeakService) {
            this.mWeakService = new WeakReference<>(mWeakService);
        }

        @Override
        public void run() {
            if (mWeakService.get() != null) {
                updateQueue(mWeakService.get());
            }
        }

        /**
         * 更新列表
         */
        private void updateQueue(MediaService mService) {
            mService.mMediaItemList = DataTransform.getInstance().getMediaItemList();
            Log.e(TAG, "updateQueue: size ... " + mService.mMediaItemList.size());

            mService.mQueueItemList = DataTransform.getInstance().getQueueItemList();
            mService.mPlayQueueMediaId = DataTransform.getInstance().getMediaIdList();
            mService.mPlayBack.setPlayQueue(mService.mPlayQueueMediaId);
            mService.mMediaSessionCompat.setQueue(mService.mQueueItemList);

            //覆盖本地缓存
            FileUtils.saveObject(DataTransform.getInstance().getMusicInfoEntities(),
                    mService.getCacheDir().getAbsolutePath());
        }
    }

    /**
     * 响应Activity的调用
     * getController.transportControls.
     */
    private class MediaSessionCallBack extends MediaSessionCompat.Callback {

        @Override
        public void onPlayFromMediaId(String mediaId, Bundle extras) {
            super.onPlayFromMediaId(mediaId, extras);
            Log.d(TAG, "onPlayFromMediaId() called with: mediaId = [" + mediaId + "]");
            if (extras != null) {
                String action = extras.getString(ACTION_PARAM);
                Log.d(TAG, "onPlayFromMediaId: extra=" + action);
                //缓冲请求
                if (ACTION_PREPARED_WITH_ID.equals(action)) {
                    if ("-1".equals(mediaId)) {
                        if (mPlayQueueMediaId != null && mPlayQueueMediaId.size() > 0) {
                            onMediaChange(mPlayQueueMediaId.get(0));
                        } else {
                            mMediaSessionCompat.sendSessionEvent(ERROR_PLAY_QUEUE_EVENT, null);
                        }
                    } else {
                        onMediaChange(mediaId);
                    }
                    //播放指定id请求
                } else if (ACTION_PLAY_WITH_ID.equals(action)) {
                    //如果和当前的mediaId相同则视为暂停或播放操作，不同则替换曲目
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                        if (!Objects.equals(mediaId, mCurrentMedia != null ?
                                mCurrentMedia.getDescription().getMediaId() : null)) {
                            onMediaChange(mediaId);
                        }
                    } else {
                        if (mCurrentMedia != null && mediaId.equals(mCurrentMedia.getDescription().getMediaId())) {
                            onMediaChange(mediaId);
                        }
                    }
                    handlePlayOrPauseRequest();
                    //初始化播放器，如果本地有播放记录，取播放记录，没有就初始化穿过来的media
                } else if (ACTION_PLAY_INIT.equals(action)) {
                    List<MusicDbEntity> entityRecordList = DBManager.getInstance()
                            .initDb(getApplicationContext()).getEntity();
                    if (entityRecordList.size() > 0) {
                        onMediaChange(entityRecordList.get(0).getLastMediaId());
                        mPlayBack.seekTo(entityRecordList.get(0).getLastPlayedPosition(), false);
                        Bundle extra = new Bundle();
                        extra.putInt(LOCAL_CACHE_POSITION_EVENT, entityRecordList.get(0).getLastPlayedPosition());
                        mMediaSessionCompat.sendSessionEvent(LOCAL_CACHE_POSITION_EVENT, extra);
                    } else {
                        onMediaChange(mediaId);
                    }
                } else if (ACTION_SEEK_TO.equals(action)) {
                    int seekPosition = extras.getInt(SEEK_TO_POSITION_PARAM);
                    mPlayBack.seekTo(seekPosition, true);
                    Log.e(TAG, "onPlayFromMediaId: " + seekPosition);
                } else {
                    PrintOut.i("unknown event");
                }
            }
        }

        @Override
        public void onPlay() {
            super.onPlay();
            Log.d(TAG, "onPlay() called");
            handlePlayOrPauseRequest();
        }

        @Override
        public void onPause() {
            super.onPause();
            Log.d(TAG, "onPause() called");
            mPlayBack.pause();
            mPlayBack.setIsUserPause(true);
        }

        @Override
        public void onStop() {
            super.onStop();
            handleStopRequest(true);
        }

        @Override
        public void onSkipToQueueItem(long id) {
            super.onSkipToQueueItem(id);
            try {
                String mediaId = DataTransform.getInstance().getMediaIdList().get((int) id);
                onMediaChange(mediaId);
                handlePlayOrPauseRequest();
            } catch (Exception e) {
                Log.e(TAG, e.getMessage());
            }
        }

        @Override
        public void onSkipToNext() {
            Log.d(TAG, "onSkipToNext() called");
            changeMediaByMode(true, false);
        }

        @Override
        public void onSkipToPrevious() {
            Log.d(TAG, "onSkipToPrevious() called");
            changeMediaByMode(false, false);
        }

        @Override
        public boolean onMediaButtonEvent(Intent mediaButtonEvent) {
            KeyEvent ke = mediaButtonEvent.getParcelableExtra("android.intent.extra.KEY_EVENT");
            if (ke.getAction() == KeyEvent.ACTION_DOWN) {
                switch (ke.getKeyCode()) {
                    case KeyEvent.KEYCODE_MEDIA_NEXT:
                        changeMediaByMode(true, false);
                        break;
                    case KeyEvent.KEYCODE_MEDIA_STOP:
                        handleStopRequest(true);
                        break;
                    case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                        changeMediaByMode(false, false);
                        break;
                    case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                    case KeyEvent.KEYCODE_MEDIA_PAUSE:
                    case KeyEvent.KEYCODE_MEDIA_PLAY:
                        handlePlayOrPauseRequest();
                        break;
                    default:
                        break;
                }
            } else {
                Log.w(TAG, "onMediaButtonEvent: action=" + ke.getAction() + ",code=" + ke.getKeyCode());
            }
            return true;
        }

        @Override
        public void onCommand(String command, Bundle reqExtra, ResultReceiver cb) {
            final Bundle resultExtra = new Bundle();
            switch (command) {
                case COMMAND_POSITION:
                    resultExtra.putInt("position", mPlayBack.getCurrentStreamPosition());
                    if (cb != null) {
                        cb.send(COMMAND_POSITION_CODE, resultExtra);
                    }
                    break;
                case COMMAND_UPDATE_QUEUE:
                    savePlayingRecord();
                    executorService.submit(mUpdateQueueRunnable);
                    List<MusicDbEntity> entity = DBManager.getInstance()
                            .initDb(getApplicationContext()).getEntity();
                    if (entity.size() > 0) {
                        String lastMediaId = entity.get(0).getLastMediaId();
                        if(!DataTransform.getInstance().getMediaIdList().contains(lastMediaId)){
                            onMediaChange(mPlayQueueMediaId.get(0));
                        }else{
                            onMediaChange(lastMediaId);
                            mPlayBack.seekTo(entity.get(0).getLastPlayedPosition(), false);
                        }
                    } else {
                        onMediaChange(mPlayQueueMediaId.get(0));
                    }
                    if (cb != null) {
                        cb.send(COMMAND_UPDATE_QUEUE_CODE, resultExtra);
                    }
                    break;
                //TODO 开始循环获取当前播放位置
                case COMMAND_START_LOOP:
                    startLoop();
                    break;
                //TODO 结束获取当前播放位置
                case COMMAND_STOP_LOOP:
                    stopLoop();
                    break;
                default:
                    PrintOut.print("onCommand no match");
                    super.onCommand(command, reqExtra, cb);
                    break;
            }
        }

        @Override
        public void onSetRepeatMode(int repeatMode) {
            super.onSetRepeatMode(repeatMode);
            mMediaSessionCompat.setRepeatMode(repeatMode);
        }

        @Override
        public void onRemoveQueueItem(MediaDescriptionCompat description) {
            super.onRemoveQueueItem(description);
            Log.d(TAG, "onRemoveQueueItem() called with: description = [" + description + "]");
            removeQueueItemByDes(description);
        }

        @Override
        public void onCustomAction(String action, Bundle extras) {
            super.onCustomAction(action, extras);
            switch (action) {
                //开始倒计时
                case ACTION_COUNT_DOWN_TIME:
                    if (mCountDownTimer != null) {
                        mCountDownTimer.stopTik();
                        mCountDownTimer = null;
                    }
                    mCountDownTimer = new TimerUtils(extras.getLong(ACTION_COUNT_DOWN_TIME), 1000);
                    mCountDownTimer.startTik(timeTikCallBack);
                    break;
                //结束倒计时
                case ACTION_STOP_COUNT_DOWN:
                    if (mCountDownTimer != null) {
                        mCountDownTimer.stopTik();
                        mCountDownTimer = null;
                    }
                    break;
                default:
                    break;
            }
        }
    }


}
