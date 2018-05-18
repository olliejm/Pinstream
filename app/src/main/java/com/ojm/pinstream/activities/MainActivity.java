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

/**
 * Main activity class, this class displays the list of bookmarks for selection
 * and the floating action button to add a new bookmark
 */
public class MainActivity extends AppCompatActivity {

    // Static identifiers for requests for activity results
    public static final int CREATE_BOOKMARK_REQUEST = 1;
    public static final int PLAY_STREAM_REQUEST = 2;

    // Static identifier for requesting audio permission
    private static final int REQUEST_AUDIO_PERMISSION = 3;

    // Database handler
    private final DatabaseHandler dbHandler = new DatabaseHandler(this);

    // List view to display bookmarks
    private ListView listView;

    /**
     * Method runs on creation of the activity
     * @param savedInstanceState saved instance state
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Must configure theme first for it to work
        configureTheme();

        // General setup
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setSupportActionBar((Toolbar) findViewById(R.id.toolbar));

        // Set click listener for floating action button to launch create activity
        findViewById(R.id.fab).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent i = new Intent(getApplicationContext(), CreateActivity.class);
                startActivityForResult(i, CREATE_BOOKMARK_REQUEST);
            }
        });

        // Assign list view to XML id
        listView = findViewById(R.id.bookmark_list_view);

        // Set adapter to new bookmark adapter populated from database
        listView.setAdapter(new BookmarkAdapter(this, dbHandler.getAllBookmarks()));

        // Set click listener for each item in the list view
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                // Identify the clicked bookmark
                Bookmark clicked = ((BookmarkAdapter) parent.getAdapter()).getItem(position);

                // Set the bookmark as selected and update database
                assert clicked != null;
                clicked.setSelected(true);
                new DatabaseHandler(getApplicationContext()).updateBookmark(clicked);
                ((BookmarkAdapter) listView.getAdapter()).notifyDataSetChanged();

                // Launch intent for play activity with bookmark attached as parcel
                Intent i = new Intent(getApplicationContext(), PlayActivity.class);
                i.putExtra(
                        Bookmark.PARCEL,
                        ((BookmarkAdapter) parent.getAdapter()).getItem(position));

                // Start activity and await result
                startActivityForResult(i, PLAY_STREAM_REQUEST);

                // Set any previously selected bookmarks as unselected
                for (Bookmark b : dbHandler.getAllBookmarks()) {
                    if (b.isSelected() && b.getID() != clicked.getID()) {
                        b.setSelected(false);
                        dbHandler.updateBookmark(b);
                    }
                }
            }
        });

        // Set long click listener for list items
        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(
                    AdapterView<?> parent, View view, int position, long id) {
                // On long click display dialog giving edit or delete options
                buildDialog(((BookmarkAdapter) parent.getAdapter()).getItem(position))
                        .show();
                return true;
            }
        });

        // Request audio permissions required for visualiser
        requestAudioPermissions();
    }

    /**
     * Run on activity destruction, closes database connection to avoid leak
     */
    @Override
    public void onDestroy() {
        dbHandler.close();
        super.onDestroy();
    }

    /**
     * Creates settings menu, auto-generated
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    /**
     * Launches settings activity while skipping header view
     */
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

    /**
     * Called on activity result being sent
     * @param requestCode request code sent to activity
     * @param resultCode result returned from activity
     * @param data any data passed between
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            // If result OK, refresh list view to reflect database
            if (requestCode == CREATE_BOOKMARK_REQUEST || requestCode == PLAY_STREAM_REQUEST) {
                listView.setAdapter(
                        new BookmarkAdapter(this, dbHandler.getAllBookmarks()));
            }
        }
    }

    /**
     * Run on result of a permissions request
     * @param requestCode the permission request code
     * @param permissions the permissions requested
     * @param grantResults the results of the request
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[],
                                           @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_AUDIO_PERMISSION: {
                // If permission denied, simply quit
                if (grantResults.length > 0 &&
                        grantResults[0] == PackageManager.PERMISSION_DENIED) {
                    finish();
                }
            }
        }
    }

    /**
     * Build a dialog to offer options on a given bookmark
     * @param bookmark the bookmark to display the dialog for
     * @return a constructed AlertDialog
     */
    private AlertDialog buildDialog(final Bookmark bookmark) {
        // Retrieve builder
        final AlertDialog.Builder builder = new AlertDialog.Builder(
                new ContextThemeWrapper(this, R.style.AlertDialog)
        );

        builder
                // Set dialog text
                .setMessage(R.string.manage_bookmark_dialog)
                // Set edit button
                .setPositiveButton(R.string.edit, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        // Launch create activity with given bookmark to edit
                        Intent i = new Intent(getApplicationContext(), CreateActivity.class);
                        i.putExtra(Bookmark.PARCEL, bookmark);
                        startActivityForResult(i, CREATE_BOOKMARK_REQUEST);
                    }
                })
                // Set remove button
                .setNegativeButton(R.string.remove, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        // Delete bookmark from database and refresh list view
                        new DatabaseHandler(getApplicationContext())
                                .deleteBookmark(bookmark);
                        listView.setAdapter(
                                new BookmarkAdapter(
                                        getApplicationContext(), dbHandler.getAllBookmarks()));
                    }
                })
                // Set cancel button, closes dialog
                .setNeutralButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });

        // Build
        return builder.create();
    }

    /**
     * Configure application theme
     */
    private void configureTheme() {
        // Get application preferences
        SharedPreferences preferences
                = PreferenceManager.getDefaultSharedPreferences(this);

        // If night mode is on
        if (preferences.getBoolean("night_mode",true)) {
            // ... and dark mode is permanent
            if (preferences.getBoolean("dark_mode", true)) {
                // Always use dark mode
                AppCompatDelegate.
                        setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            }

            // Else switch theme depending on time of day (regular night mode)
            else {
                AppCompatDelegate.
                        setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_AUTO);
            }
        }

        // If night mode is false, always use light mode
        else if (preferences.getBoolean("night_mode", false)) {
            AppCompatDelegate
                    .setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }

        // If for some reason none were triggered, follow system theme
        else {
            AppCompatDelegate
                    .setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }
    }

    /**
     * Request audio permissions required for visualiser
     */
    private void requestAudioPermissions() {
        // Check if we have the permission already
        if (ContextCompat.checkSelfPermission(MainActivity.this,
                Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            // If not, check if we should show the dialog
            if (ActivityCompat.shouldShowRequestPermissionRationale
                    (MainActivity.this, Manifest.permission.RECORD_AUDIO)) {
                // Make and show permissions request snackbar
                Snackbar.make(findViewById(android.R.id.content),
                        "Recording permission required for visualiser",
                        Snackbar.LENGTH_INDEFINITE).setAction("ALLOW",
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                // Send request
                                ActivityCompat.requestPermissions(
                                        MainActivity.this,
                                        new String[]{Manifest.permission.RECORD_AUDIO},
                                        REQUEST_AUDIO_PERMISSION);
                            }
                        }).show();
            }

            // Else just try the request
            else {
                ActivityCompat.requestPermissions(MainActivity.this,
                        new String[]{Manifest.permission.RECORD_AUDIO},
                        REQUEST_AUDIO_PERMISSION);
            }
        }
    }
}