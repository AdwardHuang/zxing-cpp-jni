
package com.hrz.zxingcpp;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Objects;

/**
 * 条码读取参数。
 *
 * <p>不可变对象，支持链式配置。</p>
 *
 * @author HRZ
 */
public final class ReadOptions {

    private static final ReadOptions DEFAULTS = new ReadOptions(
            new Symbology[]{Symbology.ALL},
            DecodeMode.FAST,
            true,
            true,
            true,
            false,
            1
    );

    private final Symbology[] symbologies;
    private final DecodeMode decodeMode;
    private final boolean tryRotate;
    private final boolean tryInvert;
    private final boolean tryDownscale;
    private final boolean returnErrors;
    private final int maxSymbols;

    /**
     * 创建默认配置。
     *
     * 默认：
     * ALL、FAST、旋转检测、反色检测、缩小检测开启，
     * 不返回错误结果，最多识别 10 个条码。
     */
    public ReadOptions() {
        this(
                new Symbology[]{Symbology.ALL},
                DecodeMode.FAST,
                true,
                true,
                true,
                false,
                10
        );
    }

    private ReadOptions(Symbology[] symbologies, DecodeMode decodeMode,
                        boolean tryRotate, boolean tryInvert, boolean tryDownscale,
                        boolean returnErrors, int maxSymbols) {
        this.symbologies = symbologies.clone();
        this.decodeMode = Objects.requireNonNull(decodeMode, "decodeMode");
        this.tryRotate = tryRotate;
        this.tryInvert = tryInvert;
        this.tryDownscale = tryDownscale;
        this.returnErrors = returnErrors;
        this.maxSymbols = maxSymbols;
    }

    /**
     * 返回默认配置。
     */
    public static ReadOptions defaults() {
        return DEFAULTS;
    }

    /**
     * 返回优先速度的配置。
     */
    public static ReadOptions fast() {
        return DEFAULTS.setDecodeMode(DecodeMode.FAST);
    }

    /**
     * 返回标准检测策略的配置。
     */
    public static ReadOptions normal() {
        return DEFAULTS.setDecodeMode(DecodeMode.NORMAL);
    }

    /**
     * 返回优先识别率的配置。
     */
    public static ReadOptions tryHarder() {
        return DEFAULTS.setDecodeMode(DecodeMode.TRY_HARDER);
    }

    public Symbology[] getSymbologies() {
        return symbologies.clone();
    }

    /**
     * 整体替换条码格式。
     */
    public ReadOptions setSymbologies(Symbology... symbologies) {
        if (symbologies == null || symbologies.length == 0) {
            throw new IllegalArgumentException("symbologies must not be empty");
        }
        for (Symbology symbology : symbologies) {
            Objects.requireNonNull(symbology, "symbology");
        }
        return new ReadOptions(symbologies, decodeMode, tryRotate, tryInvert, tryDownscale, returnErrors, maxSymbols);
    }

    /**
     * 追加一种条码格式。
     */
    public ReadOptions addSymbology(Symbology symbology) {
        return addSymbologies(symbology);
    }

    /**
     * 批量追加条码格式。
     *
     * <p>自动去重，保留原有格式顺序。</p>
     * <p>ALL 表示所有格式，与其他格式组合时仍然只保留 ALL。</p>
     */
    public ReadOptions addSymbologies(Symbology... symbologies) {
        if (symbologies == null || symbologies.length == 0) {
            throw new IllegalArgumentException("symbologies must not be empty");
        }
        for (Symbology symbology : symbologies) {
            Objects.requireNonNull(symbology, "symbology");
        }

        if (Arrays.asList(this.symbologies).contains(Symbology.ALL)
                || Arrays.asList(symbologies).contains(Symbology.ALL)) {
            return setSymbologies(Symbology.ALL);
        }

        LinkedHashSet<Symbology> result = new LinkedHashSet<>(Arrays.asList(this.symbologies));
        result.addAll(Arrays.asList(symbologies));
        return setSymbologies(result.toArray(new Symbology[0]));
    }

    public DecodeMode getDecodeMode() {
        return decodeMode;
    }

    public ReadOptions setDecodeMode(DecodeMode decodeMode) {
        return new ReadOptions(symbologies, decodeMode, tryRotate, tryInvert, tryDownscale, returnErrors, maxSymbols);
    }

    public boolean isTryRotate() {
        return tryRotate;
    }

    public ReadOptions setTryRotate(boolean tryRotate) {
        return new ReadOptions(symbologies, decodeMode, tryRotate, tryInvert, tryDownscale, returnErrors, maxSymbols);
    }

    public boolean isTryInvert() {
        return tryInvert;
    }

    public ReadOptions setTryInvert(boolean tryInvert) {
        return new ReadOptions(symbologies, decodeMode, tryRotate, tryInvert, tryDownscale, returnErrors, maxSymbols);
    }

    public boolean isTryDownscale() {
        return tryDownscale;
    }

    public ReadOptions setTryDownscale(boolean tryDownscale) {
        return new ReadOptions(symbologies, decodeMode, tryRotate, tryInvert, tryDownscale, returnErrors, maxSymbols);
    }

    public boolean isReturnErrors() {
        return returnErrors;
    }

    public ReadOptions setReturnErrors(boolean returnErrors) {
        return new ReadOptions(symbologies, decodeMode, tryRotate, tryInvert, tryDownscale, returnErrors, maxSymbols);
    }

    public int getMaxSymbols() {
        return maxSymbols;
    }

    public ReadOptions setMaxSymbols(int maxSymbols) {
        if (maxSymbols < 1 || maxSymbols > 255) {
            throw new IllegalArgumentException("maxSymbols must be between 1 and 255");
        }
        return new ReadOptions(symbologies, decodeMode, tryRotate, tryInvert, tryDownscale, returnErrors, maxSymbols);
    }

    String[] nativeFormats() {
        return Arrays.stream(symbologies).map(Symbology::nativeName).toArray(String[]::new);
    }

    boolean nativeTryHarder() {
        return decodeMode == DecodeMode.TRY_HARDER;
    }

    @Override
    public String toString() {
        return "ReadOptions{" +
                "symbologies=" + Arrays.toString(symbologies) +
                ", decodeMode=" + decodeMode +
                ", tryRotate=" + tryRotate +
                ", tryInvert=" + tryInvert +
                ", tryDownscale=" + tryDownscale +
                ", returnErrors=" + returnErrors +
                ", maxSymbols=" + maxSymbols +
                '}';
    }
}
