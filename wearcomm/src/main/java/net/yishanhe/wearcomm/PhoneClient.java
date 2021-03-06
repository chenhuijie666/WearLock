package net.yishanhe.wearcomm;

import android.util.Log;

import net.yishanhe.wearcomm.events.FileReceivedEvent;
import net.yishanhe.wearcomm.events.ReceiveMessageEvent;
import net.yishanhe.wearcomm.events.SendMessageEvent;

import org.greenrobot.eventbus.EventBus;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;

/**
 * Created by syi on 3/10/16.
 * A phone fake the watch will play as a Socket client
 * connected to the sender.
 */
public class PhoneClient {

    private static final String TAG = "PhoneClient";
    private Socket socket;
    private String serverIP = "192.168.0.100";
    private int serverPort = 8080;
    private static final int RECEIVING_MESSAGE = 0;
    private static final int RECEIVING_FILE = 1;
    private BufferedReader bufferedReader = null;
    private InputStream inputStream = null;
    private OutputStream outputStream = null;
    private Thread thread;
    public int status;
    private boolean isblocking = false;

    private boolean connected = false;

    public PhoneClient(String serverIP) {
        this.serverIP = serverIP;
    }

    public void connect() {
        if (!connected) {
            // connected
            thread = new Thread(new ClientThread());
            thread.start();
            connected = true;
        }
    }

    public void disconnect() {
        if (connected && outputStream!=null) {
            try {
                Log.d(TAG, "disconnect: sent out /QUIT");
                outputStream.write("/QUIT\r\n".getBytes());
                connected = false;
                bufferedReader.close();
                outputStream.close();
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // client at the phone side

    public class ClientThread implements Runnable {

        @Override
        public void run() {
            try {


                socket = new Socket(serverIP, serverPort);

                inputStream = socket.getInputStream();
                bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
                outputStream = socket.getOutputStream();
                outputStream.write(("HI\r\n").getBytes());

                String line;
                while((line = bufferedReader.readLine()) != null){
                    // reading a message or path.
                    if (line.contains("/MESSAGE")){
                        Log.d(TAG, "run: receive message");
                        status = RECEIVING_MESSAGE;
                        String path = line.substring(8); // /message is followed by path string
                        String message = bufferedReader.readLine();
                        EventBus.getDefault().post(new ReceiveMessageEvent(path, message.getBytes()));
                    }
                    if (line.contains("HI")) {
                        Log.d(TAG, "run: hi from server");
                    }
                    if (line.contains("/FILE")) {
                        status = RECEIVING_MESSAGE;
                        long fileReceiveStartTime = System.currentTimeMillis();
                        long fileSize = Integer.valueOf(line.substring(line.indexOf("/FILE")+5));
//                        bufferedReader.close();
//                        BufferedInputStream bis = new BufferedInputStream(socket.getInputStream());
                        File file = new File("/sdcard/WearLock/tmp.raw");
                        byte[] buffer = new byte[8194];
                        FileOutputStream fos = new FileOutputStream(file);
                        int read;
                        int bytesRead = 0;
                        long remaining = fileSize;

                        while ( (read = inputStream.read(buffer, 0, Math.min(buffer.length, (int)remaining)))>0 ) {
                            isblocking = true;
                            fos.write(buffer, 0, read);
                            fos.flush();
                            bytesRead += read;
                            remaining -= read;
                            Log.d(TAG, "sendFile: read "+read+", read/full "+bytesRead+"/"+fileSize);

                        }
                        isblocking = false;
                        fos.close();
//                        bufferedReader.close();
//                        bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                        // post
                        Log.d(TAG, "[DELAY] file transfer time cost:"+(System.currentTimeMillis()-fileReceiveStartTime));
                        EventBus.getDefault().post(new FileReceivedEvent(null));
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }


    public void sendMessage(SendMessageEvent event) {
        try {
            if (outputStream !=null) {
                if (!isblocking) {
                    outputStream.write(("/MESSAGE" + event.getPath() + "\r\n" + (new String(event.getData())) + "\r\n").getBytes());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean isblocking() {
        return isblocking;
    }
}
