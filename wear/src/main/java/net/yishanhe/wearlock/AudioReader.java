package net.yishanhe.wearlock;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Created by syi on 2/24/16.
 * credit ot HermitAndroid
 */
public class AudioReader {

    private static final String TAG = "AudioReader";

    // thread should be running
    private boolean running = false;

    // buffer for read from audio source.
    private short[] buffer = new short[1024];

    // the thread, reading from the mic.
    private Thread readerThread = null;

    private AudioRecord audioInput;


    private AudioReaderListener listener;



    public AudioReader() {

    }

    public void registerListener(AudioReaderListener listener){
        this.listener = listener;
    }

    public void unregisterListener() {
        this.listener = null;
    }


    public void startReader(int sampleRateInHz, final File file) {
        Log.i(TAG, "startReader: started.");
        synchronized (this) {

            // size in bytes.
            int minBufferSize = AudioRecord.getMinBufferSize(sampleRateInHz,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT); // may not need this 2 here.

            if (minBufferSize < 0) {
                Log.e(TAG, "startReader: parameter not supported or invalid. error code:"+minBufferSize);
                return;
            }

            Log.i(TAG, "startReader: the length of audio input buffer is "+minBufferSize);

            if (minBufferSize > buffer.length) {
                buffer = new short[minBufferSize];
            }

            audioInput = new AudioRecord(MediaRecorder.AudioSource.MIC,
                    sampleRateInHz,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBufferSize*2);

            running = true;

            readerThread = new Thread(new Runnable() {
                @Override
                public void run() {
                   readerRun(file);
                }
            }, TAG);
            readerThread.start();
        }
    }

    public void stopReader() {
        Log.i(TAG, "stopReader: signal stop.");
        synchronized (this) {
            running = false;
        }
        try {
            if (readerThread != null ) {
                readerThread.join();
            }
        } catch (InterruptedException e) {}

        readerThread = null;
        synchronized (this) {
            if (audioInput != null) {
                audioInput.release();
                audioInput = null;
            }
        }
        Log.i(TAG, "stopReader: thread stopped.");

    }
    public void readerRun(File file) {
        int readSize;

        int timeout = 500;
        try {
            while (timeout > 0 && audioInput.getState() != AudioRecord.STATE_INITIALIZED) {
                Thread.sleep(100);
                timeout -= 100;
            }
        } catch (InterruptedException e) {}

        if (audioInput.getState() != AudioRecord.STATE_INITIALIZED ) {
            Log.e(TAG, "readerRun: audio redear failed to init.");
            running = false;
            return;
        }

        // IO
        DataOutputStream output = null;
        try {
            output = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)));
            Log.i(TAG, "readerRun: start recording");
            audioInput.startRecording();
            while (running) {

                if (!running) break;

                readSize = audioInput.read(buffer, 0, buffer.length);

                if (readSize < 0) {
                    Log.e(TAG, "readerRun: audio read fail. error code"+readSize);
                    // callback
                    running = false;
                    break;
                }

                for (int i = 0; i < readSize; i++) {
                    output.writeShort(buffer[i]); // big endian
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            Log.i(TAG, "Reader: Stop Recording");
            if (audioInput.getState() == AudioRecord.RECORDSTATE_RECORDING)
                audioInput.stop();

            if (output != null) {
                try {
                    output.flush();
                    Log.d(TAG, "flush output");
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    try {
                        output.close();
                        Log.d(TAG, "close output");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }


    }

    public boolean isRunning() {
        return running;
    }
}
