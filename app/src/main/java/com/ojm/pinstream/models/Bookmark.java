package com.ojm.pinstream.models;

public class Bookmark {
    private int id;
    private String title;
    private String url;

    public Bookmark(String title, String url) {
        this.title = title;
        this.url = url;
    }

    public Bookmark(int id, String title, String url) {
        this.id = id;
        this.title = title;
        this.url = url;
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
}
