package net.yishanhe.ofdm;

import net.yishanhe.utils.DSPUtils;

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
}
