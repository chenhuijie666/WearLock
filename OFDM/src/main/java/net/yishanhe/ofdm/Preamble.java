package net.yishanhe.ofdm;


import net.yishanhe.utils.DSPUtils;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Exchanger;

/**
 * Created by syi on 2/12/16.
 */
public class Preamble {

    // @TODO: PNseq imlementation
    public enum PreambleType {
        FreqSweep, PNSeq
    }

    private PreambleType type;

    private int preambleSize;
    private int postPreambleGuardSize;
    private double startFreq;
    private double freqRange;
    private double samplingRate;

    double[] preamble; // this.preamble = makePreamble(startFreq, freqRange, preambleSize, samplingRate);

    public Preamble(int preambleSize, int postPreambleGuardSize, double startFreq, double freqRange, double samplingRate) {
        this.type = PreambleType.FreqSweep;
        this.preambleSize = preambleSize;
        this.postPreambleGuardSize = postPreambleGuardSize;
        this.startFreq = startFreq;
        this.freqRange = freqRange;
        this.samplingRate = samplingRate;
        this.preamble = makePreamble(startFreq, freqRange, preambleSize, samplingRate);
    }

    public void prependPreamble(Chunk chunk) {
        double[] postPreambleGuardBuffer = new double[postPreambleGuardSize];
        Arrays.fill(postPreambleGuardBuffer, 0.0);
        chunk.prepend(postPreambleGuardBuffer);
        chunk.prepend(preamble);
    }

    public void removePreamble(Chunk chunk) {
        chunk.skip(postPreambleGuardSize+preambleSize);
    }

    public static double[] makePreamble(double startFreq, double freqRange, int preambleSize, double samplingRate) {
        // get a 7k tone currently
        double[] preamble = new double[preambleSize];

        // using Ed's scheme
        // TODO: fade in fade out. in a smart way
        int thirdIndex = preambleSize/3;
        final double volDelta = 1.0/(double)thirdIndex;
        double volume = 0;
        double freqDelta = freqRange/preambleSize;
//        double freqDelta = freqRange*2/preambleSize;

        for (int i =0; i < preamble.length; i++) {
            if (i < thirdIndex) {
                volume += volDelta;
            }

            if (i > thirdIndex*2) {
                volume -= volDelta;
            }

            // @TODO: try increasing then decreasing chirp.
            preamble[i] = getSample(i, startFreq+freqDelta*i, 0, samplingRate) * volume;

        }

        return preamble;
    }

    private static double getSample(int sampleIndex, double subCarrierFreq, double subCarrierPhase, double samplingRate){

        if (subCarrierFreq > samplingRate/2) {
            throw new IllegalArgumentException("Frequency above nyquist.");
        }

        return Math.sin( 2 * Math.PI * sampleIndex * (subCarrierFreq/samplingRate) + subCarrierPhase);
    }

    public double[] getPreamble() {
        return preamble;
    }

    // @TODO: preamble detection
    public double detectPreamble(double[] input) {
        double[] result = DSPUtils.xcorr(preamble, input, true);

        double maxVal = Double.MIN_VALUE;
        int maxlag = (result.length-1)/2;

//        try {
//            PrintWriter pw = new PrintWriter("/sdcard/WearLock/xcorr_peak_dump.txt");
            for (int i = 0, lag=-maxlag; i < result.length; i++, lag++) {
//            System.out.println(result[i]);
//                pw.println(result[i]);
                if (result[i] > maxVal) {
                    maxVal = result[i];
                }
            }
//            pw.close();
//        } catch (Exception e) {
//            e.printStackTrace();
//        }



//        ArrayList<Map<Integer, Double>> peaks = DSPUtils.peak_detection(result, );
//        System.out.println(maxVal);
        return maxVal;

    }




}
