package com.dagu.hyperstorehelper.core;

/** One independently switchable purification rule. */
public final class Feature {

    public final String key;
    public final String title;
    public final String summary;

    public Feature(String key, String title, String summary) {
        this.key = key;
        this.title = title;
        this.summary = summary;
    }
}
