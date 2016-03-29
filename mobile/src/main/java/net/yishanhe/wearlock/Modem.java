package net.yishanhe.wearlock;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import net.yishanhe.ofdm.AdaptiveModulation;
import net.yishanhe.ofdm.Channel;
import net.yishanhe.ofdm.Chunk;
import net.yishanhe.ofdm.Constellation;
import net.yishanhe.ofdm.Constellation.ModulationType;
import net.yishanhe.ofdm.Frame;
import net.yishanhe.ofdm.Guard;
import net.yishanhe.ofdm.Guard.GuardType;
import net.yishanhe.ofdm.Preamble;
import net.yishanhe.ofdm.Synchronization;
import net.yishanhe.utils.DSPUtils;
import net.yishanhe.wearlock.events.MessageEvent;

import org.apache.commons.math3.complex.Complex;
import org.greenrobot.eventbus.EventBus;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Created by syi on 2/15/16.
 */
public class Modem {

    private static final String TAG = "Modem";

    private static final String START_RECORDING = "/start_recording";
    private static final String STOP_RECORDING = "/stop_recording";

    private Context context;
    private SharedPreferences prefs;

    public Preamble preamble;
    private Guard guard;
//    private Chunk sent;
//    private Chunk received;
    private Frame frame;
    private Channel channel;
    private Constellation constellation;
    private AdaptiveModulation adaptiveModulation;

    private int sampleRateInHZ;
    private boolean isAdaptiveModulationON;
    private boolean isChannelEstimationON;
    private boolean isChannelTestModeON;
    private boolean isExperimentModeON;
    private boolean isDebugOutputON;
    private boolean isChannelProbingON;

    private int guardSize;
    private int fftSize;
    private ArrayList<Integer> pilotSubChannelIdx;
    private ArrayList<Integer> pilot1stHarmonicSubChannelIdx;
    private ArrayList<Integer> dataSubChannelIdx;
    private ArrayList<Integer> data1stHarmonicSubChannelIdx;
    private ArrayList<Integer> nullSubChannelIdx;
    private GuardType guardType;
    private ModulationType modulationType;
    private int preambleSize;
    private double preambleStartFreq;
    private double preambleFreqRange;
    private int postPreambleGuardSize;
    private int preambleWithProbingSize;
    int numberOfFrames;

//    private AudioWriter audioWriter = null;

    public static Modem instance;

    private short[] preambleInShort;
    private short[] modulatedInShort;
    private double[] modulated;
    private String target;

//    private Thread writerThread;

    // @TODO: put it into preference
    public final static int VOL_MAXIMUM = Short.MAX_VALUE - 767; // loudness.
    private int volume;

    /**
     * Will handling the OFDM primitives and Android Audio IO
     */
    private Modem(Context context) {
        this.context = context;
        this.prefs = PreferenceManager.getDefaultSharedPreferences(context);

    }

    public static synchronized Modem getInstance(Context context) {
        if (instance == null) {
            instance = new Modem(context);
        }

        return instance;
    }

    public void LoadParameter() {
        this.sampleRateInHZ = Integer.parseInt(prefs.getString("sampling_rate","44100"));
        this.isAdaptiveModulationON = prefs.getBoolean("adaptive_mod",false);
        this.isChannelEstimationON = prefs.getBoolean("channel_est",false);
        this.isChannelTestModeON = prefs.getBoolean("channel_test_mode",false);
        this.isDebugOutputON = prefs.getBoolean("debug_output",true);
        this.isExperimentModeON  = prefs.getBoolean("experiment_mode", false);
        this.isChannelProbingON = prefs.getBoolean("channel_probing",false);
        this.guardSize = Integer.parseInt(prefs.getString("guard_size","128"));
        String[] pilotIndex = prefs.getString("pilot_index","7,11,15,19,23,27,31,35").split(",");
        this.pilotSubChannelIdx = new ArrayList<>(pilotIndex.length);
        this.pilot1stHarmonicSubChannelIdx = new ArrayList<>(pilotIndex.length);
        for (int i = 0; i < pilotIndex.length; i++) {
            pilotSubChannelIdx.add(Integer.valueOf(pilotIndex[i]));
            pilot1stHarmonicSubChannelIdx.add(2*Integer.valueOf(pilotIndex[i]));
        }
        String[] dataIndex = prefs.getString("data_index","16,17,18,20,21,22,24,25,26,28,29,30").split(",");
        this.dataSubChannelIdx = new ArrayList<>(dataIndex.length);
        this.data1stHarmonicSubChannelIdx = new ArrayList<>(dataIndex.length);
        for (int i = 0; i < dataIndex.length; i++) {
            dataSubChannelIdx.add(Integer.valueOf(dataIndex[i]));
            data1stHarmonicSubChannelIdx.add(2*Integer.valueOf(dataIndex[i]));
        }
        String[] nullIndex = prefs.getString("null_index","8,9,10,12,13,14,32,33,34,36,37,38").split(",");
        this.nullSubChannelIdx = new ArrayList<>(nullIndex.length);
        for (int i = 0; i < nullIndex.length; i++) {
            nullSubChannelIdx.add(Integer.valueOf(nullIndex[i]));
        }
        int guardTypeIndex = Integer.parseInt(prefs.getString("guard_type","0"));
        this.guardType = GuardType.values()[guardTypeIndex];
        preambleSize = Integer.parseInt(prefs.getString("preamble_size","256"));
        preambleStartFreq = Double.valueOf(prefs.getString("preamble_start_freq","1000.0"));
        preambleFreqRange = Double.valueOf(prefs.getString("preamble_freq_range","1000.0"));
        postPreambleGuardSize = Integer.parseInt(prefs.getString("post_preamble_guard_size","2048"));
        int modulationTypeIndex = Integer.parseInt(prefs.getString("modulation_type","0"));
        this.modulationType = ModulationType.values()[modulationTypeIndex];
        this.fftSize = Integer.parseInt(prefs.getString("fft_size","256"));
        this.volume = (int)(Double.valueOf(prefs.getString("volume","1.0"))*VOL_MAXIMUM);
        Log.d(TAG, "LoadParameter: parameters loaded.");
    }

    // add listener to reload parameter.
    public void prepare () {
        this.guard = new Guard(guardType, guardSize);
        this.constellation = new Constellation(modulationType);
        this.channel = new Channel(fftSize, sampleRateInHZ, pilotSubChannelIdx, dataSubChannelIdx);
        this.frame = new Frame(fftSize, pilotSubChannelIdx, dataSubChannelIdx);
    }

    public void makePreamble() {
        this.preamble = new Preamble(preambleSize, postPreambleGuardSize, preambleStartFreq, preambleFreqRange, sampleRateInHZ);
        this.adaptiveModulation = new AdaptiveModulation(preamble);

        if (isChannelProbingON) {
            //
            preambleWithProbingSize = preambleSize + postPreambleGuardSize + fftSize;
            double[] preambleWithProbing = new double[preambleWithProbingSize];
            Complex[] freqProbingFrame = new Complex[fftSize];
            for (int i = 0; i < fftSize; i++) {
                if (pilotSubChannelIdx.contains(i)) {
                    freqProbingFrame[i] = new Complex(1.0, 0.0);
                } else {
                    freqProbingFrame[i] = Complex.ZERO;
                }
            }

            double[] freqProbingFrameIfftBuffer = DSPUtils.getReals(DSPUtils.ifft(freqProbingFrame, fftSize));
            System.arraycopy(scaleDoubles(preamble.getPreamble()), 0, preambleWithProbing, 0, preambleSize);
            System.arraycopy(scaleDoubles(freqProbingFrameIfftBuffer), 0, preambleWithProbing, preambleSize + postPreambleGuardSize, freqProbingFrameIfftBuffer.length);
            preambleInShort = doubleToAudioShort(preambleWithProbing);

            Chunk toFile = new Chunk(preambleWithProbing);
            try {
                toFile.dump("/sdcard/WearLock/dumped_preamble_probing.raw");
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            Chunk toFile = new Chunk(scaleDoubles(preamble.getPreamble()));
            try {
                toFile.dump("/sdcard/WearLock/dumped_preamble_no_probing.raw");
            } catch (IOException e) {
                e.printStackTrace();
            }
            preambleInShort = doubleToAudioShort(scaleDoubles(preamble.getPreamble()));
        }


    }

    public  void makeModulated(String inputPin) {

        if (isChannelTestModeON) {
            if (modulationType == ModulationType.QPSK || modulationType == ModulationType.QASK) {
                inputPin = "111111010101000000101010";
            }
            if (modulationType == ModulationType.BASK || modulationType == ModulationType.BPSK) {
                inputPin = "111000101010";
            }
            if (modulationType == ModulationType.EightPSK || modulationType == ModulationType.EightQAM) {
                inputPin = "100001010111101110000011100001010111";
            }
            if (modulationType == ModulationType.SixteenQAM) {
                inputPin = "001001111101100010101111010100000001011010111100";
            }
            EventBus.getDefault().post(new MessageEvent(TAG, inputPin, "/fixed_input"));
        }

        // @TODO: use the api in chunk class.

        target = inputPin;

        Log.d(TAG, "makeModulated: target "+target);
        Log.d(TAG, "makeModulated: modulation type "+this.modulationType);
        Complex[] serial = constellation.constellationMapping(target);
        ArrayList<Complex[]> parallel = frame.s2p(serial);
        Log.d(TAG, "makeModulated: parallel complex size "+ parallel.size());
        numberOfFrames = parallel.size();

        int outputLen;
        outputLen = preambleSize + postPreambleGuardSize + numberOfFrames * (guardSize + fftSize);

        modulated = new double[outputLen];
        Arrays.fill(modulated, 0.0);
        // copy in the preamble
        System.arraycopy(scaleDoubles(preamble.getPreamble()), 0, modulated, 0, preambleSize);

//        if (isFreqSyncON) {
//            Complex[] freqSyncFrame = new Complex[fftSize];
//            for (int i = 0; i < fftSize; i++) {
//                if (pilotSubChannelIdx.contains(i)) {
//                    freqSyncFrame[i] = new Complex(1.0, 0.0);
//                } else {
//                    freqSyncFrame[i] = Complex.ZERO;
//                }
//            }
//            double[] freqSyncFrameIfftBuffer = DSPUtils.getReals(DSPUtils.ifft(freqSyncFrame, fftSize));
//            System.arraycopy(scaleDoubles(freqSyncFrameIfftBuffer), 0, modulated, preambleSize + postPreambleGuardSize, freqSyncFrameIfftBuffer.length);
//
//        }

        for (int i = 0; i < parallel.size(); i++) {
            Complex[] singleFrame = parallel.get(i);

            if (singleFrame.length != fftSize) {
                throw new IllegalArgumentException("input size should be the fft size.");
            }

            double[] ifftBuffer = DSPUtils.getReals(DSPUtils.ifft(singleFrame, fftSize));

            // ZP
            if (guardType == GuardType.CP) {
                System.arraycopy(scaleDoubles(ifftBuffer), 0, modulated,
                        preambleSize + postPreambleGuardSize + i * (guardSize + fftSize) + guardSize,
                        ifftBuffer.length);
                System.arraycopy(scaleDoubles(ifftBuffer), ifftBuffer.length-guardSize, modulated,
                        preambleSize + postPreambleGuardSize + i * (guardSize + fftSize),
                        guardSize);
            } else {
                System.arraycopy(scaleDoubles(ifftBuffer), 0, modulated,
                        preambleSize + postPreambleGuardSize + i * (guardSize + fftSize) + guardSize,
                        ifftBuffer.length);
            }
        }

//        for (int i = 0; i < modulated.length; i++) {
//           System.out.println("modulated i:"+i+" double: "+modulated[i]);
//        }
        Chunk toDump = new Chunk(modulated);
        try {
            toDump.dump("/sdcard/WearLock/dumped_modulated.raw");
        } catch (IOException e) {
            e.printStackTrace();
        }

        modulatedInShort = doubleToAudioShort(modulated);
//        EventBus.getDefault().post(new MessageEvent(TAG, "Modulated audio track is made.","/UPDATE_STATUS"));

    }

    public void channelProbing(Chunk whole, int start, int end , double minSPL) {


        //(minSPL+97) // noise
        double vol=1.0;
        double rxSPL = minSPL+97+10;

//        if (rxSPL > 50) {
//            vol = 1.0;
//        } else if (rxSPL > 48) {
//            vol = 0.75;
//        } else if (rxSPL > 44) {
//            vol = 0.5;
//        } else if (rxSPL > 39) {
//            vol = 0.25;
//        } else {
//            vol = 0.1;
//        }
//        EventBus.getDefault().post(new MessageEvent(TAG, "Demodulation Pilot SNR estimated: "+String.format("%.4f",vol),"/UPDATE_STATUS"));
//        this.volume = (int)(vol*VOL_MAXIMUM);
        // 100 50
        // 75 48
        // 50 44
        // 25 39



        if (isChannelProbingON) {

            int inputLen = whole.getDoubleBuffer().length;

            // get a smaller chunk
            Chunk target = whole.getSubChunk(start, Math.min(end+preambleWithProbingSize, inputLen));

            int delay = Synchronization.preambleTimeSync(preamble.getPreamble(), target.getDoubleBuffer());

            Log.d(TAG, "channelProbing: delay " + Math.abs(delay));

            // try synchronization here
            int freqDelay = Synchronization.preambleFreqSync(target.getDoubleBuffer(), (Math.abs(delay)+preambleSize+postPreambleGuardSize), fftSize, 2, pilotSubChannelIdx);
            Log.d(TAG, "channelProbing: freq delay " + freqDelay);
            // do channel probing here
            target.skip(Math.abs(delay)+freqDelay);
            target.skip(preambleSize+postPreambleGuardSize);
            double[] channelProbingFrame = target.getDoubleBuffer(0, fftSize);
            Complex[] fftBuffer = DSPUtils.fft(channelProbingFrame, fftSize);
            System.out.println("the fft output size: "+fftBuffer.length);
            if (isDebugOutputON) {

                double pilotLocalEnergy = 0.0;
                double noiseLocalEnergy = 0.0;
                for (int j = 1; j < fftSize/2; j++) {
                    if (pilotSubChannelIdx.contains(j)) {
                        pilotLocalEnergy += Math.pow(fftBuffer[j].getImaginary(),2) +  Math.pow(fftBuffer[j].getReal(),2);
                    } else if (nullSubChannelIdx.contains(j)) {
                        noiseLocalEnergy += Math.pow(fftBuffer[j].getImaginary(),2) +  Math.pow(fftBuffer[j].getReal(),2);
                    }
                }
                pilotLocalEnergy = pilotLocalEnergy / pilotSubChannelIdx.size();
                noiseLocalEnergy = noiseLocalEnergy / nullSubChannelIdx.size();
                double preamblePSNR = 10*Math.log10((pilotLocalEnergy-noiseLocalEnergy)/noiseLocalEnergy);
                EventBus.getDefault().post(new MessageEvent(TAG, "Preamble Pilot-SNR estimated: "+String.format("%.4f",preamblePSNR),"/UPDATE_STATUS"));
                EventBus.getDefault().post(new MessageEvent(
                        TAG,
                        "Eb/N0 estimated: " + String.format("%.4f",preamblePSNR+10*Math.log10(4.0/(Math.log(constellation.getConstellationSize())/Math.log(2)))),
                        "/UPDATE_STATUS"));

                channel.setChannelBuffer(fftBuffer);
                channel.estimate();

                channel.equalize();

                for (int j = 0; j < fftBuffer.length/2; j++) {
                    if (channel.getDataSubChannelIdx().contains(j)){
                        double phaseAngle = Math.atan2(fftBuffer[j].getImaginary(), fftBuffer[j].getReal());
                        System.out.println(j+" DATA complex: "+fftBuffer[j].toString()+" amp:"+String.format("%.4f",fftBuffer[j].abs())+" phase angle:"
                                +String.format("%.2f",phaseAngle*180.0/Math.PI)+" equalized: "+channel.getSubChannelAtIdx(j).getEqualized().toString());
                    }
                    if (channel.getPilotSubChannelIdx().contains(j)){
                        double phaseAngle = Math.atan2(fftBuffer[j].getImaginary(), fftBuffer[j].getReal());
                        System.out.println(j+" PILOT complex: "+fftBuffer[j].toString()+" amp:"+String.format("%.4f",fftBuffer[j].abs())+" phase angle:"
                                +String.format("%.2f",phaseAngle*180.0/Math.PI)+" equalized: "+channel.getSubChannelAtIdx(j).getEqualized().toString());
                    }
                    if (nullSubChannelIdx.contains(j)){
                        double phaseAngle = Math.atan2(fftBuffer[j].getImaginary(), fftBuffer[j].getReal());
                        System.out.println(j+" NULL complex: "+fftBuffer[j].toString()+" amp:"+String.format("%.4f",fftBuffer[j].abs())+" phase angle:"
                                +String.format("%.2f",phaseAngle*180.0/Math.PI)+" equalized: "+channel.getSubChannelAtIdx(j).getEqualized().toString());
                    }

                }

            }
        }
    }

    public String deModulate(Chunk whole, int start, int end ) {

        String result = "";
        int inputLen = whole.getDoubleBuffer().length;


//        if ( (start+modulated.length) > inputLen) {
//            EventBus.getDefault().post(new MessageEvent(TAG, "No signal to demodulated"));
//            return "no signal to demodulate.";
//        }
//
//        if ((end-start)< modulated.length) {
//            end = Math.min(start+modulated.length, inputLen);
//        }

        Chunk target = whole.getSubChunk(start, Math.min(end+modulated.length, whole.getDoubleBuffer().length));

        int delay = Synchronization.preambleTimeSync(preamble.getPreamble(), target.getDoubleBuffer());
//        int freqDelay = Synchronization.preambleFreqSync(target.getDoubleBuffer(), (Math.abs(delay)+preambleSize+postPreambleGuardSize), fftSize, 2, pilotSubChannelIdx);

//        EventBus.getDefault().post(new MessageEvent(TAG, "synchronization needs to skip " + Math.abs(delay) + " samples", "/UPDATE_STATUS"));

        if (guardType == GuardType.ZP) {

            if ( (Math.abs(delay)+start) > inputLen) {
                return "bad signal. aborted.";
            }
            target.skip(Math.abs(delay));
            // cut preamble and post preamble
            if ( (preambleSize+postPreambleGuardSize)>target.getDoubleBuffer().length ) {
                return "bad signal. aborted.";
            }
            target.skip(preambleSize+postPreambleGuardSize);

        } else {

            int cpDelay = Synchronization.cpTimeSync(target.getDoubleBuffer(), Math.abs(delay)+preambleSize+postPreambleGuardSize,fftSize+guardSize, guardSize, 20);
            System.out.println("CP time sync delay: "+cpDelay);
            target.skip(Math.abs(delay)+preambleSize+postPreambleGuardSize+cpDelay);
        }

        // synchronized:
        try {
            target.dump("/sdcard/WearLock/chunk_sychronized_dumped.raw");
        } catch (IOException e) {
            e.printStackTrace();
        }

        for (int i = 0; i < numberOfFrames; i++) {

            target.skip(guardSize);

            // read one symbol
            double[] ofdmOneFrame = target.getDoubleBuffer(0, fftSize);
            // skip this symbol
            target.skip(fftSize);

            // can dump to file here.
            Complex[] fftBuffer = DSPUtils.fft(ofdmOneFrame, fftSize);


//            double pilotLocalEnergy = 0.0;
//            double noiseLocalEnergy = 0.0;
//            for (int j = 1; j < fftSize/2; j++) {
//                if (pilotSubChannelIdx.contains(j)) {
//                    pilotLocalEnergy += Math.pow(fftBuffer[j].getImaginary(),2) +  Math.pow(fftBuffer[j].getReal(),2);
//                } else if (nullSubChannelIdx.contains(j)) {
//                    noiseLocalEnergy += Math.pow(fftBuffer[j].getImaginary(),2) +  Math.pow(fftBuffer[j].getReal(),2);
//                }
//            }
//            pilotLocalEnergy = pilotLocalEnergy / pilotSubChannelIdx.size();
//            noiseLocalEnergy = noiseLocalEnergy / nullSubChannelIdx.size();
//            double demodPilotSNR = 10*Math.log10((pilotLocalEnergy-noiseLocalEnergy)/noiseLocalEnergy);
//            EventBus.getDefault().post(new MessageEvent(TAG, "Demodulation Pilot SNR estimated: "+String.format("%.4f",demodPilotSNR),"/UPDATE_STATUS"));
//            EventBus.getDefault().post(new MessageEvent(
//                    TAG,
//                    "Eb/N0 estimated: " + String.format("%.4f",demodPilotSNR+10*Math.log10(4.0/(Math.log(constellation.getConstellationSize())/Math.log(2)))),
//                    "/UPDATE_STATUS"));



            channel.setChannelBuffer(fftBuffer);
            channel.estimate();

            channel.equalize();
            Complex[] equalized = channel.getEqualizedData();

            if (isDebugOutputON) {
                for (int j = 0; j < fftBuffer.length/2; j++) {
                    if (channel.getDataSubChannelIdx().contains(j)){
                        double phaseAngle = Math.atan2(fftBuffer[j].getImaginary(), fftBuffer[j].getReal());
                        System.out.println(j+" DATA complex: "+fftBuffer[j].toString()+" amp:"+String.format("%.4f",fftBuffer[j].abs())+" phase angle:"
                                +String.format("%.2f",phaseAngle*180.0/Math.PI)+" equalized: "+channel.getSubChannelAtIdx(j).getEqualized().toString());
                    }
                    if (channel.getPilotSubChannelIdx().contains(j)){
                        double phaseAngle = Math.atan2(fftBuffer[j].getImaginary(), fftBuffer[j].getReal());
                        System.out.println(j+" PILOT complex: "+fftBuffer[j].toString()+" amp:"+String.format("%.4f",fftBuffer[j].abs())+" phase angle:"
                                +String.format("%.2f",phaseAngle*180.0/Math.PI)+" equalized: "+channel.getSubChannelAtIdx(j).getEqualized().toString());
                    }
                    if (nullSubChannelIdx.contains(j)){
                        double phaseAngle = Math.atan2(fftBuffer[j].getImaginary(), fftBuffer[j].getReal());
                        System.out.println(j+" NULL complex: "+fftBuffer[j].toString()+" amp:"+String.format("%.4f",fftBuffer[j].abs())+" phase angle:"
                                +String.format("%.2f",phaseAngle*180.0/Math.PI)+" equalized: "+channel.getSubChannelAtIdx(j).getEqualized().toString());
                    }
                }
            }


            result += constellation.constellationDeMapping(equalized);

        }

        return result;

    }


    public double detectPreamble(double[] input) {
        return preamble.detectPreamble(input);
    }


    public static double[] scaleDoubles(double[] input) {
        double[] scaled = new double[input.length];
        double maxVal = Double.MIN_VALUE;

        for (int i = 0; i < input.length; i++) {
            if (maxVal < input[i]) {
                maxVal = input[i];
            }
        }

        for (int i = 0; i < scaled.length; i++) {
            scaled[i] = input[i]/maxVal;
        }
        return scaled;
    }

    public short[] doubleToAudioShort(double[] doubleBuffer) {
        short[] shortBuffer = new short[doubleBuffer.length];
        Arrays.fill(shortBuffer, (short)0);
        for (int i = 0; i < shortBuffer.length; i++) {
            shortBuffer[i] = (short) (doubleBuffer[i]* volume);
        }
        return shortBuffer;
    }

    public int getSampleRateInHZ() {
        return sampleRateInHZ;
    }

    public short[] getPreambleInShort() {
        return preambleInShort;
    }

    public short[] getModulatedInShort() {
        return modulatedInShort;
    }

    public double[] getPreamble() {
        return preamble.getPreamble();
    }

    public boolean isExperimentModeON() {
        return isExperimentModeON;
    }

    public int getConstellationSize() {
        return constellation.getConstellationSize();
    }
}
