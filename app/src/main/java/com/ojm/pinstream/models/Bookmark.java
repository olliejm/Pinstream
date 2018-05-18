package com.ojm.pinstream.models;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * Model class to represent a bookmark (saved stream), implements parcelable
 * interface as to allow Bookmark objects to be attached to bundles/intents
 */
public class Bookmark implements Parcelable {
    // Static identifier tags for bundles or intents
    public static final String PARCEL = "BOOKMARK_PARCEL";
    public static final String ID = "BOOKMARK_ID";

    /**
     * Static parcelable creator, returns Bookmark object from a parceled bookmark
     */
    public static final Parcelable.Creator CREATOR = new Parcelable.Creator() {
        public Bookmark createFromParcel(Parcel in) {
            return new Bookmark(in);
        }

        public Bookmark[] newArray(int size) {
            return new Bookmark[size];
        }
    };

    // Private field variables
    private int id;
    private String title;
    private Uri url;
    private boolean isSelected;

    /**
     * Construct a bookmark from a parcel
     * @param in the parcel to be converted to bookmark
     */
    private Bookmark(Parcel in) {
        String[] data = new String[4];
        in.readStringArray(data);

        this.id = Integer.parseInt(data[0]);
        this.title = data[1];
        this.url = Uri.parse(data[2]);
        this.isSelected = Boolean.parseBoolean(data[3]);
    }

    /**
     * Create a new bookmark without yet knowing ID or selected status
     * @param title the title of the bookmark
     * @param url the stream's URL
     */
    public Bookmark(String title, Uri url) {
        this.title = title;
        this.url = url;
    }

    /**
     * Create a new bookmark with all fields set
     * @param id the bookmark's database ID
     * @param title the title of the bookmark
     * @param url the stream's url
     * @param isSelected whether this bookmark is currently selected (playing)
     */
    public Bookmark(int id, String title, Uri url, boolean isSelected) {
        this.id = id;
        this.title = title;
        this.url = url;
        this.isSelected = isSelected;
    }

    // Parcelable function, needs to be implemented but is not used
    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Writes the bookmark to a given a parcel
     * @param dest the parcel the bookmark should be written to
     */
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeStringArray(new String[] {
                String.valueOf(this.id),
                this.title,
                this.url.toString(),
                Boolean.toString(this.isSelected)
        });
    }

    /**
     * Get the bookmark's ID
     * @return the bookmark's ID, as an Int
     */
    public int getID() {
        return id;
    }

    /**
     * Get the bookmark's title
     * @return to bookmark's title, as a String
     */
    public String getTitle() {
        return title;
    }

    /**
     * Get the bookmark's URL
     * @return the bookmark's URL, as a Uri
     */
    public Uri getUrl() {
        return url;
    }

    /**
     * Get the selected status of the bookmark
     * @return return a boolean indicating if the bookmark is selected
     */
    public boolean isSelected() {
        return isSelected;
    }

    /**
     * Set the selected status of the bookmark
     * @param isSelected a boolean indicating whether you want to select
     *                   or deselect the bookmark
     */
    public void setSelected(boolean isSelected) {
        this.isSelected = isSelected;
    }

}
