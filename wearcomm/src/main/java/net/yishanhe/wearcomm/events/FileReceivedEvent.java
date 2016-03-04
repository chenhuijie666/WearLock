package net.yishanhe.wearcomm.events;

import com.google.android.gms.wearable.Channel;

/**
 * Created by syi on 2/10/16.
 */
public class FileReceivedEvent {

    private final Channel channel;

    public FileReceivedEvent(Channel channel) {
        this.channel = channel;
    }

    public Channel getChannel() {
        return channel;
    }
}
