package net.yishanhe.ofdm;


import net.yishanhe.utils.DSPUtils;

import org.apache.commons.math3.complex.Complex;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by syi on 2/12/16.
 * @TODO: add a dump method to dump the channel data.
 */
public class Channel {

    private int fftSize;
    private double samplingRate;
    private ArrayList<Integer> pilotSubChannelIdx;
    private ArrayList<Integer> dataSubChannelIdx;

    private Complex[] channelBuffer;
    private ArrayList<SubChannel> subChannels;



    public Channel(int fftSize, double samplingRate, ArrayList<Integer> pilotSubChannelIdx, ArrayList<Integer> dataSubChannelIdx) {
        this.fftSize = fftSize;
        this.samplingRate = samplingRate;
        this.pilotSubChannelIdx = pilotSubChannelIdx;
        this.interpolationSize = pilotSubChannelIdx.get(1)-pilotSubChannelIdx.get(0);
        this.dataSubChannelIdx = dataSubChannelIdx;
        this.subChannels = new ArrayList<>(fftSize);
    }



    public void updateSubChannels() {
        if (channelBuffer!=null){
            this.subChannels = new ArrayList<>(fftSize);
            for (int i = 0; i < fftSize; i++) {
                subChannels.add(new SubChannel(
                        i,
                        samplingRate*i/fftSize,
                        pilotSubChannelIdx.contains(i),
                        dataSubChannelIdx.contains(i),
                        !pilotSubChannelIdx.contains(i) && !dataSubChannelIdx.contains(i),
                        channelBuffer[i]
                ));
            }

        }
    }

    public Complex[] getPilotSubChannelBuffer() {
        Complex[] pilotSubChannelBuffer = new Complex[pilotSubChannelIdx.size()];
        for (int i = 0; i < pilotSubChannelBuffer.length; i++) {
            pilotSubChannelBuffer[i] = subChannels.get(pilotSubChannelIdx.get(i)).getValue();
        }
//        System.out.println("channel getpilotsubchannelbuffer size: "+pilotSubChannelBuffer.length);
        return pilotSubChannelBuffer;
    }

    public Complex[] getDataSubChannelBuffer() {
        Complex[] dataSubChannelBuffer = new Complex[dataSubChannelIdx.size()];
        for (int i = 0; i < dataSubChannelBuffer.length; i++) {
            dataSubChannelBuffer[i] = subChannels.get(dataSubChannelIdx.get(i)).getValue();
        }
        return dataSubChannelBuffer;
    }

    public ArrayList<SubChannel> getSubChannels() {
        return subChannels;
    }

    //        return samplingRate*i/fftSize;
//        return (int)((fftSize+1)*freq/samplingRate);

    public SubChannel getSubChannelAtIdx(int i){
        return subChannels.get(i);
    }

    public SubChannel getSubChannelAtFreq(double freq) {
        int idx = (int)((fftSize+1)*freq/samplingRate);
        return getSubChannelAtIdx(idx);
    }

    public Complex[] getChannelBuffer() {
        return channelBuffer;
    }

    public void setChannelBuffer(Complex[] channelBuffer) {
        if (channelBuffer.length!=fftSize) {
            throw new IllegalArgumentException("FFT size does not fit.");
        }
        this.channelBuffer = channelBuffer;
        // update subChannels
        updateSubChannels();
    }

    public ArrayList<Integer> getPilotSubChannelIdx() {
        return pilotSubChannelIdx;
    }

    public void setPilotSubChannelIdx(ArrayList<Integer> pilotSubChannelIdx) {
        this.pilotSubChannelIdx = pilotSubChannelIdx;
    }

    public ArrayList<Integer> getDataSubChannelIdx() {
        return dataSubChannelIdx;
    }

    public void setDataSubChannelIdx(ArrayList<Integer> dataSubChannelIdx) {
        this.dataSubChannelIdx = dataSubChannelIdx;
    }

    public class SubChannel {
        private int idx;
        private double freq;
        private boolean isPilot;
        private boolean isData;
        private boolean isNull;
        private Complex value;
        private Complex est;

        public SubChannel(int idx, double freq, boolean isPilot, boolean isData, boolean isNull, Complex value) {
            this.idx = idx;
            this.freq = freq;
            this.isPilot = isPilot;
            this.isData = isData;
            this.isNull = isNull;
            this.value = value;
        }

        public int getIdx() {
            return idx;
        }

        public void setIdx(int idx) {
            this.idx = idx;
        }

        public double getFreq() {
            return freq;
        }

        public void setFreq(double freq) {
            this.freq = freq;
        }

        public boolean isPilot() {
            return isPilot;
        }

        public void setPilot(boolean pilot) {
            isPilot = pilot;
        }

        public boolean isData() {
            return isData;
        }

        public void setData(boolean data) {
            isData = data;
        }

        public boolean isNull() {
            return isNull;
        }

        public void setNull(boolean aNull) {
            isNull = aNull;
        }

        public Complex getValue() {
            return value;
        }

        public void setValue(Complex value) {
            this.value = value;
        }

        public Complex getEst() {
            return est;
        }

        public void setEst(Complex est) {
            this.est = est;
        }
    }


    private int interpolationSize;

    public int getInterpolationSize() {
        return interpolationSize;
    }

    public void setInterpolationSize(int interpolationSize) {
        this.interpolationSize = interpolationSize;
    }

    public void estimate() {
        // printed out here
        Complex[] channelEstBuffer  =  DSPUtils.interpolate(getPilotSubChannelBuffer(), interpolationSize);
//        System.out.println("channel est len: "+channelEstBuffer.length); // 32 is wrong. fix
//        System.out.println("subchannels len: "+subChannels.size());


        for (int i = 0;  i < channelEstBuffer.length; i++) {
//            System.out.println("update est for "+subChannels.get(i).getIdx()+", old "+subChannels.get(i+pilotSubChannelIdx.get(0)).getValue().toString()+", est "+channelEstBuffer[i].toString()
//                    +", equalized "+subChannels.get(i+pilotSubChannelIdx.get(0)).getValue().multiply(channelEstBuffer[i].reciprocal()));
            subChannels.get(i+pilotSubChannelIdx.get(0)).setEst(channelEstBuffer[i]);
        }
    }

    public Complex[] getEqualized() {
        Complex[] equalized = new Complex[dataSubChannelIdx.size()];
        for (int i = 0; i < equalized.length; i++) {
            SubChannel subChannel =  subChannels.get(dataSubChannelIdx.get(i));
            equalized[i] = subChannel.getValue().multiply(subChannel.getEst().reciprocal());
        }
        return equalized;
    }

    public double getSNRinDB() {

        double signalLocalEnergy = 0.0;
        double noiseLocalEnergy = 0.0;

        for (int i = 0; i < fftSize; i++) {

            if (dataSubChannelIdx.contains(i)) {
                signalLocalEnergy += Math.pow(subChannels.get(i).getValue().getImaginary(),2) +  Math.pow(subChannels.get(i).getValue().getReal(),2);
            } else {
                noiseLocalEnergy += Math.pow(subChannels.get(i).getValue().getImaginary(),2) +  Math.pow(subChannels.get(i).getValue().getReal(),2);
            }
        }

        signalLocalEnergy = signalLocalEnergy / dataSubChannelIdx.size();
        noiseLocalEnergy = noiseLocalEnergy / (fftSize-dataSubChannelIdx.size());
        return 20.0 * Math.log10(signalLocalEnergy/noiseLocalEnergy);
    }



}
