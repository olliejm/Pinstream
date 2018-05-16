package com.ojm.pinstream.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.ojm.pinstream.models.Bookmark;

import java.util.ArrayList;

public class DatabaseHandler extends SQLiteOpenHelper {
    private static final int DATABASE_VERSION = 1;

    private static final String DATABASE_NAME = "bookmarksManager";

    private static final String TABLE_BOOKMARKS = "bookmarks";

    private static final String KEY_ID = "id";
    private static final String KEY_TITLE = "title";
    private static final String KEY_URL = "url";
    private static final String KEY_SELECTED = "isSelected";

    public DatabaseHandler(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {
        sqLiteDatabase.execSQL(
                "CREATE TABLE " + TABLE_BOOKMARKS + "(" +
                        KEY_ID + " INTEGER PRIMARY KEY, " +
                        KEY_TITLE + " VARCHAR(255), " +
                        KEY_URL + " VARCHAR(255), " +
                        KEY_SELECTED + " INTEGER)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i1) {
        sqLiteDatabase.execSQL("DROP TABLE IF EXISTS " + TABLE_BOOKMARKS);
        onCreate(sqLiteDatabase);
    }

    public void addBookmark(Bookmark bookmark) {
        ContentValues values = new ContentValues();
        values.put(KEY_TITLE, bookmark.getTitle());
        values.put(KEY_URL, bookmark.getUrl());
        values.put(KEY_SELECTED, bookmark.isSelected() ? 1 : 0);

        this.getWritableDatabase().insert(TABLE_BOOKMARKS, null, values);
    }

    public ArrayList<Bookmark> getAllBookmarks() {
        Cursor cursor = this.getWritableDatabase().rawQuery(
                "SELECT * FROM " + TABLE_BOOKMARKS,
                null
        );

        ArrayList<Bookmark> bookmarkList = new ArrayList<>();
        if (cursor.moveToFirst()) {
            do {
                bookmarkList.add(new Bookmark(
                        cursor.getInt(0),
                        cursor.getString(1),
                        cursor.getString(2),
                        cursor.getInt(3) == 1)
                );
            } while (cursor.moveToNext());
        }

        cursor.close();
        return bookmarkList;
    }

    public void updateBookmark(Bookmark bookmark) {
        ContentValues values = new ContentValues();
        values.put(KEY_TITLE, bookmark.getTitle());
        values.put(KEY_URL, bookmark.getUrl());
        values.put(KEY_SELECTED, bookmark.isSelected());

        this.getWritableDatabase().update(
                TABLE_BOOKMARKS,
                values,
                KEY_ID + " = ?",
                new String[] {
                        String.valueOf(bookmark.getID())
                });
    }

    public void deleteBookmark(Bookmark bookmark) {
        this.getWritableDatabase().delete(
                TABLE_BOOKMARKS,
                KEY_ID + " = ?",
                new String[] {
                        String.valueOf(bookmark.getID())
                });
    }
}
