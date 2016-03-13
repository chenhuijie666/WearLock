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
    private BufferedReader input = null;
    private OutputStream output = null;
    private Thread thread;
    public int status;

    private boolean connected = false;

    public PhoneClient(String serverIP) {
        this.serverIP = serverIP;
    }

    public void connect() {
        if (!connected) {
            // connected
            thread = new Thread(new ClientThread());
            thread.start();
        }
    }

    public void disconnect() {
        if (connected) {
            try {
                output.write("/QUIT\r\n".getBytes());
                connected = false;
                input.close();
                output.close();
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

                input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                output = socket.getOutputStream();
                output.write(("HI\r\n").getBytes());

                String line;
                while((line = input.readLine()) != null){
                    // reading a message or path.
                    if (line.contains("/MESSAGE")){
                        Log.d(TAG, "run: receive message");
                        status = RECEIVING_MESSAGE;
                        String path = line.substring(8); // /message is followed by path string
                        String message = input.readLine();
                        EventBus.getDefault().post(new ReceiveMessageEvent(path, message.getBytes()));
                    }
                    if (line.contains("HI")) {
                        Log.d(TAG, "run: hi from server");
                    }
                    if (line.contains("/FILE")) {
                        status = RECEIVING_MESSAGE;
                        Log.d(TAG, "run: receive file");
                        input.close();
                        BufferedInputStream bis = new BufferedInputStream(socket.getInputStream());
                        File file = new File("/sdcard/WearLock/tmp.raw");
                        byte[] buffer = new byte[16*1024];
                        FileOutputStream fos = new FileOutputStream(file);
                        int bytesRead;

                        while ( (bytesRead = bis.read(buffer))
                                != -1) {
                            fos.write(buffer, 0, bytesRead);
                        }
                        fos.close();
                        bis.close();
                        input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                        // post
                        Log.d(TAG, "run: file received");
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
            if (output!=null) {
                output.write( ("/MESSAGE"+event.getPath()+"\r\n"+(new String(event.getData()))+"\r\n").getBytes());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
