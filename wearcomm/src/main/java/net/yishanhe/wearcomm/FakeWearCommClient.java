package net.yishanhe.wearcomm;

import android.content.Context;
import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Wearable;

import net.yishanhe.wearcomm.events.ReceiveMessageEvent;
import net.yishanhe.wearcomm.events.SendFileEvent;
import net.yishanhe.wearcomm.events.SendMessageEvent;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

/**
 * Created by syi on 3/8/16.
 */
public class FakeWearCommClient {

    private final static String TAG = "FakeWearComm";
    private Context context;
    private static FakeWearCommClient instance;
    private int identifier; // server or client;
    private static final int SERVER = 0;
    private static final int CLIENT = 1;
    PhoneServer server = null;
    PhoneClient client = null;

    public static synchronized FakeWearCommClient getInstance(Context context, String IP) {
        if (instance == null) {
            instance = new FakeWearCommClient(context, IP);
        }
        return  instance;
    }

    private FakeWearCommClient(Context context, String IP) {
        this.context = context;
        this.identifier = CLIENT;
        client = new PhoneClient(IP);

    }

    public static synchronized FakeWearCommClient getInstance(Context context) {
        if (instance == null) {
            instance = new FakeWearCommClient(context);
        }
        return  instance;
    }

    private FakeWearCommClient(Context context) {
        this.context = context;
        this.identifier = SERVER;
        server = new PhoneServer();
    }


    public void connect() {
        // connect

        if (identifier == CLIENT) {
            if (client != null) {
                Log.d(TAG, "connect ...");
                client.connect();
            }
        }
        EventBus.getDefault().register(this);

    }

    public void disconnect() {
        // disconnect

        if (identifier == CLIENT) {
            if (client != null) {
                Log.d(TAG, "disconnect");
                client.disconnect();
            }
        }
        EventBus.getDefault().unregister(this);
    }

    @Subscribe
    public void sendMessage(SendMessageEvent event){
        Log.d(TAG, "sendMessage: received sending message request.");
        if (identifier == CLIENT) {
            if (client != null) {
                client.sendMessage(event);
            } else {
                Log.d(TAG, "sendMessage: not send due to null client");
            }
        }

        if (identifier == SERVER) {
            if (server != null) {
                server.sendMessage(event);
            } else {
                Log.d(TAG, "sendMessage: not send due to null server");
            }
        }

    }

    @Subscribe
    public void sendFile(SendFileEvent event) {
        if (identifier == SERVER) {
            if (server != null) {
                Log.d(TAG, "sendFile: received sending file request.");
                server.sendFile(event);
            } else {
                Log.d(TAG, "sendMessage: not send due to null server");
            }
        }
    }



}
