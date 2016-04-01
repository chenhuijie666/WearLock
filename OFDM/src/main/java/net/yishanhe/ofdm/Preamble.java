package net.yishanhe.ofdm;


import net.yishanhe.utils.DSPUtils;
import net.yishanhe.utils.IOUtils;

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
        double[] result = DSPUtils.xcorr(input, preamble, true);

        double maxVal = Double.MIN_VALUE;
        int maxlag = (result.length-1)/2;
//        int delay = 0;

        for (int i = 0, lag=-maxlag; i < result.length; i++, lag++) {
            if (result[i] > maxVal) {
                maxVal = result[i];
//                delay = i;
            }
        }


        // calculate delay spreading.

//        double[] delayInput = new double[postPreambleGuardSize];
//        if ((result.length-delay)<postPreambleGuardSize) {
//            delay = result.length - postPreambleGuardSize;
//        }
//        System.arraycopy(result, delay, delayInput,0, postPreambleGuardSize);
//        getRMSDelaySpreading(delayInput);


        return maxVal;

    }

    public static double[] scaleAbsDoubles(double[] input) {
        double[] scaled = new double[input.length];
        double maxVal = Double.MIN_VALUE;

        for (int i = 0; i < input.length; i++) {
            if (maxVal < input[i]) {
                maxVal = input[i];
            }
        }

        for (int i = 0; i < scaled.length; i++) {
            scaled[i] = Math.abs(input[i]/maxVal);
        }
        return scaled;
    }

    private void getRMSDelaySpreading(double[] input) {
        int len = input.length;
        double[] scaled_input = scaleAbsDoubles(input);

        double meanDelay = 0.0;
        double rmsDelay = 0.0;
        for (int j = 0; j<len; j++){
            meanDelay += j*scaled_input[j];
        }
        meanDelay = meanDelay/postPreambleGuardSize;
        System.out.println("Mean Delay Spreading: " + meanDelay);

        for (int j = 0; j < postPreambleGuardSize; j++) {
            rmsDelay += (j-meanDelay)*(j-meanDelay)*scaled_input[j];
        }
        System.out.println("RMS Delay Sum: " + rmsDelay);
        rmsDelay = Math.pow( rmsDelay/postPreambleGuardSize ,0.5)/44100.0;
        System.out.println("RMS Delay Spreading: " + rmsDelay);

    }




}
