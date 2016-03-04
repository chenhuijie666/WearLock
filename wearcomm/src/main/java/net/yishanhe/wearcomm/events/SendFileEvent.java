package net.yishanhe.wearcomm.events;

import android.net.Uri;

/**
 * Created by syi on 2/10/16.
 */
public class SendFileEvent {
    private final String path;
    private final Uri uri;

    public SendFileEvent(String path, Uri uri) {
        this.path = path;
        this.uri = uri;
    }

    public String getPath() {
        return path;
    }

    public Uri getUri() {
        return uri;
    }
}
