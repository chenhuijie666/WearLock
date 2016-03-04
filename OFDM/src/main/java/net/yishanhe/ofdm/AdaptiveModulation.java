package net.yishanhe.ofdm;

import net.yishanhe.utils.DSPUtils;

/**
 * Created by syi on 2/13/16.
 */
public class AdaptiveModulation {

    public static final String MOD_QPSK = "/mod_qpsk";
    public static final String MOD_BASK  = "/mod_bask";
    private static double XCORR_SIGNAL_THRESHOLD = 0.5;
    private static double XCORR_LOS_THRESHOLD = 0.8;


    private double[] preamble;
    private String modMessage = "";
    private double maxVal;

    public AdaptiveModulation(double[] preamble) {
        this.preamble = preamble;
    }

    public AdaptiveModulation(Preamble preamble) {
        this.preamble = preamble.getPreamble();
    }

    public boolean process(Chunk chunk) {
        // Normalized xcorr
        double[] result = DSPUtils.xcorr(preamble, chunk.getDoubleBuffer(), true);

        maxVal = Double.MIN_VALUE;
        int maxlag = (result.length-1)/2;

        for (int i = 0, lag=-maxlag; i < result.length; i++, lag++) {
            if (result[i] > maxVal) {
                maxVal = result[i];
            }
        }

        System.out.println(maxVal);

        if (maxVal < XCORR_SIGNAL_THRESHOLD) {
            return false;
        } else {
            if (maxVal > XCORR_LOS_THRESHOLD) {
                modMessage = MOD_QPSK;
            } else {
                modMessage = MOD_BASK;
            }
            return true;
        }
    }

    public String getModMessage() {
        return modMessage;
    }
}
