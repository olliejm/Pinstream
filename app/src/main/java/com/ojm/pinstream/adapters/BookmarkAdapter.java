package com.ojm.pinstream.adapters;

import android.content.Context;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;

import com.ojm.pinstream.models.Bookmark;
import com.ojm.pinstream.R;

import java.util.ArrayList;

/**
 * This is a custom adapter class to allow the conversion of a list of bookmarks
 * into a list of CheckedTextView object for the ListView display
 */
public class BookmarkAdapter extends ArrayAdapter<Bookmark> {

    /**
     * Create a new bookmark adapter
     * @param context context the adapter belongs to
     * @param items the items the adapter should manage
     */
    public BookmarkAdapter(Context context, ArrayList<Bookmark> items) {
        super(context, 0, items);
    }

    /**
     * Get an individual view from the adapter of a given position
     * @param position position of the item to get the view from
     * @return the completed view
     */
    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup container) {
        // Only inflate if old view is null
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(
                    R.layout.list_item_bookmark,
                    container,
                    false
            );
        }

        // Find the checked text view and bookmark
        final CheckedTextView listItem = convertView.findViewById(R.id.bookmark_list_item);
        final Bookmark bookmark = getItem(position);

        // Set the checked text view values from the bookmark
        assert bookmark != null;
        listItem.setText(bookmark.getTitle());
        listItem.setChecked(bookmark.isSelected());

        // Return the convert view
        return convertView;
    }

}
