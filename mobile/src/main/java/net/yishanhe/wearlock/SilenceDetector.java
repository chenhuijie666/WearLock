package net.yishanhe.wearlock;

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
     * Calculates the local (linear) energy of an audio buffer.
     *
     * @param buffer
     *            The audio buffer.
     * @return The local (linear) energy of an audio buffer.
     */
    private double localEnergy(final double[] buffer) {
        double power = 0.0D;
        for (double element : buffer) {
            power += element * element;
        }
        return power;
    }

    /**
     * Returns the dBSPL for a buffer.
     *
     * @param buffer
     *            The buffer with audio information.
     * @return The dBSPL level for the buffer.
     */
    private double soundPressureLevel(final double[] buffer) {
        double value = Math.pow(localEnergy(buffer), 0.5);
        value = value / buffer.length;
        return linearToDecibel(value);
    }

    /**
     * Converts a linear to a dB value.
     *
     * @param value
     *            The value to convert.
     * @return The converted value.
     */
    private double linearToDecibel(final double value) {
        return 20.0 * Math.log10(value);
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
        currentSPL = soundPressureLevel(buffer);
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
}
