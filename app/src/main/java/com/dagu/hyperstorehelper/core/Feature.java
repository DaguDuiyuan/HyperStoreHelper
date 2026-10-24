package com.dagu.hyperstorehelper.core;

/** One independently switchable purification rule. */
public final class Feature {

    public final String key;
    public final String title;
    public final String summary;
    public final boolean defaultEnabled;

    public Feature(String key, String title, String summary) {
        this(key, title, summary, true);
    }

    public Feature(String key, String title, String summary, boolean defaultEnabled) {
        this.key = key;
        this.title = title;
        this.summary = summary;
        this.defaultEnabled = defaultEnabled;
    }
}
