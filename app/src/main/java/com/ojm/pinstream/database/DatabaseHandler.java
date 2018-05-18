package com.ojm.pinstream.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;

import com.ojm.pinstream.models.Bookmark;

import java.util.ArrayList;

/**
 * Simple database handler class providing methods which perform raw
 * SQL queries to SQLite database
 */
public class DatabaseHandler extends SQLiteOpenHelper {
    // Database version number
    private static final int DATABASE_VERSION = 1;

    // Name
    private static final String DATABASE_NAME = "bookmarksManager";

    // Table name
    private static final String TABLE_BOOKMARKS = "bookmarks";

    // Key names
    private static final String KEY_ID = "id";
    private static final String KEY_TITLE = "title";
    private static final String KEY_URL = "url";
    private static final String KEY_SELECTED = "isSelected";

    /**
     * Create a new database handler instance
     * @param context the context of the required database instance
     */
    public DatabaseHandler(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    /**
     * Runs on database creation
     * @param sqLiteDatabase the object representing the database
     */
    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {
        // Execute SQL create command
        sqLiteDatabase.execSQL(
                "CREATE TABLE " + TABLE_BOOKMARKS + "(" +
                        KEY_ID + " INTEGER PRIMARY KEY, " +
                        KEY_TITLE + " VARCHAR(255), " +
                        KEY_URL + " VARCHAR(255), " +
                        KEY_SELECTED + " INTEGER)");
    }

    /**
     * Runs on database upgrade. Drops table and recreates.
     */
    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i1) {
        sqLiteDatabase.execSQL("DROP TABLE IF EXISTS " + TABLE_BOOKMARKS);
        onCreate(sqLiteDatabase);
    }

    /**
     * Add a bookmark to the database
     * @param bookmark the bookmark to be added
     */
    public void addBookmark(Bookmark bookmark) {
        // Create a values object to store bookmark information
        ContentValues values = new ContentValues();

        // Extract bookmark information into values
        values.put(KEY_TITLE, bookmark.getTitle());
        values.put(KEY_URL, bookmark.getUrl().toString());
        values.put(KEY_SELECTED, bookmark.isSelected() ? 1 : 0);

        // Write to database
        this.getWritableDatabase().insert(TABLE_BOOKMARKS, null, values);
    }

    /**
     * Retrieve all the bookmarks in the database
     * @return an ArrayList of all bookmarks in the database
     */
    public ArrayList<Bookmark> getAllBookmarks() {
        // Select all bookmarks
        Cursor cursor = this.getWritableDatabase().rawQuery(
                "SELECT * FROM " + TABLE_BOOKMARKS,
                null
        );

        ArrayList<Bookmark> bookmarkList = new ArrayList<>();

        // Use cursor to extract data from database entries back into bookmark objects
        if (cursor.moveToFirst()) {
            do {
                bookmarkList.add(new Bookmark(
                        cursor.getInt(0),
                        cursor.getString(1),
                        Uri.parse(cursor.getString(2)),
                        cursor.getInt(3) == 1)
                );
            } while (cursor.moveToNext());
        }

        cursor.close();
        return bookmarkList;
    }

    /**
     * Update a given bookmark in the database
     * @param bookmark the bookmark to be updated
     */
    public void updateBookmark(Bookmark bookmark) {
        // Create values store
        ContentValues values = new ContentValues();

        // Extract values from bookmark
        values.put(KEY_TITLE, bookmark.getTitle());
        values.put(KEY_URL, bookmark.getUrl().toString());
        values.put(KEY_SELECTED, bookmark.isSelected());

        // Update the bookmark with matching ID
        this.getWritableDatabase().update(
                TABLE_BOOKMARKS,
                values,
                KEY_ID + " = ?",
                new String[] {
                        String.valueOf(bookmark.getID())
                });
    }


    /**
     * Delete a given bookmark from the database
     * @param bookmark the bookmark to be deleted
     */
    public void deleteBookmark(Bookmark bookmark) {
        // Delete from the database the entry with a matching ID
        this.getWritableDatabase().delete(
                TABLE_BOOKMARKS,
                KEY_ID + " = ?",
                new String[] {
                        String.valueOf(bookmark.getID())
                });
    }
}
