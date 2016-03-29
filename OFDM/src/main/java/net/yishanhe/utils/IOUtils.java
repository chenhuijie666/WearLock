package net.yishanhe.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * Created by syi on 2/11/16.
 */
public class IOUtils {

    public static byte[] loadFromFile(File file)  {
        if (file.getName().contains("raw")) {
            byte[] byteInput = new byte[(int)file.length()];
            FileInputStream fis = null;
            try {
                fis = new FileInputStream(file);
                fis.read(byteInput, 0, byteInput.length);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return byteInput;
        } else if (file.getName().contains("wav")) {
            byte[] byteInput = new byte[(int)file.length()-44];
            try {
                FileInputStream fis = new FileInputStream(file);
                fis.skip(44);
                fis.read(byteInput, 0, byteInput.length);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return byteInput;
        } else {
            throw  new UnsupportedOperationException("Wrong file type.");
        }
    }

    public static byte[] loadFromFile(String filePathName) {
        File file = new File(filePathName);
        return loadFromFile(file);
    }

    // PCM Signed 16 Big-Endian
    public static float[] bytesToFloats(byte[] inBuffer, boolean bigEndian ) {
        float[] outBuffer = new float[inBuffer.length/2];

        int ix = 0;
        int ox = 0;
        for (int i = 0; i < outBuffer.length; i++) {
            if (bigEndian) {
                // big endian
                outBuffer[ox++] =  ((short) ((inBuffer[ix++] << 8) |
                        (inBuffer[ix++] & 0xFF))) * (1.0f / 32767.0f);
//                (inBuffer[ix++] & 0xFF))) * (1.0f / 32768.0f);
            } else {
                // little endian
//                int x = (inBuffer[ix++] & 0xFF) | ((inBuffer[ix++] & 0xFF) << 8);
//                outBuffer[ox++] = (x - 32768) * (1.0f / 32768.0f);
            }
        }

        return outBuffer;
    }

    public static byte[] floatToBytes(float[] inBuffer, boolean bigEndian ) {
        byte[] outBuffer = new byte[inBuffer.length*2];
        int ox = 0;
        int ix = 0;
        for (int i = 0; i < inBuffer.length; i++) {
            if (bigEndian) {
//                int x = (int) (inBuffer[ix++] * 32768.0f);
                int x = (int) (inBuffer[ix++] * 32767.0f);
                outBuffer[ox++] = (byte) (x >>> 8);
                outBuffer[ox++] = (byte) x;
            } else {
//                int x = (int) (inBuffer[ix++] * 32768.0f);
//                int x = (int) (inBuffer[ix++] * 32767.0f);
//                outBuffer[ox++] = (byte) x;
//                outBuffer[ox++] = (byte) (x >>> 8);
            }
        }
        return outBuffer;
    }

    public static float[] doublesToFloats(double[] input) {
        if (input == null)
        {
            return null; // Or throw an exception - your choice
        }
        float[] output = new float[input.length];
        for (int i = 0; i < input.length; i++)
        {
            output[i] = (float)input[i];
        }
        return output;
    }

    public static double[] floatsToDoubles(float[] input) {
        if (input == null)
        {
            return null; // Or throw an exception - your choice
        }
        double[] output = new double[input.length];
        for (int i = 0; i < input.length; i++)
        {
            output[i] = input[i];
        }
        return output;
    }

    public static double[] bytesToDoubles(byte[] input, boolean bigEndian) {
        return floatsToDoubles(bytesToFloats(input, bigEndian));
    }

    public static byte[] doublesToBytes(double[] input, boolean bigEndian) {
       return floatToBytes(doublesToFloats(input), bigEndian);
    }

}
