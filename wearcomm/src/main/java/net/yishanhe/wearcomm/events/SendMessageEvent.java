package net.yishanhe.wearcomm.events;


/**
 * Created by syi on 2/10/16.
 */
public class SendMessageEvent {
    private final String path;
    private final byte[] data;

    public SendMessageEvent(String path, byte[] data) {
        this.path = path;
        this.data = data;
    }

    public SendMessageEvent(String path) {
        this.path = path;
        this.data = "null".getBytes();
    }

    public String getPath() {
        return path;
    }

    public byte[] getData() {
        return data;
    }
}
