package net.yishanhe.ofdm;

import net.yishanhe.utils.DSPUtils;

import org.apache.commons.math3.complex.Complex;

import java.io.PrintWriter;
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


        try {
            PrintWriter pw = new PrintWriter("/sdcard/WearLock/xcorr_peak_dump.txt");
            for (int i = 0, lag=-maxlag; i < result.length; i++, lag++) {
//            System.out.println(result[i]);
                pw.println(result[i]);
                if (result[i] > maxVal) {
                    maxVal = result[i];
                    delay = lag;
                }
            }
            pw.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

//        for (int i = 0, lag=-maxlag; i < result.length; i++, lag++) {
//            if (result[i] > maxVal) {
//                maxVal = result[i];
//                delay = lag;
//            }
//        }
        System.out.println("synchronized: "+maxVal+", delay: "+delay);
        return delay;
    }

    public static int preambleFreqSync(double[] input, int offset, int length, int range, ArrayList<Integer> pilotSubChannelIdx) {

        // use only the pilots with null channels.
        int index = 0;
        double minPhaseRMS = 1000.0;
        for (int i = -range; i < range+1; i++) {
            System.out.println("test range"+i);
            double[] toFFT = new double[length];
            System.arraycopy(input, offset+i, toFFT, 0, length);
            Complex[] fftBuffer = DSPUtils.fft(toFFT, length);

            double ampSum = 0.0;
            double ampSqSum = 0.0;

            double phaseSum = 0.0;
            double phaseSqSum = 0.0;

            for (int j = 0; j < fftBuffer.length; j++) {
                if (pilotSubChannelIdx.contains(j)) {
                    // get power of pilots
                    ampSum += Math.pow(Math.pow(fftBuffer[j].getImaginary(),2) +  Math.pow(fftBuffer[j].getReal(),2),0.5);
                    ampSqSum += Math.pow(fftBuffer[j].getImaginary(),2) +  Math.pow(fftBuffer[j].getReal(),2);
                    phaseSum += Math.atan2(fftBuffer[j].getImaginary(), fftBuffer[j].getReal());
                    phaseSqSum += Math.pow(Math.atan2(fftBuffer[j].getImaginary(), fftBuffer[j].getReal()),2);
                }
            }

            double rmsAmp = (ampSqSum - ampSum * ampSum /pilotSubChannelIdx.size())/pilotSubChannelIdx.size();
            double rmsPhase = (phaseSqSum - phaseSum * phaseSum /pilotSubChannelIdx.size())/pilotSubChannelIdx.size();

            System.out.println("AmpRMS: "+rmsAmp);
            System.out.println("PhaseRMS: "+rmsPhase);
            if ( rmsPhase < minPhaseRMS) {
                minPhaseRMS = rmsPhase;
                index = i;
            }

        }

        return index;
    }

    public static int cpTimeSync(double[] input, int offset, int lengthWithCP, int lengthCP, int range) {

        int index = 0;
        double maxVal = Double.MIN_VALUE;

        for (int i = -range; i < range+1; i++) {
            double[] packet = new double[lengthWithCP]; // length should be the packet length
            System.arraycopy(input, offset+i, packet, 0, lengthWithCP);

            double result = 0.0;
            double[] head = new double[lengthCP];
            double[] tail = new double[lengthCP];
            System.arraycopy(packet,  0, head, 0, lengthCP);
            System.arraycopy(packet, lengthWithCP-lengthCP, tail, 0, lengthCP);
            double[] xcorr = DSPUtils.xcorr(head, tail, true);
//            for (int j = 0; j < xcorr.length ; j++) {
//                if (xcorr[j]>result) {
                   result  = xcorr[lengthCP];
//                }
//            }
//            for (int j = 0; j < lengthCP; j++) {
//               result += packet[j]*packet[j+lengthWithCP-lengthCP];
//            }
            System.out.println("CP time sync: "+result);
            if (result>maxVal) {
                maxVal = result;
                index = i;
            }
        }
        return index;
    }
}
