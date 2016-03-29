package net.yishanhe.wearlock;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;

import com.github.clans.fab.FloatingActionButton;
import com.github.clans.fab.FloatingActionMenu;
import com.google.android.gms.common.api.GoogleApiClient;

import net.yishanhe.ofdm.Chunk;
import net.yishanhe.utils.IOUtils;
import net.yishanhe.wearcomm.FakeWearCommClient;
import net.yishanhe.wearcomm.WearCommClient;
import net.yishanhe.wearcomm.events.ChannelOpenedEvent;
import net.yishanhe.wearcomm.events.FileReceivedEvent;
import net.yishanhe.wearcomm.events.ReceiveMessageEvent;
import net.yishanhe.wearcomm.events.SendMessageEvent;
import net.yishanhe.wearlock.events.MessageEvent;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class MainActivity extends AppCompatActivity implements GoogleApiClient.ConnectionCallbacks , AudioTrack.OnPlaybackPositionUpdateListener {

    private static final String TAG = "MainActivity";

    private static final String START_ACTIVITY = "/start_activity";
    private static final String STOP_ACTIVITY = "/stop_activity";
    private static final String START_RECORDING = "/start_recording";
    private static final String RECORDING_STARTED = "/RECORDING_STARTED";
    private static final String STOP_RECORDING = "/stop_recording";
    private static final String SEND_RECORDING = "/send_recording";
    private File rec;
    private File audioFolder;
    private File logFile; // @TODO: put result in log file
    private File folder;
    private String inputPin = "";
    private static final int REQUEST_WRITE_STORAGE = 112;

    final Handler handler = new Handler();

    private static final int LOCAL = 0;
    private static final int REMOTE_PREAMBLE = 1;
    private static final int REMOTE_MODULATED = 2;
    private int state;

    private long messageSentTime;
    private long fileSentTime;

    // client-server communication
    private SharedPreferences prefs;
    private boolean useFakeWear = false;
    private String serverIP;

    private MediaPlayer mp;



    private double cumSPL = 0.0;
    private int cumSPLCtr = 0;


    // BINDING Bufferknife

    @Bind(R.id.toolbar) Toolbar toolbar;

    // fab menu and buttons.
    @Bind(R.id.fab) FloatingActionMenu fab;

    @Bind(R.id.fab_clean) FloatingActionButton fabClean;
    @OnClick(R.id.fab_clean)
    public void clean() {
        EventBus.getDefault().post(new MessageEvent(TAG, "", "/clean_status"));
        inputPin = null;
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (audioFolder.isDirectory()) {
                    for (File child : audioFolder.listFiles()) {
                        child.delete();
                    }
                }
                if (folder.isDirectory()) {
                    for (File child: folder.listFiles()) {
                        if (!child.isDirectory()) {
                            child.delete();
                        }
                    }
                }
            }
        }, 100);
        fab.toggle(true);
    }

    // @TODO: add preference to play with remote recording or just local.
    @Bind(R.id.fab_play) FloatingActionButton fabPlay;
    @OnClick(R.id.fab_play)
    public void playLocal() {
        state = LOCAL;
        if (modulatedTrack!=null) {
            handler.post(modulatedTrack);
        } else {
            handler.post(preambleTrack);
        }
        fab.toggle(true);
    }

    @Bind(R.id.fab_probing_beep) FloatingActionButton fabProbingBeep;
    @OnClick(R.id.fab_probing_beep)
    public void sendProbingBeep() {
        state = REMOTE_PREAMBLE;
        EventBus.getDefault().post(new SendMessageEvent(START_RECORDING));
        fab.toggle(true);

    }

    @Bind(R.id.fab_modulated_beep) FloatingActionButton fabModulatedBeep;
    @OnClick(R.id.fab_modulated_beep)
    public void sendModulatedBeep() {
        state = REMOTE_MODULATED;
        EventBus.getDefault().post(new SendMessageEvent(START_RECORDING));
        fab.toggle(true);
    }

    @Bind(R.id.fab_fst) FloatingActionButton fabFST;
    @OnClick(R.id.fab_fst)
    public void playFreqSweepTest() {

        if (mp.isPlaying()) {
            mp.stop();
            mp.start();
        } else {
            mp.start();
        }
        fab.toggle(true);
    }

    @Bind(R.id.fab_timer) FloatingActionButton fabTimer;
    @OnClick(R.id.fab_timer)
    public void measureMessageDelay() {
        Log.d(TAG, "measureMessageDelay is called.");
        EventBus.getDefault().post(new SendMessageEvent("/measure_message_delay"));
        messageSentTime = System.currentTimeMillis();
        fab.toggle(true);
    }

    @Subscribe
    public void onReceiveMessageEvent(ReceiveMessageEvent event){

        if (event.getPath().equalsIgnoreCase("/measure_message_delay")) {
            Log.d(TAG, "onReceiveMessageEvent: message RTT:"+(System.currentTimeMillis()-messageSentTime)+"ms");
            EventBus.getDefault().post(new MessageEvent(TAG, "Measured RTT:"+(System.currentTimeMillis()-messageSentTime)+"ms"));
        }
        if (event.getPath().equalsIgnoreCase(RECORDING_STARTED)) {
            Log.d(TAG, "onReceiveMessageEvent: remote recording started.");
            switch (state) {
                case REMOTE_PREAMBLE:
                    handler.postDelayed(preambleTrack,200);
                    break;
                case REMOTE_MODULATED:
                    handler.postDelayed(modulatedTrack,200);
                    break;
            }
        }
        if (event.getPath().equalsIgnoreCase("/PIN")) {
            String newPin = new String(event.getData());
            if (!newPin.equals(inputPin)) {

            }
            setInputPin( new String(event.getData()));
            Log.d(TAG, "onReceiveMessageEvent: new pin code "+inputPin);
            EventBus.getDefault().post(new MessageEvent(TAG, "pin "+inputPin));

            // regenerate the ofdm modulated sound.
            modem.makeModulated(inputPin);
//            if (modulatedTrack!=null) {
//                modulatedTrack.release();
//                modulatedTrack = null;
//            }
            modulatedTrack = new AudioRunnable(modem.getSampleRateInHZ(), modem.getModulatedInShort(), this);
            fabModulatedBeep.setEnabled(true);
        }

        if (event.getPath().equalsIgnoreCase("PREFERENCE_UPDATED")) {
            String key = new String(event.getData());
            if (key.equalsIgnoreCase("server_ip")) {
                serverIP = prefs.getString("server_ip", "192.168.1.10");
            }

            if (key.equalsIgnoreCase("fake_wear_mode")) {
                useFakeWear = prefs.getBoolean("fake_wear_mode",false);
                if (useFakeWear) {
                    recreate();
                }
            }
        }
    }

    @Bind(R.id.backdrop) ImageView iv;

    private Modem modem = null;
    private WearCommClient client = null;
    private FakeWearCommClient fakeWearCommClient = null;
    private AudioRunnable preambleTrack = null;
    private AudioRunnable modulatedTrack = null;

    private Fragment activeFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setContentView(R.layout.activity_main);

        Log.d(TAG, "onCreate: main.");
        EventBus.getDefault().register(this);
        ButterKnife.bind(this);

        // iv.setImageDrawable(getResources().getDrawable(R.drawable.ic_action_header));
        if (toolbar!=null) {
            setSupportActionBar(toolbar);
        }


        //
        MainActivityFragment homeFragment = new MainActivityFragment();
        getSupportFragmentManager().beginTransaction().add(R.id.fragment_container, homeFragment).commit();
        activeFragment = homeFragment;
//        displayFragment(homeFragment);

        // set fab
        fab.setClosedOnTouchOutside(true);
        fabModulatedBeep.setEnabled(false);

        // capability check
        AudioManager am = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);
        Log.d(TAG, "onCreate: "+am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER));
        Log.d(TAG, "onCreate: "+am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE));
        Log.d(TAG, "onCreate: "+am.getProperty(AudioManager.PROPERTY_SUPPORT_MIC_NEAR_ULTRASOUND));
        Log.d(TAG, "onCreate: "+am.getProperty(AudioManager.PROPERTY_SUPPORT_SPEAKER_NEAR_ULTRASOUND));

        // get shared preference
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        useFakeWear = prefs.getBoolean("fake_wear_mode",false);

        if (useFakeWear) {

            serverIP = prefs.getString("server_ip", "192.168.1.10");
            fakeWearCommClient = FakeWearCommClient.getInstance(this, serverIP);
            if (fakeWearCommClient!=null) {
                fakeWearCommClient.connect();
            }
        } else {
            // init google api client
            client = WearCommClient.getInstance(this, this);
            if (client != null) {
                client.connect();
            }
        }




        // prepare Modem
        modem = Modem.getInstance(this);


        if (modem != null) {
            modem.LoadParameter();
            modem.prepare();

            modem.makePreamble();

            // load preamble/modulated to tracks
            preambleTrack = new AudioRunnable(modem.getSampleRateInHZ(), modem.getPreambleInShort(), this);
            // modulated symbol

        }

        if ( ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_WRITE_STORAGE);
        }

        // IO
        audioFolder = new File(Environment.getExternalStorageDirectory().getPath()+"/WearLock/audio/");
        if(! this.audioFolder.exists()) {
            if (!audioFolder.mkdirs()) {
                Log.e(TAG, "onCreate: create folders failed.");
            }
        }
        folder = new File(Environment.getExternalStorageDirectory().getPath()+"/WearLock/");

        mp = MediaPlayer.create(this, R.raw.sweeptest);
        mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                Log.d(TAG, "onCompletion: mp play finished.");
            }
        });

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_WRITE_STORAGE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "Write to external storage permission granted.");
                } else {
                    Log.d(TAG, "Write to external storage permission denied.");
                }
                break;
        }
    }

    @Override
    public void onMarkerReached(AudioTrack track) {
        Log.d(TAG, "onMarkerReached: main");
        switch (state) {

            case REMOTE_PREAMBLE:
            case REMOTE_MODULATED:
                // post delay
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        EventBus.getDefault().post(new SendMessageEvent(STOP_RECORDING));
                    }
                }, 250);
                break;
        }
    }

    @Override
    public void onPeriodicNotification(AudioTrack track) {
        Log.d(TAG, "onPeriodicNotification: main");
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Log.d(TAG, "onConnected: start activity");
        EventBus.getDefault().post(new SendMessageEvent(START_ACTIVITY));
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case R.id.menu_settings:
                startActivity(new Intent(this, SettingActivity.class));
                break;
        }

        return super.onOptionsItemSelected(item);
    }


    @Override
    protected void onDestroy() {
        if (preambleTrack!=null) {
            preambleTrack.release();
            preambleTrack = null;
        }

        if (modulatedTrack!=null) {
            modulatedTrack.release();
            modulatedTrack = null;
        }
        mp.release();
        EventBus.getDefault().unregister(this);
        Log.d(TAG, "onDestroy: stop activity");
        EventBus.getDefault().post(new SendMessageEvent(STOP_ACTIVITY));


        if (useFakeWear) {
            fakeWearCommClient.disconnect();
        } else {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    client.disconnect();
                }
            }, 500); // wait for the stop_activity message being sent out.
        }



        super.onDestroy();
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void onChannelOpenedEvent(ChannelOpenedEvent event) {
        if (event.getChannel().getPath().equalsIgnoreCase(SEND_RECORDING)) {

            // use this file to create file name

            fileSentTime = System.currentTimeMillis();
            Date filedate = new Date(fileSentTime);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
            String filename = sdf.format(filedate);
            try {
                if (state == REMOTE_MODULATED) {
                    filename = sdf.format(filedate)+"-modulated"+".raw";
                } else if (state == REMOTE_PREAMBLE) {
                    filename = sdf.format(filedate)+"-preamble"+".raw";
                } else {
                    filename = sdf.format(filedate)+"-recording"+".raw";
                }

                // create file
                rec = new File(audioFolder,filename);
                if (!rec.exists()) {
                    rec.createNewFile();
                }

                // receive file
                // if use fake wear, this event will not be triggered, so this calling is safe.
                event.getChannel().receiveFile(client.getGoogleApiClient(), Uri.fromFile(rec), false);

                Log.d(TAG, "onChannelOpened: Saving data to file:"+rec.getName());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    
    
    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void onFileReceivedEvent(FileReceivedEvent event) {
        Log.d(TAG, "onFileReceivedEvent: file received. time cost:"+(System.currentTimeMillis()-fileSentTime)+"ms");

        Chunk chunk = null;

        if (useFakeWear) {
            chunk = new Chunk(true, IOUtils.loadFromFile(new File("/sdcard/WearLock/tmp.raw"))); // we fix file name in this case.
        } else {
            chunk = new Chunk(true, IOUtils.loadFromFile(rec)); // true big endian
        }
        double[] input = chunk.getDoubleBuffer();

        EventBus.getDefault().post(new MessageEvent(TAG, "load "+input.length+" samples."));

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
            if (sd.getCurrentSPL() > maxSPL ) {
                maxSPL = sd.getCurrentSPL();
                System.out.println("maxSPD updated. " + maxSPL);
                maxStartIdx = Math.max(sw.getStart(),0);
                maxEndIdx = Math.min(sw.getEnd(),chunk.getDoubleBuffer().length);
            }

        }
        CNR = maxSPL - minSPL;
//        cumSPL += maxSPL;
//        cumSPLCtr += 1;
        Log.d(TAG, "onFileReceivedEvent: rough estimate of CNR:"+CNR);
        EventBus.getDefault().post(new MessageEvent(TAG, "maxSPL:"+String.format("%.4f",maxSPL+94.0)
                + ", minSPL:"+String.format("%.4f",minSPL+94.0)
//                +", cum SPL:"+String.format("%.4f", cumSPL/(double)cumSPLCtr)
                +", Peak SNR:"+String.format("%.4f",CNR),"/UPDATE_STATUS"));

        EventBus.getDefault().post(new MessageEvent(
                TAG,
                "Eb/N0 estimated: " + String.format("%.4f",CNR+10*Math.log10(4.0/(Math.log(modem.getConstellationSize())/Math.log(2)))),
                "/UPDATE_STATUS"));


        if (isClipStart) {
            isClipStart = false;
            endIndexArray.add(chunk.getDoubleBuffer().length);
        }


        if (startIndexArray.size() !=  endIndexArray.size()) {
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
            int start =  startIndexArray.get(i);
            int end = endIndexArray.get(i);
            Log.d(TAG, "onFileReceivedEvent: check chunk start:"+start+"end:"+end);
            double[] candidate = chunk.getDoubleBuffer(start,end);
            double xcorrVal = modem.detectPreamble(candidate);
            if (xcorrVal > maxXcorrVal) {
                maxXcorrVal = xcorrVal;
                maxIndex = i;
            }
        }

        // dump

        Chunk toDump = chunk.getSubChunk(startIndexArray.get(maxIndex), endIndexArray.get(maxIndex));
        try {
            toDump.dump("/sdcard/WearLock/chunk_dumped.raw");
        } catch (IOException e) {
            e.printStackTrace();
        }

        EventBus.getDefault().post(new MessageEvent(TAG, "preamble:  "+ String.format("%.4f",maxXcorrVal),"/UPDATE_STATUS"));

        if (maxXcorrVal < 0.05) {
            EventBus.getDefault().post(new MessageEvent(TAG, "detect preamble signal too bad abort task.","/UPDATE_STATUS"));
            return;
        }

        switch (state) {

            case REMOTE_PREAMBLE:
                // replay to send to chose modulation.
                modem.channelProbing(chunk, startIndexArray.get(maxIndex), endIndexArray.get(maxIndex), minSPL);
                //(minSPL+97) // noise

                // 100 50
                // 75 48
                // 50 44
                // 25 39
                // 10 26
//                state = REMOTE_MODULATED;
//                EventBus.getDefault().post(new SendMessageEvent(START_RECORDING));
                break;

            case REMOTE_MODULATED:

                String demodulated = modem.deModulate(chunk, startIndexArray.get(maxIndex), endIndexArray.get(maxIndex));
                // call demodulate
                EventBus.getDefault().post(new MessageEvent(TAG, demodulated,"/demodulated_result"));
                break;

            default:
                break;

        }




    }


//    public void displayFragment(Fragment fragment) {
//        //  && fragment.getClass() != this.activeFragment.getClass()
//        if (this.getSupportFragmentManager().getFragments()!=null ) {
//            FragmentTransaction transaction = this.getSupportFragmentManager().beginTransaction();
//            // warning: this may be wrong.
//            transaction.replace(R.id.fragment_container, fragment);
//            transaction.commit();
//        }
//    }

    public void setInputPin(String inputPin) {
        this.inputPin = inputPin;
    }


}
