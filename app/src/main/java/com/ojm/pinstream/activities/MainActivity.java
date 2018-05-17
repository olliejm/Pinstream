package com.ojm.pinstream.activities;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.AppCompatDelegate;
import android.support.v7.widget.Toolbar;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.AdapterView;
import android.widget.ListView;

import com.ojm.pinstream.adapters.BookmarkAdapter;
import com.ojm.pinstream.models.Bookmark;
import com.ojm.pinstream.database.DatabaseHandler;
import com.ojm.pinstream.R;

public class MainActivity extends AppCompatActivity {

    public static final int CREATE_BOOKMARK_REQUEST = 1;
    public static final int PLAY_STREAM_REQUEST = 2;

    private static final int REQUEST_AUDIO_PERMISSION = 3;

    private final DatabaseHandler dbHandler = new DatabaseHandler(this);

    private ListView listView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        configureTheme();

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setSupportActionBar((Toolbar) findViewById(R.id.toolbar));

        findViewById(R.id.fab).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent i = new Intent(getApplicationContext(), CreateActivity.class);
                startActivityForResult(i, CREATE_BOOKMARK_REQUEST);
            }
        });

        listView = findViewById(R.id.bookmark_list_view);

        listView.setAdapter(new BookmarkAdapter(this, dbHandler.getAllBookmarks()));

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Bookmark clicked = ((BookmarkAdapter) parent.getAdapter()).getItem(position);

                assert clicked != null;
                clicked.setSelected(true);
                new DatabaseHandler(getApplicationContext()).updateBookmark(clicked);
                ((BookmarkAdapter) listView.getAdapter()).notifyDataSetChanged();

                Intent i = new Intent(getApplicationContext(), PlayActivity.class);
                i.putExtra(
                        Bookmark.PARCEL,
                        ((BookmarkAdapter) parent.getAdapter()).getItem(position));

                startActivityForResult(i, PLAY_STREAM_REQUEST);

                for (Bookmark b : dbHandler.getAllBookmarks()) {
                    if (b.isSelected() && b.getID() != clicked.getID()) {
                        b.setSelected(false);
                        dbHandler.updateBookmark(b);
                    }
                }
            }
        });

        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(
                    AdapterView<?> parent, View view, int position, long id) {
                buildDialog(((BookmarkAdapter) parent.getAdapter()).getItem(position))
                        .show();
                return true;
            }
        });

        requestAudioPermissions();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            final Intent i = new Intent(getApplicationContext(), SettingsActivity.class);

            i
                    .putExtra(
                            PreferenceActivity.EXTRA_SHOW_FRAGMENT,
                            SettingsActivity.GeneralPreferenceFragment.class.getName())
                    .putExtra(
                            PreferenceActivity.EXTRA_NO_HEADERS, true);

            startActivity(i);
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            if (requestCode == CREATE_BOOKMARK_REQUEST || requestCode == PLAY_STREAM_REQUEST) {
                listView.setAdapter(
                        new BookmarkAdapter(this, dbHandler.getAllBookmarks()));
            }
        }
    }

    private void configureTheme() {
        SharedPreferences preferences
                = PreferenceManager.getDefaultSharedPreferences(this);

        if (preferences.getBoolean("night_mode",true)) {
            if (preferences.getBoolean("dark_mode", true)) {
                AppCompatDelegate.
                        setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            } else {
                AppCompatDelegate.
                        setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_AUTO);
            }
        } else if (preferences.getBoolean("night_mode", false)) {
            AppCompatDelegate
                    .setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        } else {
            AppCompatDelegate
                    .setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }
    }

    private AlertDialog buildDialog(final Bookmark bookmark) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(
                new ContextThemeWrapper(this, R.style.AlertDialog)
        );

        builder
                .setMessage(R.string.manage_bookmark_dialog)
                .setPositiveButton(R.string.edit, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        Intent i = new Intent(getApplicationContext(), CreateActivity.class);
                        i.putExtra(Bookmark.PARCEL, bookmark);
                        startActivityForResult(i, CREATE_BOOKMARK_REQUEST);
                    }
                })
                .setNegativeButton(R.string.remove, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        new DatabaseHandler(getApplicationContext())
                                .deleteBookmark(bookmark);
                        listView.setAdapter(
                                new BookmarkAdapter(
                                        getApplicationContext(), dbHandler.getAllBookmarks()));
                    }
                })
                .setNeutralButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });

        return builder.create();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[],
                                           @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_AUDIO_PERMISSION: {
                if (grantResults.length > 0 &&
                        grantResults[0] == PackageManager.PERMISSION_DENIED) {
                    finish();
                }
            }
        }
    }

    private void requestAudioPermissions() {
        if (ContextCompat.checkSelfPermission(MainActivity.this,
                Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale
                    (MainActivity.this, Manifest.permission.RECORD_AUDIO)) {
                Snackbar.make(findViewById(android.R.id.content),
                        "Recording permission required for visualiser",
                        Snackbar.LENGTH_INDEFINITE).setAction("ALLOW",
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                ActivityCompat.requestPermissions(
                                        MainActivity.this,
                                        new String[]{Manifest.permission.RECORD_AUDIO},
                                        REQUEST_AUDIO_PERMISSION);
                            }
                        }).show();
            } else {
                ActivityCompat.requestPermissions(MainActivity.this,
                        new String[]{Manifest.permission.RECORD_AUDIO},
                        REQUEST_AUDIO_PERMISSION);
            }
        }
    }
}