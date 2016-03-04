package net.yishanhe.wearlock.events;

/**
 * Created by syi on 2/15/16.
 */
public class StatusMessageEvent {

    private String tag;
    private String message;
    private String path;

    public StatusMessageEvent(String tag, String message) {
        this.tag = tag;
        this.message = message;
        this.path= "";
    }

    public StatusMessageEvent(String tag, String message, String path) {
        this.tag = tag;
        this.message = message;
        this.path = path;
    }

    public String getMessage() {
        return message;
    }

    public String getTag() {
        return tag;
    }

    public String getPath() {
        return path;
    }
}

