package net.yishanhe.utils;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;

import java.util.Arrays;

/**
 * Created by syi on 12/11/15.
 */
public class DSPUtils {


    public enum PadPos {
        HEAD, TAIL, MIDDLE, HEAD_TAIL
    }

    public static void fft(double[] input, Complex[] output, int fftSize) {

        if (input.length!=output.length) {
            throw new IllegalArgumentException("Output size is not equal to input size");
        }

        Complex[] tmp = fft(input, fftSize);

        for (int i = 0; i < tmp.length; i++) {
            output[i] = tmp[i];
        }
    }

    public static Complex[] fft(double[] input, int fftSize) {


        if ( fftSize < 0 || fftSize < input.length )  {
            throw new IllegalArgumentException("FFT size should be larger(positive) than the input size (before padding)");
        }

        // TODO: do padding outside or inside?
        if ( (fftSize&(fftSize-1))!=0 ) {
            throw new IllegalArgumentException("FFT size should be a power of 2");
        }

        FastFourierTransformer fft = new FastFourierTransformer(DftNormalization.STANDARD);

        Complex[] output = fft.transform(input, TransformType.FORWARD);

        return output;
    }

    public static void ifft(Complex[] input, Complex[] output, int fftSize) {

        if (input.length!=output.length) {
            throw new IllegalArgumentException("Output size is not equal to input size");
        }

        Complex[] tmp = ifft(input, fftSize);

        for (int i = 0; i < tmp.length; i++) {
            output[i] = tmp[i];
        }
    }

    public static Complex[] ifft(Complex[] input, int fftSize) {

        if ( fftSize < 0 || fftSize < input.length )  {
            throw new IllegalArgumentException("FFT size should be larger(positive) than the input size (before padding)");
        }

        // TODO: do padding outside or inside?
        if ( (fftSize&(fftSize-1))!=0 ) {
            throw new IllegalArgumentException("FFT size should be a power of 2");
        }

        FastFourierTransformer fft = new FastFourierTransformer(DftNormalization.STANDARD);

        Complex[] output = fft.transform(input, TransformType.INVERSE);

        return output;

    }

    public static void ifft(Complex[] input, double[] output, int fftSize) {

        Complex[] tmp = ifft(input, fftSize);
        System.arraycopy(getReals(tmp), 0, output, 0, output.length);
    }

    public static double[] getReals(Complex[] input) {
        double[] output = new double[input.length];
        for (int i = 0; i < output.length; i++) {
            output[i] = input[i].getReal();
        }
        return output;
    }

    public static void gain(double[] input, double gain) {
        for (int i = 0; i < input.length ; i++) {
            float gained = (float) (input[i]*gain);
            if (gained > 1.0f) {
                gained = 1.0f;
            } else if(gained < -1.0f) {
                gained = -1.0f;
            }
            input[i] = gained;
        }
    }

    public static void padZeros( double[] input, double[] output, PadPos padPos) {

        if (output.length <= input.length) {
            throw new IllegalArgumentException("Padding length doesnt match.");
        }

        int paddingLength = output.length;
        Arrays.fill(output, 0);

        switch (padPos) {
            case TAIL:
                for (int i = 0; i < input.length; i++) {
                    output[i] = input[i];
                }
                break;
            case HEAD:
                for (int i = 0; i < input.length; i++) {
                    output[i+paddingLength-input.length] = input[i];
                }
                break;
        }
    }

    public static double[] padZeros( double[] input,  int paddingLength, PadPos padPos) {
        double[] output = new double[paddingLength];
        padZeros(input, output, padPos);
        return output;
    }


    public static Complex[] padZeros (Complex[] input, int paddingLength, PadPos padPos) {
        Complex[] output = new Complex[paddingLength];
        padZeros(input, output, padPos);
        return output;
    }


    public static void padZeros( Complex[] input, Complex[] output, PadPos padPos) {

        if (output.length <= input.length) {
            throw new IllegalArgumentException("Padding length doesnt match.");
        }

        int paddingLength = output.length;

        switch (padPos) {

            case TAIL:
                for (int i = 0; i < output.length; i++) {
                    if (i < input.length) {
                        output[i] = input[i];
                    } else {
                        output[i] = Complex.ZERO;
                    }
                }
                break;

            case HEAD:
                for (int i = 0; i < output.length; i++) {
                    if (i < (output.length-input.length)) {
                        output[i] = Complex.ZERO;
                    } else {
                        output[i] = input[i+input.length-output.length];
                    }
                }
                break;

            case MIDDLE:

                int oldMiddle = (int)Math.ceil((input.length+1)/2);
                // specially tweak this one , to simulate the behavior of fft interpolation.
                for (int i = 0; i < paddingLength; i++) {
                    if (i <= oldMiddle) {
                        output[i] = input[i];
                    } else if (i > oldMiddle && i <= (paddingLength - oldMiddle)) {
                        output[i] = Complex.ZERO;
                    } else {
                        output[i] = input[i - paddingLength + input.length];
                    }
                }
                break;

        }
    }

    public static Complex[] interpolate(Complex[] input, int interpolatedLength) {

        if ((input.length&(input.length-1))!=0) {
            throw new IllegalArgumentException("input length should be 2's exponential.");
        }

        int newLength = input.length * interpolatedLength;
        int nyqst = (int)Math.ceil((input.length+1)/2.0);
        Complex[] interpolationBuffer1; // fft forward result
        Complex[] interpolationBuffer2; // interpolated in freq domain.
        Complex[] interpolatedResultBuffer; // interpolated result.
        FastFourierTransformer fft = new FastFourierTransformer(DftNormalization.STANDARD);

        // interpolation starts from here.
        interpolationBuffer1 = fft.transform(input, TransformType.FORWARD);
//        for (int i = 0; i < interpolationBuffer1.length; i++) {
//           System.out.println(interpolationBuffer1[i].toString());
//        }
        interpolationBuffer2 = padZeros(interpolationBuffer1, newLength, PadPos.MIDDLE);
        // mimic the behavior of matlab
        // make the spectrum symmetric
        if ((input.length&0x01)==0){
            interpolationBuffer2[nyqst-1] =  interpolationBuffer2[nyqst-1].divide(2.0);
            interpolationBuffer2[nyqst-1+newLength-input.length] = interpolationBuffer2[nyqst-1];
        }
//        for (int i = 0; i < interpolationBuffer2.length; i++) {
//            System.out.println(interpolationBuffer2[i].toString());
//        }
        interpolatedResultBuffer = fft.transform(interpolationBuffer2, TransformType.INVERSE);
        for (int i = 0; i < interpolatedResultBuffer.length; i++) {
            interpolatedResultBuffer[i] = interpolatedResultBuffer[i].multiply(newLength).divide(input.length);
//            System.out.println(interpolatedResultBuffer[i].toString());
        }
        System.out.println("dsputils interpolated length: "+ interpolatedResultBuffer.length);
        return interpolatedResultBuffer;
    }

    public static void interpolate(Complex[] input, Complex[] output, int interpolatedLength) {

        if (output.length <= input.length*interpolatedLength) {
            throw new IllegalArgumentException("Interpolating length doesnt match.");
        }

        Complex[] tmp = interpolate(input, interpolatedLength);

        for (int i = 0; i < tmp.length; i++) {
            output[i] = tmp[i];
        }
    }

    public static double[] xcorr(double a[], double b[], boolean normalized) {

        if (normalized) {
            // scale to +1 maybe
            double aNorm = vectorNorm(a);
            double bNorm = vectorNorm(b);
            a = vectorDivide(a, aNorm);
            b = vectorDivide(b, bNorm);
        }

        int maxlag = Math.max(a.length, b.length);
        double[] y = new double[2*maxlag+1];
        Arrays.fill(y, 0);

        /**
         * |----- a ------|------ b -------|
         * -a             0               b-1   lag
         * idx of y       |----------------|-----------------| 2*maxlag+1
         * if maxlag = b.length idx = 1
         * else idx = a.length-b.length+1
         * slide a to convolve b to the right.
         * idx reduces some zero computation
         * it simulates append zeros at the beginning of b
         * to make it same length as a (if b<a)
         */
        for(int lag = b.length-1, idx = maxlag-b.length+1;
            lag > -a.length; lag--, idx++)
        {
            if(idx < 0)
                continue;

            if(idx >= y.length)
                break;

            // where do the two signals overlap?
            int start = 0;
            // we can't start past the left end of b
            if(lag < 0)
            {
                //System.out.println("b");
                start = -lag;
            }

            int end = a.length-1;
            // we can't go past the right end of b
            if(end > b.length-lag-1)
            {
                end = b.length-lag-1;
                //System.out.println("a "+end);
            }

            //System.out.println("lag = " + lag +": "+ start+" to " + end+"   idx = "+idx);
            for(int n = start; n <= end; n++)
            {
                //System.out.println("  bi = " + (lag+n) + ", ai = " + n);
                y[idx] += a[n]*b[lag+n];

            }
            //System.out.println(y[idx]);
        }

        return(y);

    }

    public static double[] vectorMinus(double[] a, double b) {
        double[] result = new double[a.length];
        for (int i = 0; i < a.length; i++) {
            result[i] = a[i]-b;
        }
        return result;
    }

    public static double vectorMean(double[] a) {
        double sum = 0.0;
        for (int i = 0; i < a.length; i++) {
            sum += a[i];
        }
        return sum/(double)a.length;
    }


    public static double[] vectorDivide(double[] a, double b) {
        double[] result = new double[a.length];
        for (int i = 0; i < a.length; i++) {
            result[i] = a[i]/b;
        }
        return result;
    }

    public static double vectorNorm(double[] a) {
        double sqareSum = 0.0;
        for (int i = 0; i < a.length; i++) {
            sqareSum += a[i] * a[i];
        }
        return Math.sqrt(sqareSum);
    }

    public static int NextPow2Exp(int n) {
        return (n == 0 ? 0 : 32 - Integer.numberOfLeadingZeros(n - 1));
    }

    public static int NextPow2(int n) {
        return (int)Math.pow(2, NextPow2Exp(n));
    }

    /**
     * Calculates the local (linear) energy of an audio buffer.
     *
     * @param buffer
     *            The audio buffer.
     * @return The local (linear) energy of an audio buffer.
     */
    public static double localEnergy(final double[] buffer) {
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
    public static double soundPressureLevel(final double[] buffer) {
        // reference pressure pref = 0.00002;
        return 10.0 * Math.log10(localEnergy(buffer)/buffer.length) + 94;
    }



}
