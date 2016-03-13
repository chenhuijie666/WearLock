package net.yishanhe.audiorecorder;

import android.Manifest;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.format.Formatter;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;
import net.yishanhe.wearcomm.FakeWearCommClient;
import net.yishanhe.wearcomm.events.ReceiveMessageEvent;
import net.yishanhe.wearcomm.events.SendFileEvent;
import net.yishanhe.wearcomm.events.SendMessageEvent;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.io.File;

public class MainActivity extends AppCompatActivity {

    private final static String TAG = "MainActivity";
    private boolean isRecording = false;
    private File folder;
    private final static int SAMPLE_RATE = 44100;
    private File recording;
    private AudioReader mic = null;
    private FakeWearCommClient client = null;

    // Msg path
    private static final String START_RECORDING = "/start_recording";
    private static final String STOP_RECORDING = "/stop_recording";
    private static final String SEND_RECORDING = "/send_recording";
    private static final String RECORDING_STARTED = "/RECORDING_STARTED";
    private static final String STOP_ACTIVITY = "/stop_activity";

    // Permissions for Android M.
    private static final int REQUEST_PERMISSIONS = 101;
    private static String[] PERMISSIONS = {Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.RECORD_AUDIO};

    private TextView textView;
    private FloatingActionButton fab;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setImageResource(R.drawable.ic_mic_white_24dp);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if (isRecording) {
                    stop();
                    Snackbar.make(view, "Stop recording.", Snackbar.LENGTH_LONG).setAction("Action", null).show();
                } else {
                    start();
                    Snackbar.make(view, "Start recording.", Snackbar.LENGTH_LONG).setAction("Action", null).show();
                }
            }
        });

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, PERMISSIONS, REQUEST_PERMISSIONS);
        }

        folder = new File(Environment.getExternalStorageDirectory().getPath()+"/WearLock/AudioRecorder/");
        if (!folder.exists()) {
            if (!folder.mkdirs()) {
                Log.e(TAG, "onCreate: create folders failed.");
            }
        }

        mic = new AudioReader();

        if (client == null) {
            client = FakeWearCommClient.getInstance(this);
            client.connect();
        }

        textView = (TextView) findViewById(R.id.ip_address);
        textView.setText(findLocalIPAddress());

        EventBus.getDefault().register(this);

    }

    @Subscribe
    public void onReceiveMessageEvent(ReceiveMessageEvent event){
        if (event.getPath().equalsIgnoreCase(START_RECORDING)) {
            if (!isRecording) {
                start();
                EventBus.getDefault().post(new SendMessageEvent(RECORDING_STARTED));
            }
        }
        if (event.getPath().equalsIgnoreCase(STOP_RECORDING)) {
            if (isRecording) {
                stop();
            }
        }
        if (event.getPath().equalsIgnoreCase("/measure_message_delay")) {
            // measure round trip time.
            Log.d(TAG, "onReceiveMessageEvent: measure message delay.");
            EventBus.getDefault().post(new SendMessageEvent("/measure_message_delay"));
        }
    }

    public String findLocalIPAddress(){
        WifiManager wm = (WifiManager) getSystemService(WIFI_SERVICE);
        String ip = Formatter.formatIpAddress(wm.getConnectionInfo().getIpAddress());
        return ip;
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_PERMISSIONS:
                if (PermissionUtil.verifyPermissions(grantResults)) {
                    Toast.makeText(this, "Permissions granted.", Toast.LENGTH_SHORT).show();
                    recreate();
                } else {
                    Toast.makeText(this, "Permissions not granted.", Toast.LENGTH_SHORT).show();
                }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void start() {
        recording = new File(folder, "recording.raw");
        isRecording = true;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                fab.setImageResource(R.drawable.ic_stop_white_24dp);
            }
        });
        mic.startReader(SAMPLE_RATE, recording);
    }

    private void stop() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                fab.setImageResource(R.drawable.ic_mic_white_24dp);
            }
        });
        isRecording = false;
        mic.stopReader();

        Log.d(TAG, "send recorded raw file");
        EventBus.getDefault().post(new SendFileEvent(SEND_RECORDING, Uri.fromFile(recording)));
    }


    @Override
    protected void onDestroy() {
        if (isRecording) {
            stop();
        }
        if (client!=null) {
            client.disconnect();
            client = null;
        }
        EventBus.getDefault().unregister(this);
        super.onDestroy();
    }
}
