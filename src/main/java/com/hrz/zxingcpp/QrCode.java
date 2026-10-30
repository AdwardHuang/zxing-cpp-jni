package com.hrz.zxingcpp;

import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * ZXing-C++ 二维码对象。
 *
 * 一个 QrCode 对象在 native 层只编码一次。
 * 后续查询 module、版本、纠错等级以及生成图片，
 * 都直接使用已经创建的 ZXing::Barcode。
 */
public final class QrCode implements AutoCloseable {

    private long nativeHandle;
    
    static {
        NativeLibraryLoader.loadNativeLibrary();
    }

    private QrCode(long nativeHandle) {
        if (nativeHandle == 0) {
            throw new IllegalArgumentException("nativeHandle == 0");
        }

        this.nativeHandle = nativeHandle;
    }

    // ========================================================
    // Factory
    // ========================================================

    /**
     * 默认编码：
     * EC = M
     * ECI = ISO-8859-1 (3)
     */
    public static QrCode encode(byte[] data) {
        return encode(data, ZXingCpp.DEFAULT_EC_LEVEL, ZXingCpp.DEFAULT_ECI);
    }

    /**
     * 使用指定纠错等级和 ECI 编码。
     *
     * @param data    原始数据
     * @param ecLevel 1=L, 2=M, 3=Q, 4=H
     * @param eci     ECI，例如 3、26、899
     */
    public static QrCode encode(byte[] data, int ecLevel, int eci) {
        Objects.requireNonNull(data, "data");

        if (data.length == 0) {
            throw new IllegalArgumentException("data is empty");
        }

        if (ecLevel < 1 || ecLevel > 4) {
            throw new IllegalArgumentException("ecLevel must be 1(L), 2(M), 3(Q), or 4(H)");
        }

        if (eci < 0 || eci > 999999) {
            throw new IllegalArgumentException("eci must be between 0 and 999999");
        }

        long handle = nativeCreateQrCode(data, ecLevel, eci);

        if (handle == 0) {
            throw new IllegalStateException("Failed to create QR Code");
        }

        return new QrCode(handle);
    }

    /**
     * UTF-8 + ECI 26。
     */
    public static QrCode encodeUtf8(String text) {
        Objects.requireNonNull(text, "text");

        return encode(text.getBytes(StandardCharsets.UTF_8), ZXingCpp.DEFAULT_EC_LEVEL, ZXingCpp.ECI_UTF8);
    }

    /**
     * ISO-8859-1 + ECI 3。
     * byte[] 中的每个字节保持原始值。
     */
    public static QrCode encodeBytesCompatible(byte[] data) {
        return encode(data, ZXingCpp.DEFAULT_EC_LEVEL, ZXingCpp.ECI_ISO_8859_1);
    }

    /**
     * Binary + ECI 899。
     */
    public static QrCode encodeBinary(byte[] data) {
        return encode(data, ZXingCpp.DEFAULT_EC_LEVEL, ZXingCpp.ECI_BINARY);
    }

    // ========================================================
    // QR metadata
    // ========================================================

    /**
     * 获取二维码每边的 module 数量。
     * 不包含 quiet zone。
     */
    public synchronized int size() {
        return nativeQrCodeSize(requireHandle());
    }

    /**
     * 获取 QR Code 版本，范围通常为 1~40。
     */
    public synchronized int version() {
        return nativeQrCodeVersion(requireHandle());
    }

    /**
     * 获取纠错等级：L、M、Q、H。
     */
    public synchronized String errorCorrection() {
        return nativeQrCodeErrorCorrection(requireHandle());
    }

    /**
     * 获取二维码 Mask 编号，通常为 0~7。
     */
    public synchronized int mask() {
        return nativeQrCodeMask(requireHandle());
    }

    /**
     * 判断指定 module 是否为黑色。
     *
     * 坐标从左上角开始，范围为：
     * 0 <= x < size()
     * 0 <= y < size()
     */
    public synchronized boolean isDark(int x, int y) {
        long handle = requireHandle();
        int moduleCount = nativeQrCodeSize(handle);

        if (x < 0 || x >= moduleCount || y < 0 || y >= moduleCount) {
            throw new IndexOutOfBoundsException("x=" + x + ", y=" + y + ", size=" + moduleCount);
        }

        return nativeQrCodeIsDark(handle, x, y);
    }

    // ========================================================
    // Image rendering
    // ========================================================

    /**
     * 使用默认 quiet zone 渲染。
     *
     * @param moduleSize 每个 QR module 对应的像素数
     */
    public synchronized BufferedImage toImage(int moduleSize) {
        return toImage(moduleSize, ZXingCpp.DEFAULT_QUIET_ZONE);
    }

    /**
     * 将二维码渲染为灰度图片。
     *
     * 最终图片边长：
     *
     * (size() + quietZone * 2) * moduleSize
     *
     * 每个 module 严格占用 moduleSize × moduleSize 像素。
     */
    public synchronized BufferedImage toImage(int moduleSize, int quietZone) {

        long handle = requireHandle();

        if (moduleSize <= 0) {
            throw new IllegalArgumentException("moduleSize must be > 0");
        }

        if (quietZone < 0) {
            throw new IllegalArgumentException("quietZone must be >= 0");
        }

        int moduleCount = nativeQrCodeSize(handle);

        long sideLong = (long) (moduleCount + quietZone * 2L) * moduleSize;

        if (sideLong <= 0 || sideLong > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Rendered image size is too large");
        }

        int side = (int) sideLong;

        long pixelCount = (long) side * side;

        if (pixelCount > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Rendered image is too large");
        }

        byte[] gray = nativeQrCodeRender(handle, moduleSize, quietZone);

        if (gray == null || gray.length != (int) pixelCount) {
            throw new IllegalStateException("Invalid native QR image data");
        }

        BufferedImage image = new BufferedImage(side, side, BufferedImage.TYPE_BYTE_GRAY);

        WritableRaster raster = image.getRaster();

        raster.setDataElements(0, 0, side, side, gray);

        return image;
    }

    // ========================================================
    // Module size calculation
    // ========================================================

    /**
     * 根据期望图片边长计算 moduleSize。
     *
     * 采用向下取整，保证最终二维码边长不超过 expectedSideLength。
     *
     * @param expectedSideLength 期望图片边长，像素
     * @param moduleCount        二维码 module 数量
     * @param quietZone          quiet zone，单位 module
     * @return 每个 module 对应的像素数
     */
    public static int calculateModuleSize(int expectedSideLength, int moduleCount, int quietZone) {

        if (expectedSideLength <= 0) {
            throw new IllegalArgumentException("expectedSideLength must be > 0");
        }

        if (moduleCount <= 0) {
            throw new IllegalArgumentException("moduleCount must be > 0");
        }

        if (quietZone < 0) {
            throw new IllegalArgumentException("quietZone must be >= 0");
        }

        long totalModules = (long) moduleCount + quietZone * 2L;

        int moduleSize = (int) (expectedSideLength / totalModules);

        if (moduleSize <= 0) {
            throw new IllegalArgumentException("Expected image size is too small for this QR Code");
        }

        return moduleSize;
    }

    /**
     * 使用当前二维码自身的 module 数量计算 moduleSize。
     */
    public synchronized int calculateModuleSize(int expectedSideLength, int quietZone) {

        return calculateModuleSize(expectedSideLength, nativeQrCodeSize(requireHandle()), quietZone);
    }

    // ========================================================
    // Lifecycle
    // ========================================================

    private long requireHandle() {
        if (nativeHandle == 0) {
            throw new IllegalStateException("QrCode has already been closed");
        }

        return nativeHandle;
    }

    @Override
    public synchronized void close() {
        long handle = nativeHandle;

        if (handle != 0) {
            nativeHandle = 0;
            nativeDestroyQrCode(handle);
        }
    }

    // ========================================================
    // Native methods
    // ========================================================

    private static native long nativeCreateQrCode(byte[] data, int ecLevel, int eci);

    private static native void nativeDestroyQrCode(long handle);

    private static native int nativeQrCodeSize(long handle);

    private static native int nativeQrCodeVersion(long handle);

    private static native String nativeQrCodeErrorCorrection(long handle);

    private static native int nativeQrCodeMask(long handle);

    private static native boolean nativeQrCodeIsDark(long handle, int x, int y);

    private static native byte[] nativeQrCodeRender(long handle, int moduleSize, int quietZone);
}