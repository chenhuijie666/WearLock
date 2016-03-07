package net.yishanhe.wearlock;

import net.yishanhe.utils.DSPUtils;

/**
 * Created by syi on 12/9/15.
 */
public class SilenceDetector {

    public static final double DEFAULT_SILENCE_THRESHOLD = -70.0; // in db
    private double threshold; //  in db
    private double currentSPL = 0.0;

    public SilenceDetector() {
        this(DEFAULT_SILENCE_THRESHOLD);
    }

    public SilenceDetector(double threshold) {
        this.threshold = threshold;
    }

    /**
     * Checks if the dBSPL level in the buffer falls below a certain threshold.
     *
     * @param buffer
     *            The buffer with audio information.
     * @param silenceThreshold
     *            The threshold in dBSPL
     * @return True if the audio information in buffer corresponds with silence,
     *         false otherwise.
     */
    public boolean isSilence(final double[] buffer, final double silenceThreshold) {
        currentSPL = DSPUtils.soundPressureLevel(buffer);
        System.out.println("current SPL:"+currentSPL);
        return currentSPL < silenceThreshold;
    }

    public boolean isSilence(final double[] buffer) {
        return isSilence(buffer, threshold);
    }

    public double getCurrentSPL() {
        // have to call isSilence first. be cautious.
        return currentSPL;
    }

    public double getCurrentSPL(final double[] buffer) {
        currentSPL = DSPUtils.soundPressureLevel(buffer);
        return currentSPL;
    }
}
