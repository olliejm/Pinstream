package com.ojm.pinstream.activities;

import android.content.ComponentName;
import android.content.Context;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.cleveroad.audiovisualization.AudioVisualization;
import com.cleveroad.audiovisualization.DbmHandler;
import com.cleveroad.audiovisualization.VisualizerDbmHandler;
import com.ojm.pinstream.R;
import com.ojm.pinstream.models.Bookmark;
import com.ojm.pinstream.services.StreamingService;

import java.util.Objects;

/**
 * This activity is a client activity that presents the in-app control view for the
 * streaming session. It contains a MediaBrowser which connects to our MediaBrowserService
 */
public class PlayActivity extends AppCompatActivity {

    // Instance field objects
    private MediaBrowserCompat mMediaBrowser;
    private ImageView mPlayPause;
    private Bookmark mSelectedBookmark;
    private AudioVisualization mAudioVisualization;

    // Connection callback for media browser, performed on connection
    private final MediaBrowserCompat.ConnectionCallback mConnectionCallback =
            new MediaBrowserCompat.ConnectionCallback() {
                @Override
                public void onConnected() {
                    // Get the token
                    MediaSessionCompat.Token token = mMediaBrowser.getSessionToken();
                    MediaControllerCompat mediaController = null;

                    try {
                        // Initialise the media controller from the token
                        mediaController
                                = new MediaControllerCompat(PlayActivity.this, token);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }

                    // Set controller for this activity
                    MediaControllerCompat
                            .setMediaController(PlayActivity.this, mediaController);

                    // Register our callback to the controller
                    MediaControllerCompat
                            .getMediaController(PlayActivity.this)
                            .registerCallback(mControllerCallback);

                    // Get current playback state
                    PlaybackStateCompat state = MediaControllerCompat
                            .getMediaController(PlayActivity.this)
                            .getPlaybackState();

                    // If we haven't started, or have stopped
                    if (state.getState() == PlaybackStateCompat.STATE_NONE ||
                            state.getState() == PlaybackStateCompat.STATE_STOPPED) {
                        // Just prepare and play the selected bookmark
                        prepareAndPlay();
                    }

                    // Otherwise, we're paused or playing already
                    else {
                        // In which case, only alter playback if new bookmark selected
                        assert state.getExtras() != null;
                        if (state
                                .getExtras()
                                .getInt(Bookmark.ID) != mSelectedBookmark.getID()) {
                            prepareAndPlay();
                        }
                    }

                    // Build the activity's transport controls
                    buildTransportControls();
                }
            };

    // Media controller callback for the activity, to deal with playback state change
    private MediaControllerCompat.Callback mControllerCallback =
            new MediaControllerCompat.Callback() {
                @Override
                public void onPlaybackStateChanged(PlaybackStateCompat state) {
                    assert state != null;
                    switch (state.getState()) {
                        // If playback has stopped, end the activity
                        case PlaybackStateCompat.STATE_STOPPED:
                            setResult(RESULT_OK);
                            finish();
                            break;
                        // If playback has paused, set play/pause icon to play arrow
                        case PlaybackStateCompat.STATE_PAUSED:
                            setPlayPauseDrawable(R.drawable.ic_play_arrow);
                            break;
                        // If playback has started, set play/pause icon to pause
                        case PlaybackStateCompat.STATE_PLAYING:
                            setPlayPauseDrawable(R.drawable.ic_pause);
                            break;
                    }
                }
            };

    /**
     * Runs on activity creation
     * @param savedInstanceState saved instance state
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_play);
        setSupportActionBar((Toolbar) findViewById(R.id.toolbar));
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);

        // Store the selected bookmark from the intent
        mSelectedBookmark = getIntent().getParcelableExtra(Bookmark.PARCEL);

        // Set the title of the stream
        TextView title = findViewById(R.id.playing_source_title);
        title.setText(mSelectedBookmark.getTitle());

        // Initialise the media browser for this activity
        mMediaBrowser = new MediaBrowserCompat(
                this,
                new ComponentName(this, StreamingService.class),
                mConnectionCallback,
                null);

        // Find the visualiser view
        mAudioVisualization = findViewById(R.id.visualizer_view);

        // Create a new visualiser handler for our context and audio session
        VisualizerDbmHandler visualizerHandler = DbmHandler
                .Factory
                .newVisualizerHandler(getApplicationContext(), 0);

        // Link the view to the handler
        mAudioVisualization.linkTo(visualizerHandler);
    }

    @Override
    public void onStart() {
        // Connect the browser on activity start
        super.onStart();
        mMediaBrowser.connect();
    }

    @Override
    public void onPause() {
        // Pause visualisation to avoid wasting resources
        mAudioVisualization.onPause();
        super.onPause();
    }

    @Override
    public void onResume() {
        // Resume visualisation and attach volume control to activity
        super.onResume();
        mAudioVisualization.onResume();
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
    }

    @Override
    public void onStop() {
        // Disconnect media browser and unregister controller callback
        mMediaBrowser.disconnect();

        MediaControllerCompat
                .getMediaController(PlayActivity.this)
                .unregisterCallback(mControllerCallback);

        super.onStop();
    }

    @Override
    public void onDestroy() {
        // Release all visualisation resources
        mAudioVisualization.release();
        super.onDestroy();
    }

    /**
     * Build transport controls for the activity
     */
    private void buildTransportControls() {
        mPlayPause = findViewById(R.id.play_pause);

        // Get state on activity start
        int onStartState = MediaControllerCompat
                .getMediaController(PlayActivity.this)
                .getPlaybackState()
                .getState();

        // If playing, set icon to pause
        if (onStartState == PlaybackStateCompat.STATE_PLAYING) {
            setPlayPauseDrawable(R.drawable.ic_pause);
        }

        // Otherwise, set icon to play as long as service is initialised
        else if (onStartState != PlaybackStateCompat.STATE_NONE) {
            setPlayPauseDrawable(R.drawable.ic_play_arrow);
        }

        // Set click listener for play/pause button
        mPlayPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Get playback state at time of click
                int onClickState = MediaControllerCompat
                        .getMediaController(PlayActivity.this)
                        .getPlaybackState()
                        .getState();

                // If we're playing, pause
                if (onClickState == PlaybackStateCompat.STATE_PLAYING) {
                    MediaControllerCompat
                            .getMediaController(PlayActivity.this)
                            .getTransportControls()
                            .pause();
                }

                // Otherwise, play. As long as service is initiated
                else if (onClickState != PlaybackStateCompat.STATE_NONE) {
                    MediaControllerCompat
                            .getMediaController(PlayActivity.this)
                            .getTransportControls()
                            .play();
                }
            }
        });

        // Set on click listener for stop ocon
        ImageView mStop = findViewById(R.id.stop);
        mStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Stop playback and finish our activity
                MediaControllerCompat
                        .getMediaController(PlayActivity.this)
                        .getTransportControls()
                        .stop();

                setResult(RESULT_OK);
                finish();
            }
        });

        // Find volume seek bar and assign to system volume
        SeekBar mVolumeControl = findViewById(R.id.volume_control);

        final AudioManager mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        assert mAudioManager != null;

        mVolumeControl.setMax(mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        mVolumeControl.setProgress(mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC));
        mVolumeControl.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) { }
        });
    }

    /**
     * Prepare the service from the Uri attached to the intent, and begin playback
     */
    private void prepareAndPlay() {
        MediaControllerCompat.getMediaController(PlayActivity.this)
                .getTransportControls()
                .prepareFromUri(
                        mSelectedBookmark.getUrl(),
                        getIntent().getExtras());

        MediaControllerCompat.getMediaController(PlayActivity.this)
                .getTransportControls()
                .play();
    }

    /**
     * Set icon for the play/pause button
     * @param id the id of the icon that should be used
     */
    private void setPlayPauseDrawable(int id) {
        mPlayPause.setImageDrawable(getResources().getDrawable(id));
    }

}