package com.ojm.pinstream.activities;

import android.content.ComponentName;
import android.content.Context;
import android.media.AudioManager;
import android.net.Uri;
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

public class PlayActivity extends AppCompatActivity {

    private MediaBrowserCompat mMediaBrowser;
    private ImageView mPlayPause;
    private Bookmark mSelectedBookmark;
    private AudioVisualization mAudioVisualization;

    private final MediaBrowserCompat.ConnectionCallback mConnectionCallback =
            new MediaBrowserCompat.ConnectionCallback() {
                @Override
                public void onConnected() {
                    MediaSessionCompat.Token token = mMediaBrowser.getSessionToken();
                    MediaControllerCompat mediaController = null;

                    try {
                        mediaController
                                = new MediaControllerCompat(PlayActivity.this, token);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }

                    MediaControllerCompat
                            .setMediaController(PlayActivity.this, mediaController);

                    MediaControllerCompat
                            .getMediaController(PlayActivity.this)
                            .registerCallback(mControllerCallback);

                    PlaybackStateCompat state = MediaControllerCompat
                            .getMediaController(PlayActivity.this)
                            .getPlaybackState();

                    if (state.getState() == PlaybackStateCompat.STATE_NONE ||
                            state.getState() == PlaybackStateCompat.STATE_STOPPED) {
                        prepareAndPlay();
                    } else {
                        assert state.getExtras() != null;
                        if (state
                                .getExtras()
                                .getInt(Bookmark.ID) != mSelectedBookmark.getID()) {
                            prepareAndPlay();
                        }
                    }

                    buildTransportControls();
                }
            };

    private MediaControllerCompat.Callback mControllerCallback =
            new MediaControllerCompat.Callback() {
                @Override
                public void onPlaybackStateChanged(PlaybackStateCompat state) {
                    assert state != null;
                    switch (state.getState()) {
                        case PlaybackStateCompat.STATE_STOPPED:
                            setResult(RESULT_OK);
                            finish();
                            break;
                        case PlaybackStateCompat.STATE_PAUSED:
                            setPlayPauseDrawable(R.drawable.ic_play_arrow);
                            break;
                        case PlaybackStateCompat.STATE_PLAYING:
                            setPlayPauseDrawable(R.drawable.ic_pause);
                            break;
                    }
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_play);
        setSupportActionBar((Toolbar) findViewById(R.id.toolbar));
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);

        mSelectedBookmark = getIntent().getParcelableExtra(Bookmark.PARCEL);

        TextView title = findViewById(R.id.playing_source_title);
        title.setText(mSelectedBookmark.getTitle());

        mMediaBrowser = new MediaBrowserCompat(
                this,
                new ComponentName(this, StreamingService.class),
                mConnectionCallback,
                null);

        mAudioVisualization = findViewById(R.id.visualizer_view);

        VisualizerDbmHandler visualizerHandler = DbmHandler
                .Factory
                .newVisualizerHandler(getApplicationContext(), 0);

        mAudioVisualization.linkTo(visualizerHandler);
    }

    @Override
    public void onStart() {
        super.onStart();
        mMediaBrowser.connect();
    }

    @Override
    public void onResume() {
        super.onResume();
        mAudioVisualization.onResume();
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
    }

    @Override
    public void onPause() {
        mAudioVisualization.onPause();
        super.onPause();
    }

    @Override
    public void onStop() {
        mMediaBrowser.disconnect();

        MediaControllerCompat
                .getMediaController(PlayActivity.this)
                .unregisterCallback(mControllerCallback);

        super.onStop();
    }

    @Override
    public void onDestroy() {
        mAudioVisualization.release();
        super.onDestroy();
    }

    private void buildTransportControls() {
        mPlayPause = findViewById(R.id.play_pause);

        int onStartState = MediaControllerCompat
                .getMediaController(PlayActivity.this)
                .getPlaybackState()
                .getState();

        if (onStartState == PlaybackStateCompat.STATE_PLAYING) {
            setPlayPauseDrawable(R.drawable.ic_pause);
        } else if (onStartState != PlaybackStateCompat.STATE_NONE) {
            setPlayPauseDrawable(R.drawable.ic_play_arrow);
        }

        mPlayPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int onClickState = MediaControllerCompat
                        .getMediaController(PlayActivity.this)
                        .getPlaybackState()
                        .getState();

                if (onClickState == PlaybackStateCompat.STATE_PLAYING) {
                    MediaControllerCompat
                            .getMediaController(PlayActivity.this)
                            .getTransportControls()
                            .pause();
                } else if (onClickState != PlaybackStateCompat.STATE_NONE) {
                    MediaControllerCompat
                            .getMediaController(PlayActivity.this)
                            .getTransportControls()
                            .play();
                }
            }
        });

        ImageView mStop = findViewById(R.id.stop);
        mStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MediaControllerCompat
                        .getMediaController(PlayActivity.this)
                        .getTransportControls()
                        .stop();

                setResult(RESULT_OK);
                finish();
            }
        });

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

    private void prepareAndPlay() {
        MediaControllerCompat.getMediaController(PlayActivity.this)
                .getTransportControls()
                .prepareFromUri(
                        Uri.parse(mSelectedBookmark.getUrl()),
                        getIntent().getExtras());

        MediaControllerCompat.getMediaController(PlayActivity.this)
                .getTransportControls()
                .play();
    }

    private void setPlayPauseDrawable(int id) {
        mPlayPause.setImageDrawable(getResources().getDrawable(id));
    }

}