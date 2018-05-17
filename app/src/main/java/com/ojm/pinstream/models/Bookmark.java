package com.ojm.pinstream.models;

import android.os.Parcel;
import android.os.Parcelable;

public class Bookmark implements Parcelable {
    public static final String PARCEL = "BOOKMARK_PARCEL";
    public static final String ID = "BOOKMARK_ID";

    public static final Parcelable.Creator CREATOR = new Parcelable.Creator() {
        public Bookmark createFromParcel(Parcel in) {
            return new Bookmark(in);
        }

        public Bookmark[] newArray(int size) {
            return new Bookmark[size];
        }
    };

    private int id;
    private String title;
    private String url;
    private boolean isSelected;

    private Bookmark(Parcel in) {
        String[] data = new String[4];
        in.readStringArray(data);

        this.id = Integer.parseInt(data[0]);
        this.title = data[1];
        this.url = data[2];
        this.isSelected = Boolean.parseBoolean(data[3]);
    }

    public Bookmark(String title, String url) {
        this.title = title;
        this.url = url;
    }

    public Bookmark(int id, String title, String url, boolean isSelected) {
        this.id = id;
        this.title = title;
        this.url = url;
        this.isSelected = isSelected;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeStringArray(new String[] {
                String.valueOf(this.id),
                this.title,
                this.url,
                Boolean.toString(this.isSelected)
        });
    }

    public int getID() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getUrl() {
        return url;
    }

    public boolean isSelected() {
        return isSelected;
    }

    public void setSelected(boolean isSelected) {
        this.isSelected = isSelected;
    }

}
