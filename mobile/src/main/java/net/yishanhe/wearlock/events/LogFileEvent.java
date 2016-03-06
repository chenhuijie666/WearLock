package net.yishanhe.wearlock.events;

/**
 * Created by shanh on 2016/3/4.
 * log different event into different file
 *
 * folder structure
 *\WearLock
 *  \audio\{timestamp}-{audio-type}-{}.raw
 *  \log
 *      \{timestamp}.log
 *      in side the log file
 *      csv file
 *      timestamp, location, modulation, distance, SNR,  errorbits, bits, preamble_xcorr, direction
 **/
public class LogFileEvent {
    long timestamp;
    int distanceInCentimeter;
    String location;
    String modulation;
    double SNR;
    int errorBits;
    int bits;
    double preambleXcorr;
    String direction;

}
