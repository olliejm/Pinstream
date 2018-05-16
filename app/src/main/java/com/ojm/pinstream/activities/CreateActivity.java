package com.ojm.pinstream.activities;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.EditText;

import com.ojm.pinstream.R;
import com.ojm.pinstream.models.Bookmark;
import com.ojm.pinstream.database.DatabaseHandler;

import java.util.Objects;

public class CreateActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create);
        setSupportActionBar((Toolbar) findViewById(R.id.toolbar));
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);

        if (getIntent().hasExtra(Bookmark.PARCEL)) {
            Bookmark bookmark = getIntent().getParcelableExtra(Bookmark.PARCEL);
            EditText title = findViewById(R.id.bookmark_add_title);
            EditText url = findViewById(R.id.bookmark_add_url);

            title.setText(bookmark.getTitle());
            url.setText(bookmark.getUrl());
        }

        findViewById(R.id.bookmark_add_confirm).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                DatabaseHandler dbHandler = new DatabaseHandler(getApplicationContext());

                if (getIntent().hasExtra(Bookmark.PARCEL)) {
                    Bookmark bookmark = getIntent().getParcelableExtra(Bookmark.PARCEL);
                    dbHandler.updateBookmark(new Bookmark(
                            bookmark.getID(),
                            ((EditText) findViewById(R.id.bookmark_add_title))
                                    .getText().toString(),
                            ((EditText) findViewById(R.id.bookmark_add_url))
                                    .getText().toString(),
                            bookmark.isSelected())
                    );
                } else {
                    dbHandler.addBookmark(
                            new Bookmark(
                                    ((EditText) findViewById(R.id.bookmark_add_title))
                                            .getText().toString(),
                                    ((EditText) findViewById(R.id.bookmark_add_url))
                                            .getText().toString()
                            )
                    );
                }

                Intent intent = new Intent();
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                setResult(RESULT_OK, intent);
                finish();
            }
        });
    }

}
