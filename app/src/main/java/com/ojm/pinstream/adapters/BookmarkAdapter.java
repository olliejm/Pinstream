package com.ojm.pinstream.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.ojm.pinstream.models.Bookmark;
import com.ojm.pinstream.R;

import java.util.ArrayList;

public class BookmarkAdapter extends BaseAdapter {
    private ArrayList<Bookmark> mDataSource;
    private LayoutInflater mInflater;

    public BookmarkAdapter(Context context, ArrayList<Bookmark> items) {
        mInflater = (LayoutInflater) context.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE
        );

        mDataSource = items;
    }

    @Override
    public int getCount() {
        return mDataSource.size();
    }

    @Override
    public Object getItem(int position) {
        return mDataSource.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup container) {
        if (convertView == null) {
            convertView = mInflater.inflate(
                    R.layout.list_item_bookmark,
                    container,
                    false
            );
        }

        ((TextView) convertView.findViewById(R.id.bookmark_list_title))
                .setText(((Bookmark) getItem(position)).getTitle());

        return convertView;
    }

}
