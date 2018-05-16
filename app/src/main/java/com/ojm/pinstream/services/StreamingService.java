package com.ojm.pinstream.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaBrowserServiceCompat;
import android.support.v4.media.session.MediaButtonReceiver;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;

import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.ojm.pinstream.R;
import com.ojm.pinstream.database.DatabaseHandler;
import com.ojm.pinstream.models.Bookmark;

import java.util.List;
import java.util.Objects;

public class StreamingService extends MediaBrowserServiceCompat {

    public static final String AUDIO_SESSION_ID = "AUDIO_SESSION_ID";

    public static final float VOLUME_DUCK = 0.2f;
    public static final float VOLUME_NORMAL = 1.0f;

    private static final int AUDIO_NO_FOCUS_LOST = -1;
    private static final int AUDIO_NO_FOCUS_NO_DUCK = 0;
    private static final int AUDIO_NO_FOCUS_CAN_DUCK= 1;
    private static final int AUDIO_FOCUSED = 2;

    private static final String EMPTY_MEDIA_ROOT_ID = "/pinstream";

    private static final String NOTIFICATION_CHANNEL = "1337";
    private static final int NOTIFICATION_ID = 1338;

    private AudioManager.OnAudioFocusChangeListener mOnAudioFocusChangeListener =
            new AudioManager.OnAudioFocusChangeListener() {
                @Override
                public void onAudioFocusChange(int focusChange) {
                    switch (focusChange) {
                        case AudioManager.AUDIOFOCUS_GAIN:
                            mCurrentAudioFocusState = AUDIO_FOCUSED;
                            break;
                        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                            mCurrentAudioFocusState = AUDIO_NO_FOCUS_CAN_DUCK;
                            break;
                        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                            mCurrentAudioFocusState = AUDIO_NO_FOCUS_NO_DUCK;
                            break;
                        case AudioManager.AUDIOFOCUS_LOSS:
                            mCurrentAudioFocusState = AUDIO_NO_FOCUS_LOST;
                            break;
                    } if (mExoPlayer != null) configurePlayerState();
                }
            };

    private final IntentFilter mAudioNoisyIntentFilter =
            new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);

    private BroadcastReceiver mAudioNoisyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mMediaSession.getController().getTransportControls().pause();
        }
    };

    private boolean mAudioNoisyReceiverRegistered = false;
    private boolean mServiceInStartedState = false;
    private int mCurrentAudioFocusState = AUDIO_NO_FOCUS_NO_DUCK;

    private AudioManager mAudioManager;
    private MediaSessionCompat mMediaSession;
    private PlaybackStateCompat.Builder mPlaybackStateBuilder;
    private SimpleExoPlayer mExoPlayer;
    private WifiManager.WifiLock mWifiLock;

    private Bookmark mSelectedBookmark;

    @Nullable
    @Override
    public BrowserRoot onGetRoot
            (@NonNull String clientPackageName, int clientUid, @Nullable Bundle rootHints) {
        return new BrowserRoot(EMPTY_MEDIA_ROOT_ID, null);
    }

    @Override
    public void onLoadChildren
            (@NonNull String parentId, @NonNull Result<List<MediaBrowserCompat.MediaItem>> result) {
        if (TextUtils.equals(EMPTY_MEDIA_ROOT_ID, parentId)) result.sendResult(null);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        BandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();

        TrackSelection.Factory trackSelectionFactory =
                new AdaptiveTrackSelection.Factory(bandwidthMeter);

        TrackSelector trackSelector =
                new DefaultTrackSelector(trackSelectionFactory);

        mExoPlayer = ExoPlayerFactory.newSimpleInstance(this, trackSelector);

        mMediaSession = new MediaSessionCompat(
                this, getResources().getString(R.string.app_name));

        mMediaSession.setCallback(new MediaSessionCallback());

        mMediaSession.setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS
                        | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);

        mPlaybackStateBuilder = new PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_PAUSED, 0, 0)
                .setActions(
                        PlaybackStateCompat.ACTION_PREPARE_FROM_URI |
                                PlaybackStateCompat.ACTION_PLAY_PAUSE |
                                PlaybackStateCompat.ACTION_PLAY |
                                PlaybackStateCompat.ACTION_PAUSE |
                                PlaybackStateCompat.ACTION_STOP);

        mMediaSession.setPlaybackState(mPlaybackStateBuilder.build());
        setSessionToken(mMediaSession.getSessionToken());

        mWifiLock =
                ((WifiManager) Objects.requireNonNull(
                        this.getSystemService(Context.WIFI_SERVICE)))
                        .createWifiLock(WifiManager.WIFI_MODE_FULL, "ps_wifi_lock");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        MediaButtonReceiver.handleIntent(mMediaSession, intent);
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        mSelectedBookmark.setSelected(false);
        new DatabaseHandler(getApplicationContext())
                .updateBookmark(mSelectedBookmark);

        mExoPlayer.release();
        mMediaSession.release();
        unregisterAudioNoisyReceiver();

        super.onDestroy();
    }

    public class MediaSessionCallback extends MediaSessionCompat.Callback {
        @Override
        public void onPlay() {
            switch (mAudioManager.requestAudioFocus(
                    mOnAudioFocusChangeListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
            )) {
                case AudioManager.AUDIOFOCUS_REQUEST_GRANTED:
                    mCurrentAudioFocusState = AUDIO_FOCUSED;
                    registerAudioNoisyReceiver();

                    mWifiLock.acquire();
                    mExoPlayer.setPlayWhenReady(true);

                    mPlaybackStateBuilder
                            .setState(
                                    PlaybackStateCompat.STATE_PLAYING,
                                    0,
                                    0
                            );

                    Bundle audioSessionId = new Bundle();
                    audioSessionId.putInt(AUDIO_SESSION_ID, mExoPlayer.getAudioSessionId());
                    mPlaybackStateBuilder.setExtras(audioSessionId);

                    mMediaSession.setPlaybackState(mPlaybackStateBuilder.build());
                    mMediaSession.setActive(true);

                    configureServiceState(PlaybackStateCompat.ACTION_PLAY);
                    break;
                default:
                    configureServiceState(PlaybackStateCompat.ACTION_STOP);
                    break;
            }
        }

        @Override
        public void onPause() {
            unregisterAudioNoisyReceiver();
            mWifiLock.release();

            mExoPlayer.setPlayWhenReady(false);

            mPlaybackStateBuilder
                    .setState(PlaybackStateCompat.STATE_PAUSED, 0, 0);

            mMediaSession.setPlaybackState(mPlaybackStateBuilder.build());

            configureServiceState(PlaybackStateCompat.ACTION_PAUSE);
        }

        @Override
        public void onStop() {
            unregisterAudioNoisyReceiver();

            if (mWifiLock.isHeld()) mWifiLock.release();

            mExoPlayer.stop();

            mPlaybackStateBuilder.setState(
                    PlaybackStateCompat.STATE_STOPPED, 0,0);

            mMediaSession.setPlaybackState(mPlaybackStateBuilder.build());

            configureServiceState(PlaybackStateCompat.ACTION_STOP);
        }

        @Override
        public void onPrepareFromUri(Uri uri, Bundle extras) {
            MediaSource mediaSource = new ExtractorMediaSource.Factory(
                    new DefaultHttpDataSourceFactory(
                            getResources().getString(R.string.app_name)))
                    .createMediaSource(uri);

            extras.setClassLoader(Bookmark.class.getClassLoader());
            mSelectedBookmark = extras.getParcelable(Bookmark.PARCEL);

            mExoPlayer.prepare(mediaSource);
        }
    }

    private void configurePlayerState() {
        switch (mCurrentAudioFocusState) {
            case AUDIO_NO_FOCUS_CAN_DUCK:
                mExoPlayer.setVolume(VOLUME_DUCK);
                break;
            case AUDIO_NO_FOCUS_NO_DUCK:
                mMediaSession.getController().getTransportControls().pause();
                break;
            case AUDIO_NO_FOCUS_LOST:
                mMediaSession.getController().getTransportControls().stop();
                break;
            case AUDIO_FOCUSED:
                mExoPlayer.setVolume(VOLUME_NORMAL);
                break;
        }
    }

    private void registerAudioNoisyReceiver() {
        if (!mAudioNoisyReceiverRegistered) {
            this.registerReceiver(mAudioNoisyReceiver, mAudioNoisyIntentFilter);
            mAudioNoisyReceiverRegistered = true;
        }
    }

    private void unregisterAudioNoisyReceiver() {
        if (mAudioNoisyReceiverRegistered) {
            this.unregisterReceiver(mAudioNoisyReceiver);
            mAudioNoisyReceiverRegistered = false;
        }
    }

    private void configureServiceState(long action) {
        if (action == PlaybackStateCompat.ACTION_PLAY) {
            if (!mServiceInStartedState) {
                ContextCompat.startForegroundService(
                        StreamingService.this,
                        new Intent(
                                StreamingService.this,
                                StreamingService.class));
                mServiceInStartedState = true;
            } startForeground(NOTIFICATION_ID,
                    buildNotification(PlaybackStateCompat.ACTION_PAUSE));
        } else if (action == PlaybackStateCompat.ACTION_PAUSE) {
            stopForeground(false);

            NotificationManager mNotificationManager
                    = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

            assert mNotificationManager != null;
            mNotificationManager
                    .notify(NOTIFICATION_ID,
                            buildNotification(PlaybackStateCompat.ACTION_PLAY));
        } else if (action == PlaybackStateCompat.ACTION_STOP) {
            mServiceInStartedState = false;
            mMediaSession.setActive(false);

            mSelectedBookmark.setSelected(false);
            new DatabaseHandler(getApplicationContext())
                    .updateBookmark(mSelectedBookmark);

            stopForeground(true);
            stopSelf();
        }
    }

    private Notification buildNotification(long action) {
        createNotificationChannel();

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL);

        builder
                .setContentTitle(mSelectedBookmark.getTitle())
                .setContentIntent(mMediaSession.getController().getSessionActivity())
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setDeleteIntent(
                        MediaButtonReceiver.buildMediaButtonPendingIntent(
                                this, PlaybackStateCompat.ACTION_STOP));

        builder
                .setSmallIcon(R.drawable.ic_play_arrow);


        if (action == PlaybackStateCompat.ACTION_PLAY) {
            builder
                    .addAction(new NotificationCompat.Action(
                            R.drawable.ic_play_arrow, getString(R.string.play),
                            MediaButtonReceiver.buildMediaButtonPendingIntent(
                                    this, PlaybackStateCompat.ACTION_PLAY)));
        }

        if (action == PlaybackStateCompat.ACTION_PAUSE) {
            builder
                    .addAction(new NotificationCompat.Action(
                            R.drawable.ic_pause, getString(R.string.pause),
                            MediaButtonReceiver.buildMediaButtonPendingIntent(
                                    this, PlaybackStateCompat.ACTION_PAUSE)));
        }

        builder
                .setStyle(new android.support.v4.media.app.NotificationCompat.MediaStyle()
                        .setShowActionsInCompactView(0)
                        .setShowCancelButton(true)
                        .setMediaSession(mMediaSession.getSessionToken())
                        .setCancelButtonIntent(
                                MediaButtonReceiver.buildMediaButtonPendingIntent(
                                        this, PlaybackStateCompat.ACTION_STOP)));

        return builder.build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.channel_name);
            String description = getString(R.string.channel_description);
            int importance = NotificationManager.IMPORTANCE_DEFAULT;

            NotificationChannel channel =
                    new NotificationChannel(NOTIFICATION_CHANNEL, name, importance);

            channel.setDescription(description);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);

            assert notificationManager != null;
            notificationManager.createNotificationChannel(channel);
        }
    }

}