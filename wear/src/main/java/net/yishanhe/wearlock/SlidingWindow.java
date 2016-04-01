package net.yishanhe.wearlock;

import java.util.Iterator;

/**
 * Created by syi on 12/9/15.
 */
public class SlidingWindow implements Iterator<double[]> {

    private int chunkSize;
    private int stepSize;
    private long remainSize = 0;
    private int start;
    private int end;
    private double[] input;

    public SlidingWindow(int chunkSize, int stepSize) {
        this.chunkSize = chunkSize;
        this.stepSize = stepSize;
    }

    public SlidingWindow(int chunkSize, int stepSize, double[] input) {
        this.chunkSize = chunkSize;
        this.stepSize = stepSize;
        this.start = 0;
        this.end = 0;
        this.remainSize = input.length;
        this.input = input;
    }

    public void setInput(double[] input) {
        this.start = 0;
        this.end = 0;
        this.remainSize = input.length;
        this.input = input;
    }

    @Override
    public boolean hasNext() {
        if (remainSize > 0){
            return true;
        } else {
            return false;
        }
    }

    @Override
    public double[] next() {
        int toChunk = (remainSize > chunkSize) ? chunkSize : (int) remainSize;
        double[] chunk = new double[toChunk];
        end = start + toChunk;
        for (int i = start; i < end; i++) {
            chunk[i-start] = input[i];
        }
//        System.out.println(""+start+","+end);
        start += stepSize;
        remainSize -= stepSize;
        return chunk;
    }

    @Override
    public void remove() {

    }

    public int getStart() {
        return start-stepSize;
    }

    public int getEnd() {
        return end;
    }
}
