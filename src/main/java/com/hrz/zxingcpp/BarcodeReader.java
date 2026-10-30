
package com.hrz.zxingcpp;

import java.awt.image.BufferedImage;
import java.util.Objects;

/**
 * 多格式条码读取器。
 */
public final class BarcodeReader {

    static {
        NativeLibraryLoader.loadNativeLibrary();
    }

    private BarcodeReader() {
    }

    /**
     * 读取 BufferedImage 中的所有条码。
     */
    public static BarcodeHit[] read(BufferedImage image) {
        return read(image, new ReadOptions());
    }

    /**
     * 使用指定参数读取 BufferedImage。
     */
    public static BarcodeHit[] read(BufferedImage image, ReadOptions options) {
        Objects.requireNonNull(image, "image == null");
        Objects.requireNonNull(options, "options == null");

        int width = image.getWidth();
        int height = image.getHeight();
        int[] argb = image.getRGB(0, 0, width, height, null, 0, width);
        byte[] rgba = new byte[Math.multiplyExact(argb.length, 4)];

        int p = 0;
        for (int pixel : argb) {
            rgba[p++] = (byte) ((pixel >>> 16) & 0xff);
            rgba[p++] = (byte) ((pixel >>> 8) & 0xff);
            rgba[p++] = (byte) (pixel & 0xff);
            rgba[p++] = (byte) ((pixel >>> 24) & 0xff);
        }

        return readRgba(rgba, width, height, width * 4, options);
    }

    /**
     * 读取 RGBA 图像。
     */
    public static BarcodeHit[] readRgba(byte[] rgba, int width, int height) {
        return readRgba(rgba, width, height, Math.multiplyExact(width, 4), new ReadOptions());
    }

    /**
     * 读取指定行跨度的 RGBA 图像。
     *
     * @param rgba      RGBA 原始数据
     * @param width     图像宽度
     * @param height    图像高度
     * @param rowStride 每行字节数，通常为 width * 4
     * @param options   识别参数
     */
    public static BarcodeHit[] readRgba(byte[] rgba, int width, int height, int rowStride, ReadOptions options) {
        Objects.requireNonNull(rgba, "rgba == null");
        Objects.requireNonNull(options, "options == null");

        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("width/height must be > 0");
        }
        if (rowStride < width * 4) {
            throw new IllegalArgumentException("rowStride must be >= width * 4");
        }
        if ((long) rgba.length < (long) rowStride * height) {
            throw new IllegalArgumentException("rgba data is too small");
        }

        return nativeRead(
                rgba,
                width,
                height,
                rowStride,
                options.nativeFormats(),
                options.nativeTryHarder(),
                options.isTryRotate(),
                options.isTryInvert(),
                options.isTryDownscale(),
                options.getMaxSymbols(),
                options.isReturnErrors()
        );
    }

    /**
     * 返回第一个识别结果，没有结果时返回 null。
     */
    public static BarcodeHit readFirst(BufferedImage image) {
        BarcodeHit[] hits = read(image);
        return hits.length == 0 ? null : hits[0];
    }

    /**
     * 返回 ZXing-C++ 版本。
     */
    public static String version() {
        return ZXingCpp.version();
    }

    private static native BarcodeHit[] nativeRead(
            byte[] rgba,
            int width,
            int height,
            int rowStride,
            String[] formats,
            boolean tryHarder,
            boolean tryRotate,
            boolean tryInvert,
            boolean tryDownscale,
            int maxSymbols,
            boolean returnErrors
    );
}
