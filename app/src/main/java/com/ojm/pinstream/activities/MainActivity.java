package com.ojm.pinstream.activities;

import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.AppCompatDelegate;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.AdapterView;
import android.widget.ListView;

import com.ojm.pinstream.adapters.BookmarkAdapter;
import com.ojm.pinstream.models.Bookmark;
import com.ojm.pinstream.database.DatabaseHandler;
import com.ojm.pinstream.R;
import com.ojm.pinstream.services.StreamingService;

public class MainActivity extends AppCompatActivity {

    private final DatabaseHandler db = new DatabaseHandler(this);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //TODO config day/night vs day-only prefs
        if (PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean("dark_theme",true)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setSupportActionBar((Toolbar) findViewById(R.id.toolbar));

        findViewById(R.id.fab).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent i = new Intent(getApplicationContext(), CreateActivity.class);
                startActivity(i);
            }
        });

        final BookmarkAdapter adapter = new BookmarkAdapter(this, db.getAllBookmarks());
        final ListView listView = findViewById(R.id.bookmark_list_view);

        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Bookmark bookmark = (Bookmark) adapter.getItem(position);

                Intent s = new Intent(getApplicationContext(), StreamingService.class);
                s.putExtra(
                        StreamingService.STREAM_TITLE,
                        bookmark.getTitle()
                );

                s.putExtra(
                        StreamingService.STREAM_URI,
                        bookmark.getUrl()
                );

                s.setAction(StreamingService.ACTION_CMD);

                s.putExtra(
                        StreamingService.CMD_NAME,
                        StreamingService.CMD_PLAY
                );

                startService(s);

                Intent i = new Intent(getApplicationContext(), PlayActivity.class);
                startActivity(i);
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            Intent i = new Intent(getApplicationContext(), SettingsActivity.class);
            i.putExtra(
                    PreferenceActivity.EXTRA_SHOW_FRAGMENT,
                    SettingsActivity.GeneralPreferenceFragment.class.getName()
            );

            i.putExtra(PreferenceActivity.EXTRA_NO_HEADERS, true);
            startActivity(i);
        }

        return super.onOptionsItemSelected(item);
    }
}
