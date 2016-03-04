package net.yishanhe.ofdm;

import net.yishanhe.utils.IOUtils;

import org.apache.commons.math3.complex.Complex;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Created by syi on 2/11/16.
 * using double hereafter.
 */
public class Chunk {

    private boolean bigEndian;

    // time domain
    private double[] doubleBuffer = null;
    // freq domain
    private Complex[] complexBuffer = null;

    public Chunk() {
    }

    public Chunk( String fileNamePath, boolean bigEndian) throws IOException {
        this.bigEndian = bigEndian;
        this.doubleBuffer = IOUtils.bytesToDoubles(IOUtils.loadFromFile(fileNamePath), bigEndian);
    }


    public Chunk(boolean bigEndian, byte[] byteBuffer) {
        this.bigEndian = bigEndian;
        this.doubleBuffer = IOUtils.bytesToDoubles(byteBuffer, bigEndian);
    }

    public Chunk(double[] doubleBuffer) {
        this.bigEndian = true;
        this.doubleBuffer = doubleBuffer;
    }

    // @TODO: to add more constructors

    // toBytes
    public byte[] toBytes() {
        // update bytes
        return toBytes(bigEndian);
    }

    public byte[] toBytes(boolean bigEndian) {
       return IOUtils.doublesToBytes(doubleBuffer, bigEndian);
    }



    // dump methods

    public static void dump(String filePathName, byte[] byteBuffer) throws IOException {
        File file = new File(filePathName);
        dump(file, byteBuffer);
    }

    public void dump(String filePathName) throws IOException {

        File file = new File(filePathName);
        dump(file);
    }

    public void dump(File file) throws IOException {
        dump(file, toBytes());
    }

    public static void dump(File file, byte[] byteBuffer) throws IOException {
        FileOutputStream fos = new FileOutputStream(file);
        fos.write(byteBuffer);
        fos.close();
    }

    // operation on double buffer
    public void skip(int delay) {
        double[] oldDoubleBuffer = getDoubleBuffer();
        int newLength = oldDoubleBuffer.length - Math.abs(delay);
        double[] newDoubleBuffer = new double[newLength];

        if (delay >= 0) {
            System.arraycopy(oldDoubleBuffer, delay, newDoubleBuffer, 0, newDoubleBuffer.length);
        } else {
            // skip from end
            System.arraycopy(oldDoubleBuffer, 0, newDoubleBuffer, 0, newDoubleBuffer.length);
        }
        setDoubleBuffer(newDoubleBuffer);
    }

    public void prepend(double[] prependent) {
        double[] oldDoubleBuffer = getDoubleBuffer();
        int newLength = oldDoubleBuffer.length + prependent.length;
        double[] newDoubleBuffer = new double[newLength];
        System.arraycopy(prependent, 0, newDoubleBuffer, 0, prependent.length);
        System.arraycopy(oldDoubleBuffer, 0, newDoubleBuffer, prependent.length, newDoubleBuffer.length);
        setDoubleBuffer(newDoubleBuffer);
    }

    public void append(double[] appendent) {
        double[] oldDoubleBuffer = getDoubleBuffer();
        int newLength = oldDoubleBuffer.length + appendent.length;
        double[] newDoubleBuffer = new double[newLength];
        System.arraycopy(oldDoubleBuffer, 0, newDoubleBuffer, 0, oldDoubleBuffer.length);
        System.arraycopy(appendent, 0, newDoubleBuffer, oldDoubleBuffer.length, appendent.length);
        setDoubleBuffer(newDoubleBuffer);
    }

    // getter and setter
    public boolean isBigEndian() {
        return bigEndian;
    }

    public void setBigEndian(boolean bigEndian) {
        this.bigEndian = bigEndian;
    }

    public double[] getDoubleBuffer() {
        return doubleBuffer;
    }

    public double[] getDoubleBuffer(int start, int end) {

        double[] output = new double[end-start];
        for (int i = start; i < end; i++) {
            output[i-start] = doubleBuffer[i];
        }
        return output;

    }

    public Chunk getSubChunk(int start, int end) {
        return new Chunk(getDoubleBuffer(start, end));
    }

    public void setDoubleBuffer(double[] doubleBuffer) {
        this.doubleBuffer = doubleBuffer;
    }

    public Complex[] getComplexBuffer() {
        return complexBuffer;
    }

    public void setComplexBuffer(Complex[] complexBuffer) {
        this.complexBuffer = complexBuffer;
    }
}
