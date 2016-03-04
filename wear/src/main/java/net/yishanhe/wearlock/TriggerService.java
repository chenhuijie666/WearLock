package net.yishanhe.wearlock;

import android.content.Intent;
import android.provider.Settings;
import android.util.Log;

import com.google.android.gms.wearable.Channel;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

import net.yishanhe.wearcomm.events.ReceiveMessageEvent;

import org.greenrobot.eventbus.EventBus;


/**
 * Created by syi on 9/22/15.
 * this service is used to active
 * the main activity of this app
 * when the phone side app is on.
 */
public class TriggerService extends WearableListenerService {

    private static final String TAG = "WearMessageService";

    private static final String START_ACTIVITY = "/start_activity";



    @Override
    public void onCreate() {
        super.onCreate();
//        EventBus.getDefault().register(this);
    }

    @Override
    public void onDestroy() {
//        EventBus.getDefault().unregister(this);
        super.onDestroy();
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        if (messageEvent.getPath().equalsIgnoreCase(START_ACTIVITY)) {
            Log.d(TAG, "onMessageReceived: log the time: "+ System.currentTimeMillis());
            Intent intent = new Intent(this,MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT|Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }
    }



}
