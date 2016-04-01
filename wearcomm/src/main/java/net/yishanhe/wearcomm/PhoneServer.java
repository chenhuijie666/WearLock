package net.yishanhe.wearcomm;

import android.util.Log;

import net.yishanhe.wearcomm.events.ReceiveMessageEvent;
import net.yishanhe.wearcomm.events.SendFileEvent;
import net.yishanhe.wearcomm.events.SendMessageEvent;

import org.greenrobot.eventbus.EventBus;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

/**
 * Created by syi on 3/10/16.
 * this one should be placed at fake wearable side;
 */
public class PhoneServer {

    private static final String TAG = "PhoneServer";

    private ServerSocket serverSocket;
    private ArrayList<ClientHandler> threadPool;

    private static final int PORT = 8080;
    private static final int RECEIVING_MESSAGE = 0;
    private static final int RECEIVING_FILE = 1;

    private static final String START_RECORDING_P2 = "/start_recording_p2";
    private static final String START_RECORDING_P1 = "/start_recording_p1";
    private static final String STOP_RECORDING = "/stop_recording";
    private static final String SEND_RECORDING = "/send_recording";
    private static final String RECORDING_STARTED = "/RECORDING_STARTED";
    private static final String STOP_ACTIVITY = "/stop_activity";
    private Thread thread;


    public PhoneServer() {
        thread = new Thread(new ServerThread());
        thread.start();
        threadPool = new ArrayList<>();
    }

    public class ServerThread implements Runnable {

        @Override
        public void run() {
            try {
                serverSocket = new ServerSocket(PORT);
                Log.d(TAG, "run: local server is monitoring");
                while (true) {
                    Socket client = serverSocket.accept();
                    ClientHandler temp = new ClientHandler(client);
                    temp.start();
                    threadPool.add(temp);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // this client handles the receiving of messages and files.
    public class ClientHandler extends Thread {
        public InputStream inputStream = null;
        public BufferedReader input = null;
        public OutputStream output = null;
        public Socket client = null;
        public int status;
//        public int fileSizeInBytes;

        public ClientHandler(Socket client) {
            this.client = client;
        }

        @Override
        public void run() {
            try {
                input = new BufferedReader(new InputStreamReader(client.getInputStream()));
                output = client.getOutputStream();
                String line;
                // block
                while ((line = input.readLine())!=null) { // blocked. util quit is called.
                    // reading a message or path.
                    if (line.contains("HI")) {
                        Log.d(TAG, "run: hi from client");
                        output.write("HI\r\n".getBytes());
                    }
                    if (line.contains("/MESSAGE")){
                        status = RECEIVING_MESSAGE;
                        String path = line.substring(line.indexOf("/MESSAGE")+8); // /message is followed by path string
                        String message = input.readLine();
                        EventBus.getDefault().post(new ReceiveMessageEvent(path, message.getBytes()));
                    }
                    if (line.contains("/QUIT")) {
                        break;
                    }
                }

//                switch (status) {
//                    case RECEIVING_MESSAGE:
//                        break;
//                }

                input.close();
                output.close();
                client.close();
                threadPool.remove(this);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void sendMessage(SendMessageEvent event) {
        if (!threadPool.isEmpty()) {
            for (int i = 0; i < threadPool.size(); i++) {
                try {
                    Log.d(TAG, "sendMessage: send out" + ("/MESSAGE"+event.getPath()+"\r\n"+(new String(event.getData()))+"\r\n"));
                    threadPool.get(i).output.write( ("/MESSAGE"+event.getPath()+"\r\n"+(new String(event.getData()))+"\r\n").getBytes());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void sendFile(SendFileEvent event) {
        if (!threadPool.isEmpty()) {
            for (int i = 0; i < threadPool.size(); i++) {
                try {
//                    byte[] buffer = new byte[threadPool.get(i).client.getSendBufferSize()];
                    byte[] buffer = new byte[8194];
                    File toSend = new File(event.getUri().getPath());
                    BufferedInputStream in = new BufferedInputStream(new FileInputStream(toSend));
                    long fileSize = toSend.length();
                    int read;
                    int bytesRead = 0;
                    threadPool.get(i).output.write(("/FILE"+fileSize+"\r\n").getBytes());

//                    while ( bytesRead<fileSize && (read = in.read(buffer, 0, Math.min(buffer.length, ((int)fileSize-bytesRead))))!=-1) {
//                        threadPool.get(i).output.write(buffer, 0, read);
//                        bytesRead += read;
//                        Log.d(TAG, "sendFile: read "+read+", sent/full"+bytesRead+"/"+fileSize);
//                        threadPool.get(i).output.flush();
//                    }
                    while ( (read = in.read(buffer))!=-1) {
                        threadPool.get(i).output.write(buffer, 0, read);
                        bytesRead += read;
//                        Log.d(TAG, "sendFile: read "+read+", sent/full"+bytesRead+"/"+fileSize);
                        Log.d(TAG, "sendFile: read "+read+", sent/full"+bytesRead+"/"+fileSize);
                    }
                    threadPool.get(i).output.flush();
                    in.close();
                    Log.d(TAG, "sendFile: file sent.");

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
