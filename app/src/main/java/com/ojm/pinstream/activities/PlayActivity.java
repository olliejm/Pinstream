package com.ojm.pinstream.activities;

import android.content.ComponentName;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import com.ojm.pinstream.R;
import com.ojm.pinstream.services.StreamingService;

import java.util.Objects;

public class PlayActivity extends AppCompatActivity {

    private MediaBrowserCompat mMediaBrowser;
    private ImageView mPlayPause;

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

                    MediaControllerCompat.setMediaController(
                            PlayActivity.this, mediaController);

                    MediaControllerCompat.getMediaController(PlayActivity.this)
                            .registerCallback(mControllerCallback);

                    MediaControllerCompat.getMediaController(PlayActivity.this)
                            .getTransportControls()
                            .prepareFromUri(
                                    Uri.parse(
                                            getIntent()
                                                    .getStringExtra(StreamingService.STREAM_URI)),
                                    getIntent().getExtras());

                    MediaControllerCompat.getMediaController(PlayActivity.this)
                            .getTransportControls().play();

                    buildTransportControls();
                }

                @Override
                public void onConnectionSuspended() {
                    // Automatically reconnects
                }

                @Override
                public void onConnectionFailed() {
                    Log.println(
                            Log.ERROR, "Pinstream", "MediaBrowser connection failed");
                }
            };

    private MediaControllerCompat.Callback mControllerCallback =
            new MediaControllerCompat.Callback() {
                @Override
                public void onMetadataChanged(MediaMetadataCompat metadata) {
                    // No metadata functionality required
                }

                @Override
                public void onPlaybackStateChanged(PlaybackStateCompat state) {
                    assert state != null;
                    switch (state.getState()) {
                        case PlaybackStateCompat.STATE_STOPPED:
                            finish();
                            break;
                        case PlaybackStateCompat.STATE_PAUSED:
                            mPlayPause.setImageDrawable(
                                    getResources()
                                            .getDrawable(R.drawable.ic_play_arrow_black_24px));
                            break;
                        case PlaybackStateCompat.STATE_PLAYING:
                            mPlayPause.setImageDrawable(
                                    getResources()
                                            .getDrawable(R.drawable.ic_pause_black_24px));
                            break;
                    }
                }
            };


    private void buildTransportControls() {
        mPlayPause = findViewById(R.id.play_pause);

        mPlayPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int state = MediaControllerCompat.getMediaController(PlayActivity.this)
                        .getPlaybackState().getState();

                if (state == PlaybackStateCompat.STATE_PLAYING) {
                    MediaControllerCompat.getMediaController(PlayActivity.this)
                            .getTransportControls().pause();
                } else {
                    MediaControllerCompat.getMediaController(PlayActivity.this)
                            .getTransportControls().play();
                }
            }
        });

        ImageView mStop = findViewById(R.id.stop);

        mStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MediaControllerCompat.getMediaController(PlayActivity.this)
                        .getTransportControls().stop();
                finish();
            }
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_play);
        setSupportActionBar((Toolbar) findViewById(R.id.toolbar));
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);

        mMediaBrowser = new MediaBrowserCompat(this,
                new ComponentName(this, StreamingService.class),
                mConnectionCallback,
                null);
    }

    @Override
    public void onStart() {
        super.onStart();
        mMediaBrowser.connect();
    }

    @Override
    public void onResume() {
        super.onResume();
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
    }

    @Override
    public void onStop() {
        super.onStop();

        mMediaBrowser.disconnect();
        MediaControllerCompat.getMediaController(PlayActivity.this)
                .unregisterCallback(mControllerCallback);
    }

}
