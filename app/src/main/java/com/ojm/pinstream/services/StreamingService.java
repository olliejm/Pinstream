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

/**
 * A MediaBrowserService class that provides the streaming functionality.
 * The class starts and maintains a background service with a foreground notification,
 * and manages audio output management, playback state callbacks and playback itself
 */
public class StreamingService extends MediaBrowserServiceCompat {

    // Volume levels to use for audio ducking
    private static final float VOLUME_DUCK = 0.2f;
    private static final float VOLUME_NORMAL = 1.0f;

    // Static values to be used to indicate current audio focus level
    private static final int AUDIO_FOCUSED = 2;
    private static final int AUDIO_NO_FOCUS_CAN_DUCK= 1;
    private static final int AUDIO_NO_FOCUS_LOST = -1;
    private static final int AUDIO_NO_FOCUS_NO_DUCK = 0;

    // Our notification ID
    private static final int NOTIFICATION_ID = 1338;

    // A root media directory is required for MediaBrowserService classes
    // This represents a dummy root directory as we are not playing on-device files
    private static final String EMPTY_MEDIA_ROOT_ID = "/pinstream";

    // Out notification channel
    private static final String NOTIFICATION_CHANNEL = "1337";

    // Intent filter for audio becoming noisy (headphones unplugged)
    private final IntentFilter mAudioNoisyIntentFilter =
            new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);

    // Receives broadcast of headphones unplugged and pauses playback accordingly
    private final BroadcastReceiver mAudioNoisyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mMediaSession.getController().getTransportControls().pause();
        }
    };

    // Media session callback provides essential control functions
    private final MediaSessionCompat.Callback mMediaSessionCallback =
            new MediaSessionCompat.Callback() {
                /**
                 * Performed upon receipt of play command
                 */
                @Override
                public void onPlay() {
                    // Request audio focus from system
                    switch (mAudioManager.requestAudioFocus(
                            mOnAudioFocusChangeListener,
                            AudioManager.STREAM_MUSIC,
                            AudioManager.AUDIOFOCUS_GAIN)) {
                        // In case of request granted
                        case AudioManager.AUDIOFOCUS_REQUEST_GRANTED:
                            // Store instance focus state
                            mCurrentAudioFocusState = AUDIO_FOCUSED;

                            // Register receiver
                            registerAudioNoisyReceiver();

                            // Acquire wifi lock and start playback
                            mWifiLock.acquire();
                            mExoPlayer.setPlayWhenReady(true);

                            // Set playback state to playing
                            mPlaybackStateBuilder
                                    .setState(
                                            PlaybackStateCompat.STATE_PLAYING,
                                            0,
                                            0);

                            // Assign playback state to session and set session active
                            mMediaSession.setPlaybackState(mPlaybackStateBuilder.build());
                            mMediaSession.setActive(true);

                            // Configure service state
                            configureServiceState(PlaybackStateCompat.ACTION_PLAY);
                            break;
                        // Focus request was not granted, default to stopping the service
                        default:
                            stopSelf();
                            break;
                    }
                }

                /**
                 * Performed on pause request
                 */
                @Override
                public void onPause() {
                    // Unregister noisy receiver
                    unregisterAudioNoisyReceiver();

                    // Release wifi lock if help
                    if (mWifiLock.isHeld()) mWifiLock.release();

                    // Stop playback
                    mExoPlayer.setPlayWhenReady(false);

                    // Adjust and set states accordingly
                    mPlaybackStateBuilder
                            .setState(PlaybackStateCompat.STATE_PAUSED, 0, 0);

                    mMediaSession.setPlaybackState(mPlaybackStateBuilder.build());

                    configureServiceState(PlaybackStateCompat.ACTION_PAUSE);
                }

                /**
                 * Performed on stop command
                 */
                @Override
                public void onStop() {
                    // Unregister noisy receiver
                    unregisterAudioNoisyReceiver();

                    // Release wifi lock if held
                    if (mWifiLock.isHeld()) mWifiLock.release();

                    // Stop playback
                    mExoPlayer.stop();

                    // Configure and set states
                    mPlaybackStateBuilder.setState(
                            PlaybackStateCompat.STATE_STOPPED,
                            0,
                            0);

                    mMediaSession.setPlaybackState(mPlaybackStateBuilder.build());

                    configureServiceState(PlaybackStateCompat.ACTION_STOP);
                }

                /**
                 * Performed up on request to prepare the service from a URI
                 * @param uri the Uri to be attached to the player
                 * @param extras Bundle containing any needed extras
                 */
                @Override
                public void onPrepareFromUri(Uri uri, Bundle extras) {
                    // Configure an ExoPlayer media source from the URI
                    MediaSource mediaSource = new ExtractorMediaSource.Factory(
                            new DefaultHttpDataSourceFactory(
                                    getResources().getString(R.string.app_name)))
                            .createMediaSource(uri);

                    // Set class loader to Bookmark and retrieve from parcel
                    extras.setClassLoader(Bookmark.class.getClassLoader());
                    mSelectedBookmark = extras.getParcelable(Bookmark.PARCEL);

                    // Create a bundle with the currently playing bookmark's ID
                    // Allows client to test if user is already streaming this URI
                    Bundle idBundle = new Bundle();
                    idBundle.putInt(Bookmark.ID, mSelectedBookmark.getID());

                    // Attach bundle to playback state, and state to session
                    mPlaybackStateBuilder.setExtras(idBundle);
                    mMediaSession.setPlaybackState(mPlaybackStateBuilder.build());

                    // Prepare the media player
                    mExoPlayer.prepare(mediaSource);
                }
            };

    // Audio focus change listener for notification noises or other focus loss
    private final AudioManager.OnAudioFocusChangeListener mOnAudioFocusChangeListener =
            new AudioManager.OnAudioFocusChangeListener() {
                /**
                 * Performed on detection of audio focus change
                 * @param focusChange int identifying the change that has occurred
                 */
                @Override
                public void onAudioFocusChange(int focusChange) {
                    switch (focusChange) {
                        // If focus gained
                        case AudioManager.AUDIOFOCUS_GAIN:
                            mCurrentAudioFocusState = AUDIO_FOCUSED;
                            break;
                        // Focus lost transiently, can duck volume
                        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                            mCurrentAudioFocusState = AUDIO_NO_FOCUS_CAN_DUCK;
                            break;
                        // Focus lost transiently, can't duck volume
                        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                            mCurrentAudioFocusState = AUDIO_NO_FOCUS_NO_DUCK;
                            break;
                        // Focus lost permanently
                        case AudioManager.AUDIOFOCUS_LOSS:
                            mCurrentAudioFocusState = AUDIO_NO_FOCUS_LOST;
                            break;
                    }

                    // Configure media player's state for new focus state
                    if (mExoPlayer != null) configurePlayerState();
                }
            };

    // Track is noisy receiver is registered and if service is started
    private boolean mAudioNoisyReceiverRegistered = false;
    private boolean mServiceInStartedState = false;

    // Track focus state
    private int mCurrentAudioFocusState = AUDIO_NO_FOCUS_NO_DUCK;

    // Instance object fields
    private AudioManager mAudioManager;
    private SimpleExoPlayer mExoPlayer;
    private MediaSessionCompat mMediaSession;
    private PlaybackStateCompat.Builder mPlaybackStateBuilder;
    private Bookmark mSelectedBookmark;
    private WifiManager.WifiLock mWifiLock;

    /**
     * Required to implement for MediaBrowserService, performs no functionality here
     */
    @Nullable
    @Override
    public BrowserRoot onGetRoot(@NonNull String clientPackageName,
                                 int clientUid,
                                 @Nullable Bundle rootHints) {
        return new BrowserRoot(EMPTY_MEDIA_ROOT_ID, null);
    }

    /**
     * Required to implement for MediaBrowserService, performs no functionality here
     */
    @Override
    public void onLoadChildren(@NonNull String parentId,
                               @NonNull Result<List<MediaBrowserCompat.MediaItem>> result) {
        if (TextUtils.equals(EMPTY_MEDIA_ROOT_ID, parentId)) result.sendResult(null);
    }

    /**
     * Configure the service when started, this function runs whenever service start
     * is initiated.
     */
    @Override
    public void onCreate() {
        super.onCreate();

        // Retrieve system audio manager
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        // Some setup required for ExoPlayer
        BandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();

        TrackSelection.Factory trackSelectionFactory =
                new AdaptiveTrackSelection.Factory(bandwidthMeter);

        TrackSelector trackSelector =
                new DefaultTrackSelector(trackSelectionFactory);

        // Get ExoPlayer instance
        mExoPlayer = ExoPlayerFactory.newSimpleInstance(this, trackSelector);

        // Initialise the media session and set callbacks and handler flags
        mMediaSession = new MediaSessionCompat(this, getResources().getString(R.string.app_name));

        mMediaSession.setCallback(mMediaSessionCallback);

        mMediaSession.setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS
                        | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);

        // Initialise playback state and set current state and supported actions
        mPlaybackStateBuilder = new PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_NONE, 0, 0)
                .setActions(
                        PlaybackStateCompat.ACTION_PREPARE_FROM_URI |
                                PlaybackStateCompat.ACTION_PLAY_PAUSE |
                                PlaybackStateCompat.ACTION_PLAY |
                                PlaybackStateCompat.ACTION_PAUSE |
                                PlaybackStateCompat.ACTION_STOP);

        // Assign state to session and assign session token to service
        mMediaSession.setPlaybackState(mPlaybackStateBuilder.build());
        setSessionToken(mMediaSession.getSessionToken());

        // Obtain wifi lock
        mWifiLock =
                ((WifiManager) Objects.requireNonNull(
                        this.getSystemService(Context.WIFI_SERVICE)))
                        .createWifiLock(WifiManager.WIFI_MODE_FULL, "ps_wifi_lock");
    }

    /**
     * Runs when the service receives an incoming intent such as a media button event
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Forward the media button intent to the session
        MediaButtonReceiver.handleIntent(mMediaSession, intent);
        return super.onStartCommand(intent, flags, startId);
    }

    /**
     * Performed when service is destroyed by system after stopping
     */
    @Override
    public void onDestroy() {
        // Deselect bookmark and update database
        mSelectedBookmark.setSelected(false);
        new DatabaseHandler(getApplicationContext())
                .updateBookmark(mSelectedBookmark);

        // Release wifi lock if held
        if (mWifiLock.isHeld()) mWifiLock.release();

        // Release other resources
        mExoPlayer.release();
        mMediaSession.release();
        unregisterAudioNoisyReceiver();

        super.onDestroy();
    }

    /**
     * Assembles a media notification that displays the given action
     * @param action long ID of the given PlaybackState action to be displayed
     * @return a built Notification object
     */
    private Notification buildNotification(long action) {
        // Must create a channel for higher API versions
        createNotificationChannel();

        // Obtain notification builder
        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL);

        // Assign stream title, intent to stop playback on removal, tray icon, etc.
        builder
                .setContentTitle(mSelectedBookmark.getTitle())
                .setContentIntent(mMediaSession.getController().getSessionActivity())
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setDeleteIntent(
                        MediaButtonReceiver.buildMediaButtonPendingIntent(
                                this, PlaybackStateCompat.ACTION_STOP))
                .setSmallIcon(R.drawable.ic_stat_name);


        // If intended action is to play, add play icon which calls play action
        if (action == PlaybackStateCompat.ACTION_PLAY) {
            builder
                    .addAction(new NotificationCompat.Action(
                            R.drawable.ic_play_arrow, getString(R.string.play),
                            MediaButtonReceiver.buildMediaButtonPendingIntent(
                                    this, PlaybackStateCompat.ACTION_PLAY)));
        }

        // If intended action is to pause, add pause icon which calls pause action
        if (action == PlaybackStateCompat.ACTION_PAUSE) {
            builder
                    .addAction(new NotificationCompat.Action(
                            R.drawable.ic_pause, getString(R.string.pause),
                            MediaButtonReceiver.buildMediaButtonPendingIntent(
                                    this, PlaybackStateCompat.ACTION_PAUSE)));
        }

        // Set notification as being media style
        builder
                .setStyle(new android.support.v4.media.app.NotificationCompat.MediaStyle()
                        .setShowActionsInCompactView(0)
                        .setShowCancelButton(true)
                        .setMediaSession(mMediaSession.getSessionToken())
                        .setCancelButtonIntent(
                                MediaButtonReceiver.buildMediaButtonPendingIntent(
                                        this, PlaybackStateCompat.ACTION_STOP)));

        // Build and return
        return builder.build();
    }

    /**
     * Configure the state of the media player (called on focus change)
     */
    private void configurePlayerState() {
        switch (mCurrentAudioFocusState) {
            // If duck-able, lower volume
            case AUDIO_NO_FOCUS_CAN_DUCK:
                mExoPlayer.setVolume(VOLUME_DUCK);
                break;
            // If transient but not duck-able, pause
            case AUDIO_NO_FOCUS_NO_DUCK:
                mMediaSession.getController().getTransportControls().pause();
                break;
            // If focus is lost completely, stop
            case AUDIO_NO_FOCUS_LOST:
                mMediaSession.getController().getTransportControls().stop();
                break;
            // If focus gained, return to normal volume
            case AUDIO_FOCUSED:
                mExoPlayer.setVolume(VOLUME_NORMAL);
                break;
        }
    }

    /**
     * Configure the state of the service given the desired action
     * @param action the action to performed to the service state
     */
    private void configureServiceState(long action) {
        // If we are changing to playing state
        if (action == PlaybackStateCompat.ACTION_PLAY) {
            // Start foreground service if not yet running
            if (!mServiceInStartedState) {
                ContextCompat.startForegroundService(
                        StreamingService.this,
                        new Intent(
                                StreamingService.this,
                                StreamingService.class));

                mServiceInStartedState = true;
            }

            // Launch foreground notification
            startForeground(
                    NOTIFICATION_ID,
                    buildNotification(PlaybackStateCompat.ACTION_PAUSE));
        }

        // If we are changing to paused state
        else if (action == PlaybackStateCompat.ACTION_PAUSE) {
            // Stop foreground service but keep notification
            stopForeground(false);

            // Get notification manager and make notification non-foreground (swipe-able)
            // Swiping will cause the service to stop
            NotificationManager mNotificationManager
                    = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

            assert mNotificationManager != null;
            mNotificationManager.notify(
                    NOTIFICATION_ID,
                    buildNotification(PlaybackStateCompat.ACTION_PLAY));
        }

        // If we are changing to stopped state
        else if (action == PlaybackStateCompat.ACTION_STOP) {
            // Configure relevant fields
            mServiceInStartedState = false;
            mMediaSession.setActive(false);

            // Deselect bookmark and update database
            mSelectedBookmark.setSelected(false);
            new DatabaseHandler(getApplicationContext()).updateBookmark(mSelectedBookmark);

            // Stop foreground service removing notification, then destroy service
            stopForeground(true);
            stopSelf();
        }
    }

    /**
     * Creates a notification channel as required for higher APIs
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Description and info for notification settings screen
            CharSequence name = getString(R.string.channel_name);
            String description = getString(R.string.channel_description);
            int importance = NotificationManager.IMPORTANCE_DEFAULT;

            NotificationChannel notificationChannel =
                    new NotificationChannel(NOTIFICATION_CHANNEL, name, importance);

            notificationChannel.setDescription(description);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);

            assert notificationManager != null;
            notificationManager.createNotificationChannel(notificationChannel);
        }
    }

    /**
     * Register the audio noisy receiver if it isn't already
     */
    private void registerAudioNoisyReceiver() {
        if (!mAudioNoisyReceiverRegistered) {
            this.registerReceiver(mAudioNoisyReceiver, mAudioNoisyIntentFilter);
            mAudioNoisyReceiverRegistered = true;
        }
    }

    /**
     * Unregister the audio noisy receiver if it's registered
     */
    private void unregisterAudioNoisyReceiver() {
        if (mAudioNoisyReceiverRegistered) {
            this.unregisterReceiver(mAudioNoisyReceiver);
            mAudioNoisyReceiverRegistered = false;
        }
    }

}