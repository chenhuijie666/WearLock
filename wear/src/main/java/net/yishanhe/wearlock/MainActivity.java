package net.yishanhe.wearlock;

import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.wearable.activity.WearableActivity;
import android.support.wearable.view.BoxInsetLayout;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

import net.yishanhe.wearcomm.WearCommClient;
import net.yishanhe.wearcomm.events.ReceiveMessageEvent;
import net.yishanhe.wearcomm.events.SendFileEvent;
import net.yishanhe.wearcomm.events.SendMessageEvent;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import android.Manifest;
import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class MainActivity extends WearableActivity {

    private final static String TAG = "WearLock_MainActivity";
    private static final int CLIENT_CONNECTION_TIMEOUT = 15000;

    private Context context = this;

    // UI
    @Bind(R.id.container) BoxInsetLayout mContainerView;
    @Bind(R.id.btn) ImageButton mImageButton;

    // IO
    private AudioRecord mRecorder;
    private File mRecording = null;
    private short[] mBuffer;
    private boolean mIsRecording = false;
    public static String SAVE_DIR = "WearMic";
    private File folder;
    private final static int SAMPLE_RATE = 44100;

    // Msg path
    private static final String START_RECORDING = "/start_recording";
    private static final String STOP_RECORDING = "/stop_recording";
    private static final String SEND_RECORDING = "/send_recording";
    private static final String RECORDING_STARTED = "/RECORDING_STARTED";
    private static final String STOP_ACTIVITY = "/stop_activity";
    private static final int REQUEST_WRITE_STORAGE = 101;
    private static final int REQUEST_RECORD_AUDIO = 102;

    private WearCommClient client = null;
    private AudioReader mic = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);


        setAmbientEnabled();

        Log.d(TAG, "onCreate: main");
        // IO
        folder = new File("/sdcard/"+File.separator+SAVE_DIR+File.separator);
        if(! this.folder.exists()) {
            folder.mkdir();
        }

        // Google API
        client = WearCommClient.getInstance(this);
        mic = new AudioReader();
//        client.connect();

        // check some features, bottom two are supported in API 23
        AudioManager am = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);
        Log.d(TAG, "onCreate: "+am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER));
        Log.d(TAG, "onCreate: "+am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE));
        Log.d(TAG, "onCreate: "+am.getProperty(AudioManager.PROPERTY_SUPPORT_MIC_NEAR_ULTRASOUND));
        Log.d(TAG, "onCreate: "+am.getProperty(AudioManager.PROPERTY_SUPPORT_SPEAKER_NEAR_ULTRASOUND));

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_WRITE_STORAGE);
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_RECORD_AUDIO);
        }

    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case REQUEST_WRITE_STORAGE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//                    this.recreate();
                    Log.d(TAG, "Write to external storage permission granted.");
                } else {
                    Log.d(TAG, "Write to external storage permission denied.");
                }
                break;

            case REQUEST_RECORD_AUDIO:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//                    this.recreate();
                    Log.d(TAG, "Record audio permission granted.");
                } else {
                    Log.d(TAG, "Record audio permission denied.");
                }
                break;
        }
    }

    @OnClick(R.id.btn)
    public void onRecordingBtnPressed() {
        Log.d(TAG, "onRecordingBtnPressed");
        if (!mIsRecording) {
            // start recording
            start();
        } else {
            // stop recording
            stop();
        }
    }

    @Subscribe
    public void onReceiveMessageEvent(ReceiveMessageEvent event){
        if (event.getPath().equalsIgnoreCase(STOP_ACTIVITY)) {
            this.finish();
        }
        if (event.getPath().equalsIgnoreCase(START_RECORDING)) {
            if (!mIsRecording) {
                start();
                EventBus.getDefault().post(new SendMessageEvent(RECORDING_STARTED));
            }
        }
        if (event.getPath().equalsIgnoreCase(STOP_RECORDING)) {
            if (mIsRecording) {
                stop();
            }
        }
        if (event.getPath().equalsIgnoreCase("/measure_message_delay")) {
            // measure round trip time.
            Log.d(TAG, "onReceiveMessageEvent: measure message delay.");
            EventBus.getDefault().post(new SendMessageEvent("/measure_message_delay"));
        }
    }

    @Override
    public void onEnterAmbient(Bundle ambientDetails) {
        super.onEnterAmbient(ambientDetails);
        updateDisplay();
    }

    @Override
    public void onUpdateAmbient() {
        super.onUpdateAmbient();
        updateDisplay();
    }

    @Override
    public void onExitAmbient() {
        updateDisplay();
        super.onExitAmbient();
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy: main");
//        if (mIsRecording) {
//            stop();
//        }
//        if (client.isConnected()) {
//            client.disconnect();
//        }
        super.onDestroy();
    }

    @Override
    protected void onStart() {
        Log.d(TAG, "onStart: main");
        super.onStart();
        EventBus.getDefault().register(this);
        Log.d(TAG, "onStart: client is connected:"+client.isConnected());
        if (!client.isConnected()) {
            client.connect();
        }
    }


    @Override
    protected void onStop() {
        Log.d(TAG, "onStop: main");
        if (mIsRecording) {
            stop();
        }
        if (client.isConnected()) {
            client.disconnect();
        }
        EventBus.getDefault().unregister(this);
        super.onStop();
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause: main");
        super.onPause();
    }

    private void updateDisplay() {
        if (isAmbient()) {
            mContainerView.setBackgroundColor(getResources().getColor(android.R.color.black));
            mImageButton.setVisibility(View.VISIBLE);
        } else {
            mContainerView.setBackground(null);
            mImageButton.setVisibility(View.VISIBLE);
        }
    }


    //**************************************************************************
    // Audio processing and IO
    //**************************************************************************

//    private void initRecorder() {
//
//        // size in bytes 8bit
//        int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
//
//        // short 16bit 1 short 2 bytes
//        mBuffer = new short[bufferSize];
//        mRecorder = new AudioRecord(MediaRecorder.AudioSource.CAMCORDER, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize*2);
//    }

    private void start() {

//        initRecorder();

//        if (mRecorder.getState() == AudioRecord.STATE_INITIALIZED) {
//            mRecorder.startRecording();
//        try {
//            mRecording = File.("wearlock",".raw", folder);
            mRecording = new File(folder, "wearloc.raw");
//            mRecording.createNewFile();
            Log.d(TAG, "start: buffering the audio samples.");
//                startBufferedWriteToRaw(mRecording);
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//            Log.d(TAG, "Recording started.");
        mImageButton.setBackgroundResource(R.drawable.round_button_pressed);
        mIsRecording = true;
//        } else {
//            Log.e(TAG, "Audio reader failed to initialize");
//            mIsRecording = false;
//        }
        mic.startReader(SAMPLE_RATE, mRecording);
    }

    private void stop() {
        mIsRecording = false;
//        mRecorder.stop();

        mic.stopReader();
//        File waveFile = createNewFile("wav");
//        try {
//            rawToWave(mRecording, waveFile);
//        } catch (IOException e) {
//            e.printStackTrace();
//        }

        Log.d(TAG, "Recording stopped.");
        Toast.makeText(this, "Audio recorded.", Toast.LENGTH_SHORT).show();

        mImageButton.setBackgroundResource(R.drawable.round_button);
//        mRecorder.release();
//        mRecorder = null;

        // send recorded wav file
//        Log.d(TAG, "send recorded wav file");
        Log.d(TAG, "send recorded raw file");
        EventBus.getDefault().post(new SendFileEvent(SEND_RECORDING, Uri.fromFile(mRecording)));
    }



    public void clearLogs(){

        if (folder.isDirectory()) {
            for(File child: folder.listFiles()){
                child.delete();
            }
        }

    }

//    private void startBufferedWriteToRaw(final File file) {
//        new Thread(new Runnable() {
//            @Override
//            public void run() {
//                DataOutputStream output = null;
//                try {
//                    output = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)));
//                    while (mIsRecording) {
//                        int readSize = mRecorder.read(mBuffer, 0, mBuffer.length);
//                        Log.d(TAG, "StartBufferedWriteToRaw: read "+readSize+" samples.");
//                        for (int i = 0; i < readSize; i++) {
//                            output.writeShort(mBuffer[i]);
//                        }
//                        // calculate on chunk decide whether to put it into. some where
//                        // using a ringbuffer if want to use sliding windows rather windowing
//                    }
//                } catch (IOException e) {
//                    e.printStackTrace();
//                } finally {
//                    if (output != null) {
//                        try {
//                            output.flush();
//                            Log.d(TAG, "flush output");
//                        } catch (IOException e) {
//                            e.printStackTrace();
//                        } finally {
//                            try {
//                                output.close();
//                                Log.d(TAG, "close output");
//                            } catch (IOException e) {
//                                e.printStackTrace();
//                            }
//                        }
//                    }
//                }
//            }
//        }).start();
//    }



    //**************************************************************************
    // test audio hardware capabilities
    // call those functions
    //**************************************************************************

    public void getValidSampleRates() {
        for (int rate : new int[] {8000, 11025, 16000, 22050, 44100, 48000}) {  // add the rates you wish to check against
            int bufferSize = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_CONFIGURATION_DEFAULT, AudioFormat.ENCODING_PCM_16BIT);
            if (bufferSize > 0) {
                // buffer size is valid, Sample rate supported
                Log.d(TAG, "Supporting sample rate: "+rate+" Hz.");
            }
        }
    }

    public void getValideChannelInDevices() {
        for (int device: new int[] {MediaRecorder.AudioSource.CAMCORDER, MediaRecorder.AudioSource.DEFAULT,
                MediaRecorder.AudioSource.MIC} ) {
            int bufferSize = AudioRecord.getMinBufferSize(44100, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            AudioRecord testRecord = new AudioRecord(device, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);
            if ( testRecord != null ) {
                Log.d(TAG, "This device is supported: " + device);
            }
        }
    }

    public void getValideInputChannel() {
        for (int channel: new int[] {AudioFormat.CHANNEL_IN_BACK, AudioFormat.CHANNEL_IN_DEFAULT,
                AudioFormat.CHANNEL_IN_FRONT, AudioFormat.CHANNEL_IN_LEFT, AudioFormat.CHANNEL_IN_RIGHT,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.CHANNEL_IN_STEREO} ) {
            int bufferSize = AudioRecord.getMinBufferSize(44100, channel, AudioFormat.ENCODING_PCM_16BIT);
            if (bufferSize > 0) {
                // buffer size is valid, Sample rate supported
                Log.d(TAG, "Supporting channel is: "+channel);
            }
        }
    }

}
