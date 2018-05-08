package com.ojm.pinstream.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
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
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;

import com.ojm.pinstream.R;
import com.ojm.pinstream.activities.MainActivity;

import java.io.IOException;
import java.util.Objects;

public class StreamingService extends Service
        implements MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener {

    public static final String ACTION_PLAY = "ACTION_PLAY";
    public static final String ACTION_PAUSE = "ACTION_PAUSE";
    public static final String ACTION_STOP = "ACTION_STOP";

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

                    if (mMediaPlayer != null)
                        configurePlayerState();
                }
            };

    private int mCurrentAudioFocusState = AUDIO_NO_FOCUS_NO_DUCK;

    private final IntentFilter mAudioNoisyIntentFilter =
            new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);

    private BroadcastReceiver mNoisyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mMediaPlayer.pause();
        }
    };

    private boolean mAudioNoisyReceiverRegistered = false;

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

        if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            stopSelf();
        } else {
            mCurrentAudioFocusState = AUDIO_FOCUSED;
            registerAudioNoisyReceiver();

            mMediaSession = new MediaSessionCompat(getApplicationContext(), "Pinstream");
            mMediaSession.setCallback(new MediaSessionCallback());
            mMediaSession.setFlags(
                    MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS);

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

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public boolean onError(MediaPlayer mediaPlayer, int i, int i1) {
        mMediaPlayer.reset();
        return true;
    }

    @Override
    public void onPrepared(MediaPlayer mediaPlayer) {
        handleIntent(mOnStartCommandIntent);
    }

    public class MediaSessionCallback extends MediaSessionCompat.Callback {
        @Override
        public void onPlay() {
            registerAudioNoisyReceiver();
            mMediaPlayer.start();
        }

        @Override
        public void onPause() {
            unregisterAudioNoisyReceiver();
            mMediaPlayer.pause();
        }

        @Override
        public void onStop() {
            unregisterAudioNoisyReceiver();
            mMediaPlayer.stop();
            mMediaSession.setActive(false);
            stopSelf();
        }
    }

    private void handleIntent(Intent intent) {
        switch (Objects.requireNonNull(intent.getAction())) {
            case ACTION_PLAY:
                mMediaController.getTransportControls().play();
                break;
            case ACTION_PAUSE:
                mMediaController.getTransportControls().pause();
                break;
            case ACTION_STOP:
                mMediaController.getTransportControls().stop();
                break;
        }
    }

    private Notification buildNotification() {
        createNotificationChannel();

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(getApplicationContext(), "1337");

        builder
                .setContentTitle(mOnStartCommandIntent.getStringExtra(STREAM_TITLE))
                .setContentIntent(PendingIntent.getActivity(
                        this,
                        0,
                        new Intent(getApplicationContext(), MainActivity.class),
                        0))
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setDeleteIntent(getActionIntent(ACTION_STOP));

        builder
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setColor(ContextCompat.getColor(this, R.color.colorPrimaryDark));

        builder
                .addAction(new NotificationCompat.Action(
                        android.R.drawable.ic_media_pause, getString(R.string.pause),
                        getActionIntent(ACTION_PAUSE)));

        builder
                .setStyle(new android.support.v4.media.app.NotificationCompat.MediaStyle()
                        .setShowActionsInCompactView(0)
                        .setShowCancelButton(true)
                        .setCancelButtonIntent(
                                getActionIntent(ACTION_STOP)));

        return builder.build();
    }

    private PendingIntent getActionIntent(String action) {
        Intent s = new Intent(getApplicationContext(), StreamingService.class);
        s.putExtra(
                STREAM_TITLE,
                mOnStartCommandIntent.getStringExtra(STREAM_TITLE)
        );

        s.putExtra(
                STREAM_URI,
                mOnStartCommandIntent.getStringExtra(STREAM_URI)
        );

        s.setAction(action);
        s.setPackage(getApplicationContext().getPackageName());

        return PendingIntent.getService(
                getApplicationContext(), 0, s, 0);
    }

    private void registerAudioNoisyReceiver() {
        if (!mAudioNoisyReceiverRegistered) {
            getApplicationContext()
                    .registerReceiver(mNoisyReceiver, mAudioNoisyIntentFilter);
            mAudioNoisyReceiverRegistered = true;
        }
    }

    private void unregisterAudioNoisyReceiver() {
        if (mAudioNoisyReceiverRegistered) {
            getApplicationContext()
                    .unregisterReceiver(mNoisyReceiver);
            mAudioNoisyReceiverRegistered = false;
        }
    }

    private void configurePlayerState() {
        switch(mCurrentAudioFocusState) {
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