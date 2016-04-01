package net.yishanhe.wearlock;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.wearable.activity.WearableActivity;
import android.support.wearable.view.BoxInsetLayout;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

import net.yishanhe.ofdm.Chunk;
import net.yishanhe.utils.IOUtils;
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
import java.util.ArrayList;

import android.Manifest;
import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;

// @TODO: sliding to left to go to setting activity.

public class MainActivity extends WearableActivity implements SensorEventListener {

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
    private static final String START_RECORDING_P1 = "/start_recording_p1";
    private static final String START_RECORDING_P2 = "/start_recording_p2";
    private static final String STOP_RECORDING = "/stop_recording";
    private static final String SEND_RECORDING = "/send_recording";
    private static final String RECORDING_STARTED = "/RECORDING_STARTED";
    private static final String STOP_ACTIVITY = "/stop_activity";
    // Permissions for Android M.
    private static final int REQUEST_PERMISSIONS = 101;
    private static String[] PERMISSIONS = {Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.RECORD_AUDIO};

    private WearCommClient client = null;
    private AudioReader mic = null;

    // added sensor part
    private SensorManager sensorManager;
    private static final int[] REQUIRED_SENSOR = {Sensor.TYPE_LINEAR_ACCELERATION};
    private static final int[] SENSOR_RATES = {SensorManager.SENSOR_DELAY_FASTEST};
    private long sensorStartTime;
    private int samplingRateCtrAcc;
    private boolean showSamplingRateAcc = true;
    private FileOutputStream fosAcc = null;

    private boolean enableSensor = true;
    private Modem modem = null;

    private boolean localProcessing = false;

    private static final int LOCAL = 0;
    private static final int REMOTE_PREAMBLE = 1;
    private static final int REMOTE_MODULATED = 2;
    private int state;


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

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, PERMISSIONS, REQUEST_PERMISSIONS);
        }

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);


//        if (localProcessing) {
//            // MODEM
//            // prepare Modem
        modem = Modem.getInstance(this);


        if (modem != null) {
            modem.LoadParameter();
            modem.prepare();

            modem.makePreamble();
            modem.makeModulated();
        }
//        }

        clearLogs();
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

    @OnClick(R.id.btn)
    public void onRecordingBtnPressed() {
        Log.d(TAG, "onRecordingBtnPressed");
        if (!mIsRecording) {
            // start recording
            state = LOCAL;

            if (enableSensor) {
                startSensor();
            } else {
                start();
            }
        } else {
            // stop recording

            if (enableSensor) {
                stopSensor();
            } else {
                stop();
            }
        }
    }

    @Subscribe
    public void onReceiveMessageEvent(ReceiveMessageEvent event){
        if (event.getPath().equalsIgnoreCase(STOP_ACTIVITY)) {
            this.finish();
        }
        if (event.getPath().equalsIgnoreCase(START_RECORDING_P1)||event.getPath().equalsIgnoreCase(START_RECORDING_P2)) {
            if (event.getPath().equalsIgnoreCase(START_RECORDING_P1)) {
                state = REMOTE_PREAMBLE;
            } else {
                state = REMOTE_MODULATED;
            }
            if (!mIsRecording) {

                if (enableSensor) {
                    startSensor();
                } else {
                    start();
                }
                EventBus.getDefault().post(new SendMessageEvent(RECORDING_STARTED));
            }
        }
        if (event.getPath().equalsIgnoreCase(STOP_RECORDING)) {
            if (mIsRecording) {

                if (enableSensor) {
                    stopSensor();
                } else {
                    stop();
                }
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

    private void startSensor() {
        for (int i = 0; i < REQUIRED_SENSOR.length; i++) {
            Sensor sensor = sensorManager.getDefaultSensor(REQUIRED_SENSOR[i]);
            if (sensor!=null) {
                Log.d(TAG, "startSensor: registering " + sensor.getName());
                sensorManager.registerListener(this, sensor, SENSOR_RATES[i]);
            } else {
                Log.d(TAG, "startSensor: not found "+sensor.getName());
            }
        }
        if (fosAcc == null) {
            try {
                mRecording = new File(folder, "wearloc.sensor");
                fosAcc = new FileOutputStream(mRecording,false);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        samplingRateCtrAcc = 0;
        showSamplingRateAcc = true;
        sensorStartTime = System.currentTimeMillis();
        Log.d(TAG, "startSensor: start logging sensor");
        mImageButton.setBackgroundResource(R.drawable.round_button_pressed);
        mIsRecording = true;
    }

    private void stopSensor() {
        if (fosAcc!=null) {
            try {
                fosAcc.close();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                fosAcc = null;
            }
        }
        sensorManager.unregisterListener(this);
        Toast.makeText(this, "Sensor recorded.", Toast.LENGTH_SHORT).show();
        mIsRecording = false;
        mImageButton.setBackgroundResource(R.drawable.round_button);
        Log.d(TAG, "stopSensor: stop logging sensor");

        Log.d(TAG, "send recorded raw file");
        EventBus.getDefault().post(new SendFileEvent(SEND_RECORDING, Uri.fromFile(mRecording)));

    }

//    public File createNewFile(String name) {
//        int num = 0;
//        File file = new File(folder.toString(),String.valueOf(num++)+"_"+name+".txt");
//        while (file.exists()) {
//            file = new File(folder.toString(),String.valueOf(num++)+"_"+name+".txt");
//        }
//        return file;
//    }
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



        if (localProcessing) {

            Chunk chunk = new Chunk(true, IOUtils.loadFromFile(mRecording));
            double[] input = chunk.getDoubleBuffer();

            long processingBegin = System.currentTimeMillis();

            SlidingWindow sw = new SlidingWindow(4096, 2048, input);

            SilenceDetector sd = new SilenceDetector(-70.0);

            boolean isClipStart = false;
            ArrayList<Integer> startIndexArray = new ArrayList<>();
            ArrayList<Integer> endIndexArray = new ArrayList<>();

            int maxStartIdx = 0;
            int maxEndIdx = 0;
            double maxSPL = -1000.0;
            double minSPL = 1000.0;
            double CNR;

            while (sw.hasNext()) {

                double[] chunkData = sw.next();

                if (sd.isSilence(chunkData)) {
                    // silent do nothing.
                    if (isClipStart) {
                        isClipStart = false;
                        endIndexArray.add(sw.getStart());
                    }
                } else {
                    // detected sound
                    if (!isClipStart) {
                        isClipStart = true;
                        startIndexArray.add(sw.getStart());
                    }
                }

                // get minSPL:
                if (sd.getCurrentSPL() < minSPL) {
                    minSPL = sd.getCurrentSPL();
                }

                // get maxSPL
                if (sd.getCurrentSPL() > maxSPL) {
                    maxSPL = sd.getCurrentSPL();
                    System.out.println("maxSPD updated. " + maxSPL);
                    maxStartIdx = Math.max(sw.getStart(), 0);
                    maxEndIdx = Math.min(sw.getEnd(), chunk.getDoubleBuffer().length);
                }

            }
            CNR = maxSPL - minSPL;


            if (isClipStart) {
                isClipStart = false;
                endIndexArray.add(chunk.getDoubleBuffer().length);
            }


            if (startIndexArray.size() != endIndexArray.size()) {
                throw new IllegalArgumentException("chunk start and end size mismatch.");
            }

            // fall back to max SPL chunk to run preamble detection.
            if (startIndexArray.size() == 0) {
//            EventBus.getDefault().post(new MessageEvent(TAG, "detect preamble:  not found. use the max one. start:"+maxStartIdx+" end:"+maxEndIdx,"/UPDATE_STATUS"));
                startIndexArray.add(maxStartIdx);
                endIndexArray.add(maxEndIdx);

            }

            int maxIndex = 0;
            double maxXcorrVal = 0.0;
            for (int i = 0; i < startIndexArray.size(); i++) {
                int start = startIndexArray.get(i);
                int end = endIndexArray.get(i);
//                Log.d(TAG, "onFileReceivedEvent: check chunk start:"+start+"end:"+end);
                double[] candidate = chunk.getDoubleBuffer(start, end);
                double xcorrVal = modem.detectPreamble(candidate);
                if (xcorrVal > maxXcorrVal) {
                    maxXcorrVal = xcorrVal;
                    maxIndex = i;
                }
            }

            if (maxXcorrVal < 0.05) {
                return;
            }


            long windowingEnd = System.currentTimeMillis();
            Log.d(TAG, "[DELAY] Windowing Xcorr:"+(windowingEnd-processingBegin));


            if (state == REMOTE_MODULATED) {
                String demodulated = modem.deModulate(chunk, startIndexArray.get(maxIndex), endIndexArray.get(maxIndex));
                // call demodulate
            }

            if (state == REMOTE_PREAMBLE) {
                modem.channelProbing(chunk, startIndexArray.get(maxIndex), endIndexArray.get(maxIndex), minSPL);
            }

            long processingEnd = System.currentTimeMillis();
            if (state == REMOTE_MODULATED) {
                Log.d(TAG, "[DELAY]: Demodulation:"+(processingEnd-windowingEnd)+", Phase 2 Total:"+(processingEnd-processingBegin));
            } else if (state == REMOTE_PREAMBLE) {
                Log.d(TAG, "[DELAY]: Channel Probing:"+(processingEnd-windowingEnd)+", Phase 1 Total:"+(processingEnd-processingBegin));
            }
        }

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



    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
            if (showSamplingRateAcc) {
                samplingRateCtrAcc++;
                if (samplingRateCtrAcc >= 50) {
                    long now = System.currentTimeMillis();
                    showSamplingRateAcc = false;
                    Log.d(TAG, "onSensorChanged: acc sampling rate "+ (double)samplingRateCtrAcc/ ((double)(now-sensorStartTime)/1000.0) );
                }
            }

            if (fosAcc!=null) {
                try {
                    fosAcc.write(( Math.sqrt(Math.pow(event.values[0],2)+Math.pow(event.values[1],2)+Math.pow(event.values[2],2)) +","+event.timestamp+"\n").getBytes());
//                    fosAcc.write((event.values[0]+","+event.values[1]+","+event.values[2]+","+event.timestamp+"\n").getBytes());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
}
