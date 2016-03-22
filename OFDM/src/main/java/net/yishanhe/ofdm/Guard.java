package net.yishanhe.ofdm;

import net.yishanhe.utils.DSPUtils;

/**
 * Created by syi on 2/12/16.
 */
public class Guard {

    public enum GuardType {
        ZP, CP
    }

    private GuardType type;
    private int guardSize;


    public Guard(int guardSize) {
        this.type = GuardType.ZP;
        this.guardSize = guardSize;
    }

    public Guard(GuardType type, int guardSize) {
        this.type = type;
        this.guardSize = guardSize;
    }

    public void prependGuard(Chunk chunk) {
        if (type == GuardType.ZP) {
            double[] padded = DSPUtils.padZeros(chunk.getDoubleBuffer(), this.guardSize, DSPUtils.PadPos.HEAD);
            chunk.setDoubleBuffer(padded);
        }
    }

    public void removeGuard(Chunk chunk) {
        if (type == GuardType.ZP) {
            chunk.skip(guardSize);
        } 
    }
}
