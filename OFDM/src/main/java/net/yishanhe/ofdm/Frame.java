package net.yishanhe.ofdm;

import org.apache.commons.math3.complex.Complex;

import java.util.ArrayList;

/**
 * Created by syi on 2/13/16.
 */
public class Frame {

    // @TODO: need a better scheme to generate frames.

    private int fftSize;
    private ArrayList<Integer> pilotSubChannelIdx;
    private ArrayList<Integer> dataSubChannelIdx;

    public Frame(int fftSize, ArrayList<Integer> pilotSubChannelIdx, ArrayList<Integer> dataSubChannelIdx) {
        this.fftSize = fftSize;
        this.pilotSubChannelIdx = pilotSubChannelIdx;
        this.dataSubChannelIdx = dataSubChannelIdx;
    }

    public ArrayList<Complex[]> s2p(Complex[] serial) {

        int dataBlockSize = dataSubChannelIdx.size();
        int pilotBlockSize = pilotSubChannelIdx.size();

        int numberOfFrames = (serial.length + dataBlockSize - 1) / dataBlockSize;

        ArrayList<Complex[]> parallel = new ArrayList<>(numberOfFrames);

        for (int i = 0; i < numberOfFrames; i++) {

            Complex[] singleOFDMSymbolBuffer = new Complex[fftSize];

            int pilotCounter = 0;
            int dataCounter = 0;

            for (int j = 0; j < fftSize; j++) {
                if (dataSubChannelIdx.contains(j)) {
                    // data channel
                    if ((i*dataBlockSize+dataCounter)<serial.length) {
                        singleOFDMSymbolBuffer[j] = serial[i * dataBlockSize+ dataCounter];
                    } else {
                        singleOFDMSymbolBuffer[j] = new Complex(0.0, 0.0);
                    }
                    dataCounter ++;
                    if (dataCounter>dataBlockSize) {
                        throw new IllegalArgumentException("Data size overflow.");
                    }
                } else if (pilotSubChannelIdx.contains(j)) {
                    // pilot channel
                    singleOFDMSymbolBuffer[j] = new Complex(1.0, 0.0);
                    pilotCounter ++;
                    if (pilotCounter>pilotBlockSize) {
                        throw new IllegalArgumentException("Pilot size overflow.");
                    }
                } else {
                    // null channel
                    singleOFDMSymbolBuffer[j] = new Complex(0.0, 0.0);
                }
            }

//            for (int k = 0; k < singleOFDMSymbolBuffer.length; k++) {
//               System.out.println("generated OFDM channel:"+k+" value:"+singleOFDMSymbolBuffer[k].toString());
//            }
            parallel.add(singleOFDMSymbolBuffer);
        }

        return parallel;
    }



    public Complex[] p2s(ArrayList<Complex[]> parallel) {
        int dataBlockSize = dataSubChannelIdx.size();
        int numberOfFrames =  parallel.size();
        Complex[] serial = new Complex[numberOfFrames*dataBlockSize];
        int serialCounter = 0;
        for (int i = 0; i < numberOfFrames; i++) {
            Complex[] oneFrame = parallel.get(i);
            for (int j = 0; j < oneFrame.length; j++) {
                if (dataSubChannelIdx.contains(j)) {
                    // data channel
                    serial[serialCounter++]=oneFrame[j];
                }
            }
        }
        return serial;
    }


}
