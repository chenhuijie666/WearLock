package net.yishanhe.ofdm;

import org.apache.commons.math3.complex.Complex;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Created by syi on 2/12/16.
 */
public class Constellation {


    public static enum ModulationType {
        BASK, BPSK, QASK, QPSK, EightQAM, EightPSK, SixteenQAM
    }

    private ModulationType type;

//    <item>BASK</item>
//    <item>BPSK</item>
//    <item>QASK</item>
//    <item>QPSK</item>

    private int numberOfSymbols;
    private int constellationSize;
    private int bitsPerConstellationSymbol;
    public Map<String, Complex> bitStringToComplexMapping = null;

    public Constellation(ModulationType type) {
        this.type = type;

        this.bitStringToComplexMapping = new HashMap<>();


        switch (type) {

            // see @url{https://en.wikipedia.org/wiki/Phase-shift_keying}
            case BPSK:
                this.constellationSize = 2;
                bitStringToComplexMapping.put("1", new Complex(0.0, 1.0));
                bitStringToComplexMapping.put("0", new Complex(0.0, -1.0));
                break;

            case QPSK:
                // @TODO: add inital phase offset if possible
                // @TODO: more dynamic modulation than current the hardcoded schemes.
                this.constellationSize = 4;
                bitStringToComplexMapping.put("01", new Complex(-0.707, 0.707)); // 135
                bitStringToComplexMapping.put("11", new Complex(0.707, 0.707)); // 45
                bitStringToComplexMapping.put("10", new Complex(0.707, -0.707)); // 315
                bitStringToComplexMapping.put("00", new Complex(-0.707, -0.707)); // 225
                break;

            case BASK:
                this.constellationSize = 2;
                bitStringToComplexMapping.put("1", new Complex(1.0, 0.0));
                bitStringToComplexMapping.put("0", new Complex(0.0, 0.0));
                break;

            case QASK:
                this.constellationSize = 4;
                bitStringToComplexMapping.put("01", new Complex(-1.0, 0));
                bitStringToComplexMapping.put("11", new Complex(-0.33, 0));
                bitStringToComplexMapping.put("10", new Complex(0.33, 0));
                bitStringToComplexMapping.put("00", new Complex(1.0, 0));
                // see url{https://en.wikipedia.org/wiki/Amplitude-shift_keying}
                break;

            case EightPSK:
                this.constellationSize = 8;
                bitStringToComplexMapping.put("001", new Complex(-1.0, 0.0));
                bitStringToComplexMapping.put("011", new Complex(-0.707, 0.707));
                bitStringToComplexMapping.put("010", new Complex(0.0, 1.0));
                bitStringToComplexMapping.put("110", new Complex(0.707, 0.707));
                bitStringToComplexMapping.put("111", new Complex(1.0, 0.0));
                bitStringToComplexMapping.put("101", new Complex(0.707, -0.707));
                bitStringToComplexMapping.put("100", new Complex(0.0, -1.0));
                bitStringToComplexMapping.put("000", new Complex(-0.707, -0.707));
                break;

            case EightQAM:
                this.constellationSize = 8;
                bitStringToComplexMapping.put("100", new Complex(-1.0, 0.0));
                bitStringToComplexMapping.put("001", new Complex(1.0, 0.0));
                bitStringToComplexMapping.put("010", new Complex(0.0, -1.0));
                bitStringToComplexMapping.put("111", new Complex(0.0, 1.0));
                bitStringToComplexMapping.put("101", new Complex(0.366, 0.366));
                bitStringToComplexMapping.put("110", new Complex(-0.366, 0.366));
                bitStringToComplexMapping.put("000", new Complex(-0.366, -0.366));
                bitStringToComplexMapping.put("011", new Complex(0.366, -0.366));
                break;

            case SixteenQAM:
                this.constellationSize = 16;
                bitStringToComplexMapping.put("0010", new Complex(-1.0, 1.0));
                bitStringToComplexMapping.put("0110", new Complex(-0.333, 1.0));
                bitStringToComplexMapping.put("1110", new Complex(0.333, 1.0));
                bitStringToComplexMapping.put("1010", new Complex(1.0, 1.0));
                bitStringToComplexMapping.put("0011", new Complex(-1.0, 0.333));
                bitStringToComplexMapping.put("0111", new Complex(-0.333, 0.333));
                bitStringToComplexMapping.put("1111", new Complex(0.333, 0.333));
                bitStringToComplexMapping.put("1011", new Complex(1.0, 0.333));
                bitStringToComplexMapping.put("0001", new Complex(-1.0, -0.333));
                bitStringToComplexMapping.put("0101", new Complex(-0.333, -0.333));
                bitStringToComplexMapping.put("1101", new Complex(0.333, -0.333));
                bitStringToComplexMapping.put("1001", new Complex(1.0, -0.333));
                bitStringToComplexMapping.put("0000", new Complex(-1.0, -1.0));
                bitStringToComplexMapping.put("0100", new Complex(-0.333, -1.0));
                bitStringToComplexMapping.put("1100", new Complex(0.333, -1.0));
                bitStringToComplexMapping.put("1000", new Complex(1.0, -1.0));
                break;

        }

        this.bitsPerConstellationSymbol = (int) (Math.log((double)constellationSize)/Math.log(2));
    }

    public Complex[] constellationMapping(String input) {

        int numberOfInputBits = input.length();
        this.numberOfSymbols = numberOfInputBits/bitsPerConstellationSymbol;
        Complex[] complexMappingResult = new Complex[numberOfSymbols];
        String s;
        if (this.bitStringToComplexMapping != null){
            for (int i = 0; i<numberOfSymbols; i++) {
                s = input.substring(i*bitsPerConstellationSymbol, (i+1)*bitsPerConstellationSymbol);
                complexMappingResult[i] = this.bitStringToComplexMapping.get(s);
            }
        } else {
            throw new IllegalArgumentException("Constellation Map is NULL.");
        }

        return complexMappingResult;

    }

    // unmapping
    public String constellationDeMapping(Complex[] input) {
        // input is from parallel->serial
        String result = "";
        for (int i = 0; i < input.length; i++) {
            result += euclideanML(input[i]);
        }

        return result;
    }

    private String euclideanML(Complex input) {
        String result="";
        Iterator it = bitStringToComplexMapping.entrySet().iterator();
        double minDistance = Double.MAX_VALUE;
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry) it.next();
            double distance = ((Complex)pair.getValue()).subtract(input).abs();
            if (distance < minDistance) {
                minDistance = distance;
                result = (String) pair.getKey();
            }
        }
        return result;
    }

    public int getConstellationSize() {
        return constellationSize;
    }
}
