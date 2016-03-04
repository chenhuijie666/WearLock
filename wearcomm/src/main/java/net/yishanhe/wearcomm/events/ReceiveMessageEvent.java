package net.yishanhe.wearcomm.events;

import com.google.android.gms.wearable.MessageEvent;

/**
 * Created by syi on 2/10/16.
 */
public class ReceiveMessageEvent {

    private String path;
    private byte[] data;

    public ReceiveMessageEvent(String path, byte[] data) {
        this.path = path;
        this.data = data;
    }

    public String getPath() {
        return path;
    }

    public byte[] getData() {
        return data;
    }
}
