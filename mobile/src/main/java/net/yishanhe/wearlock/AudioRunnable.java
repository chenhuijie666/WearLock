package net.yishanhe.wearlock;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

import java.util.Arrays;

/**
 * Created by syi on 2/25/16.
 */
public class AudioRunnable implements Runnable {

    private final static String TAG = "AudioRunnable";
    private final AudioTrack track;
    private short[] toSend;

    public AudioRunnable(int sampleRateInHz, short[] samples, AudioTrack.OnPlaybackPositionUpdateListener listener) {

        int minBufferSize = AudioTrack.getMinBufferSize(sampleRateInHz, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
//        int paddingLen = 2048;
//        toSend = new short[samples.length+paddingLen];
//        Arrays.fill(toSend, (short)0);
//        System.arraycopy(samples, 0, toSend, paddingLen/2, samples.length);
        toSend = new short[minBufferSize*4+samples.length];
        Arrays.fill(toSend, (short)0);
        System.arraycopy(samples,0,toSend,minBufferSize*2,samples.length);
        Log.d(TAG, "AudioRunnable: minBufferSize "+minBufferSize+" audio length: "+(toSend.length*1000/44100)+"ms");
        this.track = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRateInHz, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, toSend.length*2, AudioTrack.MODE_STATIC);
        track.write(toSend, 0, toSend.length);
        track.setPlaybackPositionUpdateListener(listener);
    }


    @Override
    public void run() {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
        playStaticSound();
    }


    private synchronized void playStaticSound() {
//        track.reloadStaticData();
        switch (track.getPlayState()) {
            case AudioTrack.PLAYSTATE_PAUSED:
                track.stop();
                track.reloadStaticData();
                track.setNotificationMarkerPosition(toSend.length/2);
                track.play();
                break;
            case AudioTrack.PLAYSTATE_PLAYING:
                track.pause();
                track.stop();
                track.reloadStaticData();
                track.setNotificationMarkerPosition(toSend.length/2);
                track.play();
                break;
            case AudioTrack.PLAYSTATE_STOPPED:
                track.reloadStaticData();
                track.setNotificationMarkerPosition(toSend.length/2);
                track.play();
                break;
            default:
                break;
        }
    }

    public synchronized void release() {
        track.setNotificationMarkerPosition(0);
        track.setPlaybackPositionUpdateListener(null);
        track.release();
    }


}
