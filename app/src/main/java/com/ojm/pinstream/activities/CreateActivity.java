package com.ojm.pinstream.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.EditText;

import com.ojm.pinstream.R;
import com.ojm.pinstream.models.Bookmark;
import com.ojm.pinstream.database.DatabaseHandler;

import java.util.Objects;

/**
 * The activity to allow a user to create or edit bookmarks
 */
public class CreateActivity extends AppCompatActivity {

    /**
     * Method runs on activity construction
     * @param savedInstanceState saved instance state
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create);
        setSupportActionBar((Toolbar) findViewById(R.id.toolbar));
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);

        // If the intent has a bookmark attached, fill the details in the text fields (editing)
        if (getIntent().hasExtra(Bookmark.PARCEL)) {
            Bookmark bookmark = getIntent().getParcelableExtra(Bookmark.PARCEL);
            EditText title = findViewById(R.id.bookmark_add_title);
            EditText url = findViewById(R.id.bookmark_add_url);

            title.setText(bookmark.getTitle());
            url.setText(bookmark.getUrl().toString());
        }

        // Set click listener for confirmation check mark
        findViewById(R.id.bookmark_add_confirm).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Get new database handler
                DatabaseHandler dbHandler = new DatabaseHandler(getApplicationContext());

                // If we're editing
                if (getIntent().hasExtra(Bookmark.PARCEL)) {
                    // Update the bookmark with the current field values
                    Bookmark bookmark = getIntent().getParcelableExtra(Bookmark.PARCEL);
                    dbHandler.updateBookmark(new Bookmark(
                            bookmark.getID(),
                            ((EditText) findViewById(R.id.bookmark_add_title))
                                    .getText().toString(),
                            Uri.parse(((EditText) findViewById(R.id.bookmark_add_url))
                                    .getText().toString()),
                            bookmark.isSelected())
                    );
                }

                // Else just save the field values to a new bookmark in the database
                else {
                    dbHandler.addBookmark(
                            new Bookmark(
                                    ((EditText) findViewById(R.id.bookmark_add_title))
                                            .getText().toString(),
                                    Uri.parse(((EditText) findViewById(R.id.bookmark_add_url))
                                            .getText().toString())
                            )
                    );
                }

                dbHandler.close();

                // Set result and finish
                Intent intent = new Intent();
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                setResult(RESULT_OK, intent);
                finish();
            }
        });
    }

}
