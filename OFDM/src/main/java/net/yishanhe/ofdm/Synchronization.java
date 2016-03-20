package net.yishanhe.ofdm;

import net.yishanhe.utils.DSPUtils;

import org.apache.commons.math3.complex.Complex;

import java.util.ArrayList;

/**
 * Created by syi on 2/13/16.
 */
public class Synchronization {

    public static int preambleTimeSync(double[] ref, double[] input) {
        if (input.length < ref.length) {
            throw new IllegalArgumentException("Reference is longer than input.");
        }
        /*
        * |----- a ------|------ b -------|
        * -a             0               b-1   lag
        * idx of y       |----------------|-----------------| 2*maxlag+1
        */

        double[] result = DSPUtils.xcorr(ref, input, true);
        int delay = 0;
        double maxVal = Double.MIN_VALUE;
        int maxlag = Math.max(ref.length, input.length);

        for (int i = 0, lag=-maxlag; i < result.length; i++, lag++) {
            if (result[i] > maxVal) {
                maxVal = result[i];
                delay = lag;
            }
        }
        System.out.println("synchronized: "+maxVal);
        return delay;
    }

    public static int preambleFreqSync(double[] input, int offset, int length, int range, ArrayList<Integer> pilotSubChannelIdx) {

        // use only the pilots with null channels.
        int maxIndex = 0;
        double maxRMS = -1000.0;
        for (int i = -range; i < range+1; i++) {
            double[] toFFT = new double[length];
            System.arraycopy(input, offset+i, toFFT, 0, length);
            Complex[] fftBuffer = DSPUtils.fft(toFFT, length);
            double rms = 0.0;

            for (int j = 0; i < fftBuffer.length; j++) {
                if (pilotSubChannelIdx.contains(j)) {
                    // get power of pilots
                    rms += Math.pow(fftBuffer[j].getImaginary(),2) +  Math.pow(fftBuffer[j].getReal(),2);
                }
            }

            rms = rms/pilotSubChannelIdx.size();

            if (rms>maxRMS) {
                maxRMS = rms;
                maxIndex = i;
            }
        }

        return maxIndex;
    }
}
