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

public class BookmarkAdapter extends ArrayAdapter<Bookmark> {

    public BookmarkAdapter(Context context, ArrayList<Bookmark> items) {
        super(context, 0, items);
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup container) {
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(
                    R.layout.list_item_bookmark,
                    container,
                    false
            );
        }

        final CheckedTextView listItem = convertView.findViewById(R.id.bookmark_list_item);
        final Bookmark bookmark = getItem(position);

        assert bookmark != null;
        listItem.setText(bookmark.getTitle());
        listItem.setChecked(bookmark.isSelected());

        return convertView;
    }

}
