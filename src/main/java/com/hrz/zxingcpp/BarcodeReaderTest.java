
package com.hrz.zxingcpp;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * BarcodeReader 功能测试。
 *
 * 不依赖 JUnit，可直接运行 main()。
 *
 * 运行前需要：
 * 1. 编译 Java 代码。
 * 2. 完成 BarcodeReader_nativeRead JNI 实现。
 * 3. 确保本机 native 库能够正常加载。
 */
public final class BarcodeReaderTest {

    private static final int IMAGE_SIZE = 480;

    private BarcodeReaderTest() {
    }

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("BarcodeReader Test");
        System.out.println("========================================");

        testVersion();
        testQrCodeRoundTrip();
        testReadRgba();
        testNoBarcode();
        testInvalidArguments();

        System.out.println();
        System.out.println("========================================");
        System.out.println("All tests passed.");
        System.out.println("========================================");
    }

    /**
     * 测试 ZXing-C++ 版本。
     */
    private static void testVersion() {
        System.out.println();
        System.out.println("[TEST] version");

        String version = BarcodeReader.version();

        check(version != null && !version.isBlank(), "ZXing-C++ version is empty");

        System.out.println("ZXing-C++ version: " + version);
    }

    /**
     * 测试二维码编码、解码及原始字节一致性。
     */
    private static void testQrCodeRoundTrip() {
        System.out.println();
        System.out.println("[TEST] QR Code round trip");

        byte[] source = new byte[] {
                'Q', 'O', 'c', 'r', '-', 'T', 'e', 's', 't',
                0x00,
                0x01,
                0x7F,
                (byte) 0x80,
                (byte) 0xFE,
                (byte) 0xFF
        };

        BufferedImage image = ZXingCpp.encodeImage(
                source,
                IMAGE_SIZE,
                IMAGE_SIZE,
                4,
                QrConstants.EC_LEVEL_M,
                QrConstants.ECI_ISO_8859_1
        );

        check(image != null, "QR image is null");

        ReadOptions options = new ReadOptions()
                .setSymbologies(Symbology.QR_CODE)
                .setDecodeMode(DecodeMode.NORMAL)
                .setTryRotate(true)
                .setTryInvert(true)
                .setTryDownscale(true)
                .setMaxSymbols(10);

        BarcodeHit[] hits = BarcodeReader.read(image, options);

        check(hits != null, "BarcodeReader returned null");
        check(hits.length > 0, "QR Code not detected");

        BarcodeHit hit = hits[0];

        check(hit != null, "BarcodeHit is null");
        check(hit.isValid(), "BarcodeHit contains error: " + hit.error());

        byte[] decoded = hit.rawBytes();

        check(Arrays.equals(source, decoded), "Decoded bytes do not match source bytes");

        System.out.println("Detected count : " + hits.length);
        System.out.println("Format         : " + hit.format());
        System.out.println("Symbology      : " + hit.symbology());
        System.out.println("Raw bytes      : " + decoded.length);
        System.out.println("Orientation    : " + hit.orientation());
        System.out.println("Mirrored       : " + hit.mirrored());
        System.out.println("Inverted       : " + hit.inverted());
        System.out.println("Has ECI        : " + hit.hasECI());

        System.out.println("Raw bytes test : PASSED");
    }

    /**
     * 测试 RGBA 原始图像接口。
     */
    private static void testReadRgba() {
        System.out.println();
        System.out.println("[TEST] readRgba");

        byte[] source = "BarcodeReader RGBA Test".getBytes(StandardCharsets.UTF_8);

        BufferedImage image = ZXingCpp.encodeImage(
                source,
                IMAGE_SIZE,
                IMAGE_SIZE,
                4,
                QrConstants.EC_LEVEL_M,
                QrConstants.ECI_UTF8
        );

        byte[] rgba = toRgba(image);

        ReadOptions options = new ReadOptions()
                .setSymbologies(Symbology.QR_CODE)
                .setMaxSymbols(5);

        BarcodeHit[] hits = BarcodeReader.readRgba(
                rgba,
                image.getWidth(),
                image.getHeight(),
                image.getWidth() * 4,
                options
        );

        check(hits != null && hits.length > 0, "readRgba did not detect QR Code");

        BarcodeHit hit = hits[0];

        check(Arrays.equals(source, hit.rawBytes()), "readRgba decoded bytes mismatch");

        System.out.println("Image size     : " + image.getWidth() + " x " + image.getHeight());
        System.out.println("RGBA size      : " + rgba.length);
        System.out.println("Decoded text   : " + hit.text());
        System.out.println("readRgba       : PASSED");
    }

    /**
     * 测试空白图片。
     */
    private static void testNoBarcode() {
        System.out.println();
        System.out.println("[TEST] no barcode");

        BufferedImage image = new BufferedImage(
                IMAGE_SIZE,
                IMAGE_SIZE,
                BufferedImage.TYPE_INT_RGB
        );

        BarcodeHit[] hits = BarcodeReader.read(image);

        check(hits != null, "Result array should not be null");
        check(hits.length == 0, "Unexpected barcode detected in blank image");

        System.out.println("No barcode detected: PASSED");
    }

    /**
     * 测试非法参数。
     */
    private static void testInvalidArguments() {
        System.out.println();
        System.out.println("[TEST] invalid arguments");

        ReadOptions options = new ReadOptions();

        expectIllegalArgument(
                () -> BarcodeReader.readRgba(
                        new byte[4],
                        1,
                        1,
                        3,
                        options
                ),
                "rowStride"
        );

        expectIllegalArgument(
                () -> BarcodeReader.readRgba(
                        new byte[4],
                        2,
                        2,
                        8,
                        options
                ),
                "RGBA buffer size"
        );

        expectIllegalArgument(
                () -> options.setMaxSymbols(0),
                "maxSymbols"
        );

        System.out.println("Invalid arguments: PASSED");
    }

    /**
     * BufferedImage 转 RGBA。
     */
    private static byte[] toRgba(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();

        int[] argb = image.getRGB(0, 0, width, height, null, 0, width);
        byte[] rgba = new byte[Math.multiplyExact(argb.length, 4)];

        int p = 0;

        for (int pixel : argb) {
            rgba[p++] = (byte) ((pixel >>> 16) & 0xFF);
            rgba[p++] = (byte) ((pixel >>> 8) & 0xFF);
            rgba[p++] = (byte) (pixel & 0xFF);
            rgba[p++] = (byte) ((pixel >>> 24) & 0xFF);
        }

        return rgba;
    }

    /**
     * 断言。
     */
    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    /**
     * 验证是否抛出 IllegalArgumentException。
     */
    private static void expectIllegalArgument(Runnable action, String name) {
        try {
            action.run();
        } catch (IllegalArgumentException e) {
            System.out.println(name + ": " + e.getMessage());
            return;
        }

        throw new AssertionError("Expected IllegalArgumentException: " + name);
    }
}
