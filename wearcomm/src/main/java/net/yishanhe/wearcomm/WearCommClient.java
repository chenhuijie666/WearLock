package net.yishanhe.wearcomm;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.Channel;
import com.google.android.gms.wearable.ChannelApi;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

import net.yishanhe.wearcomm.events.FileReceivedEvent;
import net.yishanhe.wearcomm.events.FileSentEvent;
import net.yishanhe.wearcomm.events.ChannelOpenedEvent;
import net.yishanhe.wearcomm.events.ReceiveMessageEvent;
import net.yishanhe.wearcomm.events.SendFileEvent;
import net.yishanhe.wearcomm.events.SendMessageEvent;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.HashSet;

/**
 * Created by syi on 2/10/16.
 */
public class WearCommClient implements GoogleApiClient.OnConnectionFailedListener,
GoogleApiClient.ConnectionCallbacks, MessageApi.MessageListener, DataApi.DataListener, ChannelApi.ChannelListener {

    private static final String TAG = "WearCommClient";

    private static WearCommClient instance = null;

    private GoogleApiClient googleApiClient = null;

    private Context context;

    public static synchronized WearCommClient getInstance(Context context) {
        if (instance == null) {
            instance = new WearCommClient(context);
        }
        return  instance;
    }

    public static synchronized WearCommClient getInstance(Context context, GoogleApiClient.ConnectionCallbacks connectionCallbacks) {
        if (instance == null) {
            instance = new WearCommClient(context, connectionCallbacks);
        }
        return  instance;
    }

    private WearCommClient(Context context) {
        this.context = context;
        this.googleApiClient = new GoogleApiClient.Builder(context)
                .addOnConnectionFailedListener(this)
                .addConnectionCallbacks(this)
                .addApi(Wearable.API)
                .build();
    }

    private WearCommClient(Context context, GoogleApiClient.ConnectionCallbacks connectionCallbacks) {
        this.context = context;
        this.googleApiClient = new GoogleApiClient.Builder(context)
                .addOnConnectionFailedListener(this)
                .addConnectionCallbacks(connectionCallbacks)
                .addConnectionCallbacks(this)
                .addApi(Wearable.API)
                .build();
    }

    public void connect() {
        Log.d(TAG, "connect ...");
        EventBus.getDefault().register(this);
        googleApiClient.connect();
    }


    public void disconnect() {
        Log.d(TAG, "disconnect");
        Wearable.DataApi.removeListener(googleApiClient, this);
        Wearable.MessageApi.removeListener(googleApiClient, this);
        Wearable.ChannelApi.removeListener(googleApiClient, this);
        if (googleApiClient!=null){
            googleApiClient.disconnect();
        }
        EventBus.getDefault().unregister(this);
    }

    // GoogleApiClient call backs

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Log.d(TAG, "onConnected.");
        Wearable.DataApi.addListener(googleApiClient, this);
        Wearable.MessageApi.addListener(googleApiClient, this);
        Wearable.ChannelApi.addListener(googleApiClient,this);
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d(TAG, "onConnectionSuspended: "+i);
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.d(TAG, "onConnectionFailed: error code "+connectionResult.getErrorCode());
        Toast.makeText(context, "Cannot start the google api client.", Toast.LENGTH_SHORT).show();
    }


    // Message
    // Send Message
    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void sendMessage(SendMessageEvent event){
        Log.d(TAG, "sendMessage: received sending request.");

        NodeApi.GetConnectedNodesResult nodes = Wearable.NodeApi.getConnectedNodes(googleApiClient).await();
//        NodeApi.GetConnectedNodesResult nodes = Wearable.NodeApi.getConnectedNodes(googleApiClient).await();
        for (Node node:nodes.getNodes()){
            Log.d(TAG, "send message to Node: name:"+node.getDisplayName()+" id:"+node.getId()+" nearby:"+node.isNearby());
            MessageApi.SendMessageResult result =
                    Wearable.MessageApi.sendMessage(googleApiClient, node.getId(), event.getPath(), event.getData()).await();
            if (!result.getStatus().isSuccess()){
                Log.d(TAG, "sendMessage: failed "+node.getId()+" "+result.getStatus().toString());
            }
        }
    }

    // Receive Message
    // forwarded to event buss
    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        EventBus.getDefault().post(new ReceiveMessageEvent(messageEvent.getPath(), messageEvent.getData()));
    }


    // DataApi
    // @TODO: future work to support DataApi

    @Override
    public void onDataChanged(DataEventBuffer dataEventBuffer) {
        Log.d(TAG, "onDataChanged: not supported currently.");
    }

    // ChannelApi
    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void sendChannel(SendFileEvent event){

        Log.d(TAG, "sendChannel: received sending request.");
        NodeApi.GetConnectedNodesResult nodes = Wearable.NodeApi.getConnectedNodes(googleApiClient).await();
        for (Node node:nodes.getNodes()){
            // open channel and send the file.
            Log.d(TAG, "send file to Node: name:"+node.getDisplayName()+" id:"+node.getId()+" nearby:"+node.isNearby());
            ChannelApi.OpenChannelResult result =
                    Wearable.ChannelApi.openChannel(googleApiClient, node.getId(), event.getPath()).await();
            if (result.getStatus().isSuccess()){
                Channel channel = result.getChannel();
                channel.sendFile(googleApiClient, event.getUri());
                // should not be closed after calling this method.
            }
        }
    }


    @Override
    public void onChannelOpened(Channel channel) {
        // called when a new channel is opened by a remote node
        Log.d(TAG, "onChannelOpened: NodeID:"+channel.getNodeId());
        EventBus.getDefault().post(new ChannelOpenedEvent(channel));
        // User needs to handle the file receiving.
    }

    @Override
    public void onChannelClosed(Channel channel, int i, int i1) {
        // called when channel is closed. can be happened at either side
        // by calling close.
        Log.d(TAG, "onChannelClosed: NodeID:"+channel.getNodeId());
        channel.close(googleApiClient);
    }

    @Override
    public void onInputClosed(Channel channel, int i, int i1) {
        // called when the input side of a channel is closed.
        Log.d(TAG, "[File received.]onInputClosed: NodeID:"+channel.getNodeId());
        // file is received.
        channel.close(googleApiClient);
        EventBus.getDefault().post(new FileReceivedEvent(channel));
    }

    @Override
    public void onOutputClosed(Channel channel, int i, int i1) {
        // called when the output side of a channel is closed.
        Log.d(TAG, "[File sent.]onOutputClosed: NodeID:"+channel.getNodeId());
        // file is sent.
        // add this event if you need do something triggered by this
        // try to close a channel here.
//        channel.close(googleApiClient);
//        EventBus.getDefault().post(new FileSentEvent(channel));
    }

    public boolean isConnected() {

        if (googleApiClient==null) {
            Log.d(TAG, "isConnected: googleApiClient is null.");
            return  false;
        }

        Log.d(TAG, "isConnected: googleApiClient isConnected:"+googleApiClient.isConnected());
        return googleApiClient.isConnected();
    }

    public GoogleApiClient getGoogleApiClient() {
        return googleApiClient;
    }
}
