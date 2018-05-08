package com.ojm.pinstream.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.media.session.MediaButtonReceiver;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import com.ojm.pinstream.R;

import java.io.IOException;
import java.util.Objects;

public class StreamingService extends Service
        implements MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener {

    public static final String NOTIFICATION_CHANNEL = "1337";
    public static final int NOTIFICATION_ID = 1338;

    public static final String STREAM_URI = "STREAM_URI";
    public static final String STREAM_TITLE = "STREAM_TITLE";

    public static final float VOLUME_DUCK = 0.2f;
    public static final float VOLUME_NORMAL = 1.0f;

    private static final int AUDIO_NO_FOCUS_LOST = -1;
    private static final int AUDIO_NO_FOCUS_NO_DUCK = 0;
    private static final int AUDIO_NO_FOCUS_CAN_DUCK= 1;
    private static final int AUDIO_FOCUSED = 2;

    private MediaPlayer mMediaPlayer;
    private MediaSessionCompat mMediaSession;
    private MediaControllerCompat mMediaController;

    private Intent mOnStartCommandIntent;

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
                    }

                    if (mMediaPlayer != null) configurePlayerState();
                }
            };

    private int mCurrentAudioFocusState = AUDIO_NO_FOCUS_NO_DUCK;

    private final IntentFilter mAudioNoisyIntentFilter =
            new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);

    private BroadcastReceiver mAudioNoisyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mMediaController.getTransportControls().pause();
        }
    };

    private boolean mAudioNoisyReceiverRegistered = false;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        AudioManager mAudioManager = (AudioManager)
                getSystemService(Context.AUDIO_SERVICE);

        assert mAudioManager != null;
        int result = mAudioManager.requestAudioFocus(
                mOnAudioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
        );

        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            init();
        } else {
            stopSelf();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            mMediaPlayer.setDataSource(intent.getStringExtra(STREAM_URI));
        } catch (IOException e) {
            e.printStackTrace();
        }

        mMediaPlayer.prepareAsync();
        mOnStartCommandIntent = intent;

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mMediaPlayer.release();
        mMediaSession.release();
    }

    @Override
    public boolean onError(MediaPlayer mediaPlayer, int i, int i1) {
        mMediaPlayer.reset();
        return true;
    }

    @Override
    public void onPrepared(MediaPlayer mediaPlayer) {
        MediaButtonReceiver.handleIntent(mMediaSession, mOnStartCommandIntent);
    }

    public class MediaSessionCallback extends MediaSessionCompat.Callback {
        @Override
        public void onPlay() {
            registerAudioNoisyReceiver();
            mMediaPlayer.start();
            startForeground(
                    NOTIFICATION_ID, buildNotification(PlaybackStateCompat.ACTION_PAUSE));
        }

        @Override
        public void onPause() {
            unregisterAudioNoisyReceiver();
            mMediaPlayer.pause();
            startForeground(
                    NOTIFICATION_ID, buildNotification(PlaybackStateCompat.ACTION_PLAY));
        }

        @Override
        public void onStop() {
            unregisterAudioNoisyReceiver();
            mMediaPlayer.stop();
            mMediaSession.setActive(false);
            stopSelf();
        }
    }

    private void init() {
        mCurrentAudioFocusState = AUDIO_FOCUSED;
        registerAudioNoisyReceiver();

        mMediaSession = new MediaSessionCompat(getApplicationContext(), "Pinstream");
        mMediaSession.setCallback(new MediaSessionCallback());
        mMediaSession.setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS);
        mMediaSession.setActive(true);

        try {
            mMediaController = new MediaControllerCompat(
                    getApplicationContext(), mMediaSession.getSessionToken());
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        mMediaPlayer = new MediaPlayer();
        mMediaPlayer.setOnPreparedListener(this);
        mMediaPlayer.setOnErrorListener(this);
        mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        mMediaPlayer.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);

        WifiManager.WifiLock wifiLock =
                ((WifiManager) Objects.requireNonNull(
                        getApplicationContext().getSystemService(Context.WIFI_SERVICE)))
                        .createWifiLock(WifiManager.WIFI_MODE_FULL, "ps_wifi_lock");
        wifiLock.acquire();
    }

    private void configurePlayerState() {
        switch (mCurrentAudioFocusState) {
            case AUDIO_NO_FOCUS_CAN_DUCK:
                mMediaPlayer.setVolume(VOLUME_DUCK, VOLUME_DUCK);
                break;
            case AUDIO_NO_FOCUS_NO_DUCK:
                mMediaController.getTransportControls().pause();
                break;
            case AUDIO_NO_FOCUS_LOST:
                mMediaController.getTransportControls().stop();
                break;
            case AUDIO_FOCUSED:
                mMediaPlayer.setVolume(VOLUME_NORMAL, VOLUME_NORMAL);
                break;
        }
    }

    private void registerAudioNoisyReceiver() {
        if (!mAudioNoisyReceiverRegistered) {
            getApplicationContext()
                    .registerReceiver(mAudioNoisyReceiver, mAudioNoisyIntentFilter);
            mAudioNoisyReceiverRegistered = true;
        }
    }

    private void unregisterAudioNoisyReceiver() {
        if (mAudioNoisyReceiverRegistered) {
            getApplicationContext()
                    .unregisterReceiver(mAudioNoisyReceiver);
            mAudioNoisyReceiverRegistered = false;
        }
    }

    private Notification buildNotification(long action) {
        createNotificationChannel();

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(getApplicationContext(), NOTIFICATION_CHANNEL);

        builder
                .setContentTitle(mOnStartCommandIntent.getStringExtra(STREAM_TITLE))
                .setContentIntent(mMediaController.getSessionActivity())
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setDeleteIntent(
                        MediaButtonReceiver.buildMediaButtonPendingIntent(
                                this, PlaybackStateCompat.ACTION_STOP));

        builder
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setColor(ContextCompat.getColor(this, R.color.colorPrimaryDark));


        if (action == PlaybackStateCompat.ACTION_PLAY) {
            builder
                    .addAction(new NotificationCompat.Action(
                            android.R.drawable.ic_media_play, getString(R.string.play),
                            MediaButtonReceiver.buildMediaButtonPendingIntent(
                                    this, PlaybackStateCompat.ACTION_PLAY)));
        }

        if (action == PlaybackStateCompat.ACTION_PAUSE) {
            builder
                    .addAction(new NotificationCompat.Action(
                            android.R.drawable.ic_media_pause, getString(R.string.pause),
                            MediaButtonReceiver.buildMediaButtonPendingIntent(
                                    this, PlaybackStateCompat.ACTION_PAUSE)));
        }

        builder
                .setStyle(new android.support.v4.media.app.NotificationCompat.MediaStyle()
                        .setShowActionsInCompactView(0)
                        .setShowCancelButton(true)
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
                    new NotificationChannel("1337", name, importance);
            channel.setDescription(description);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            assert notificationManager != null;
            notificationManager.createNotificationChannel(channel);
        }
    }

}