
package com.hrz.zxingcpp;

import java.util.Arrays;

/**
 * 条码读取参数。
 * @author HRZ
 */
public final class ReadOptions {

    private Symbology[] symbologies = {Symbology.ALL};
    private DecodeMode decodeMode = DecodeMode.FAST;
    private boolean tryRotate = true;
    private boolean tryInvert = true;
    private boolean tryDownscale = true;
    private boolean returnErrors = false;
    private int maxSymbols = 10;

    public ReadOptions() {
    }

    public Symbology[] getSymbologies() {
        return symbologies.clone();
    }

    public ReadOptions setSymbologies(Symbology... symbologies) {
        if (symbologies == null || symbologies.length == 0) {
            throw new IllegalArgumentException("symbologies must not be empty");
        }
        for (Symbology symbology : symbologies) {
            if (symbology == null) {
                throw new IllegalArgumentException("symbology must not be null");
            }
        }
        this.symbologies = symbologies.clone();
        return this;
    }

    public DecodeMode getDecodeMode() {
        return decodeMode;
    }

    public ReadOptions setDecodeMode(DecodeMode decodeMode) {
        if (decodeMode == null) {
            throw new IllegalArgumentException("decodeMode == null");
        }
        this.decodeMode = decodeMode;
        return this;
    }

    public boolean isTryRotate() {
        return tryRotate;
    }

    public ReadOptions setTryRotate(boolean tryRotate) {
        this.tryRotate = tryRotate;
        return this;
    }

    public boolean isTryInvert() {
        return tryInvert;
    }

    public ReadOptions setTryInvert(boolean tryInvert) {
        this.tryInvert = tryInvert;
        return this;
    }

    public boolean isTryDownscale() {
        return tryDownscale;
    }

    public ReadOptions setTryDownscale(boolean tryDownscale) {
        this.tryDownscale = tryDownscale;
        return this;
    }

    public boolean isReturnErrors() {
        return returnErrors;
    }

    public ReadOptions setReturnErrors(boolean returnErrors) {
        this.returnErrors = returnErrors;
        return this;
    }

    public int getMaxSymbols() {
        return maxSymbols;
    }

    public ReadOptions setMaxSymbols(int maxSymbols) {
        if (maxSymbols < 1 || maxSymbols > 255) {
            throw new IllegalArgumentException("maxSymbols must be between 1 and 255");
        }
        this.maxSymbols = maxSymbols;
        return this;
    }

    String[] nativeFormats() {
        return Arrays.stream(symbologies).map(Symbology::nativeName).toArray(String[]::new);
    }

    boolean nativeTryHarder() {
        return decodeMode == DecodeMode.TRY_HARDER;
    }
}
