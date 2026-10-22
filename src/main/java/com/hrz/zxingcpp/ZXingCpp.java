package com.hrz.zxingcpp;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;


/**
 * Java wrapper for ZXing-C++ through JNI.
 */
public final class ZXingCpp {

    private static volatile boolean loaded;


    private ZXingCpp() {
    }


    static {
        loadNative();
    }


    private static void loadNative() {

        if (loaded) {
            return;
        }

        synchronized (ZXingCpp.class) {

            if (loaded) {
                return;
            }

            String resource =
                    nativeResourcePath();

            try (InputStream in =
                         ZXingCpp.class.getResourceAsStream(
                                 resource
                         )) {

                if (in == null) {

                    throw new UnsatisfiedLinkError(
                            "Native library not found in JAR: "
                                    + resource
                    );
                }

                String suffix =
                        resource.endsWith(".dll")
                                ? ".dll"
                                : ".so";

                Path temp =
                        Files.createTempFile(
                                "zxing-jni-",
                                suffix
                        );

                Files.deleteIfExists(temp);

                temp.toFile().deleteOnExit();

                try (OutputStream out =
                             Files.newOutputStream(temp)) {

                    byte[] buffer =
                            new byte[8192];

                    int n;

                    while ((n = in.read(buffer))
                            != -1) {

                        out.write(
                                buffer,
                                0,
                                n
                        );
                    }
                }

                System.load(
                        temp.toAbsolutePath()
                                .toString()
                );

                loaded = true;

            } catch (IOException e) {

                UnsatisfiedLinkError error =
                        new UnsatisfiedLinkError(
                                "Failed to extract native library: "
                                        + resource
                        );

                error.initCause(e);

                throw error;
            }
        }
    }


    private static String nativeResourcePath() {

        String os =
                System.getProperty(
                        "os.name",
                        ""
                ).toLowerCase(Locale.ROOT);

        String arch =
                normalizeArch(
                        System.getProperty(
                                "os.arch",
                                ""
                        )
                );

        if (os.contains("win")) {

            return "/native/windows-"
                    + arch
                    + "/zxing_jni.dll";
        }

        if (os.contains("linux")) {

            return "/native/linux-"
                    + arch
                    + "/libzxing_jni.so";
        }

        throw new UnsupportedOperationException(
                "Unsupported OS: " + os
        );
    }


    private static String normalizeArch(
            String arch) {

        String value =
                arch.toLowerCase(Locale.ROOT);

        if ("amd64".equals(value)
                || "x86_64".equals(value)) {

            return "x86_64";
        }

        if ("aarch64".equals(value)
                || "arm64".equals(value)) {

            return "aarch64";
        }

        return value;
    }


    /**
     * ============================================================
     * DecodeResult
     * ============================================================
     */
    public static final class DecodeResult {

        private final byte[] bytes;

        private final boolean hasECI;

        private final int eci;

        private final String charset;

        private final String text;


        private DecodeResult(
                byte[] bytes,
                boolean hasECI,
                int eci,
                String charset,
                String text) {

            this.bytes = bytes;

            this.hasECI = hasECI;

            this.eci = eci;

            this.charset = charset;

            this.text = text;
        }


        public byte[] getBytes() {
            return bytes;
        }


        public boolean hasECI() {
            return hasECI;
        }


        public int getEci() {
            return eci;
        }


        public String getCharset() {
            return charset;
        }


        public String getText() {
            return text;
        }


        /**
         * 根据二维码中的 ECI 将 bytes
         * 转换成 Java String。
         */
        public String decodeString(
                Charset fallbackCharset) {

            if (bytes == null) {
                return null;
            }

            Charset charsetToUse =
                    fallbackCharset;

            if (hasECI
                    && charset != null
                    && !charset.isEmpty()) {

                try {

                    charsetToUse =
                            Charset.forName(
                                    charset
                            );

                } catch (Exception ignored) {

                    /*
                     * Java 不支持该 Charset，
                     * 使用 fallback。
                     */
                }
            }

            if (charsetToUse == null) {

                charsetToUse =
                        StandardCharsets.UTF_8;
            }

            return new String(
                    bytes,
                    charsetToUse
            );
        }


        /**
         * 使用 UTF-8 作为 fallback。
         */
        public String decodeString() {

            return decodeString(
                    StandardCharsets.UTF_8
            );
        }


        @Override
        public String toString() {

            return "DecodeResult{"
                    + "bytes="
                    + (bytes == null
                    ? 0
                    : bytes.length)
                    + ", hasECI="
                    + hasECI
                    + ", eci="
                    + eci
                    + ", charset='"
                    + charset
                    + '\''
                    + ", text='"
                    + text
                    + '\''
                    + '}';
        }
    }


    /**
     * ============================================================
     * Decode
     * ============================================================
     */


    /**
     * RGBA byte[] -> 原始 bytes。
     *
     * QrFT/OpenRQ 推荐使用。
     */
    public static byte[] decodeBytes(
            byte[] rgba,
            int width,
            int height,
            int rowStride,
            boolean tryRotate,
            boolean tryInvert) {

        if (rgba == null) {

            throw new NullPointerException(
                    "rgba"
            );
        }

        if (width <= 0
                || height <= 0) {

            throw new IllegalArgumentException(
                    "Invalid image size: "
                            + width
                            + "x"
                            + height
            );
        }

        if (rowStride < width * 4) {

            throw new IllegalArgumentException(
                    "rowStride is too small"
            );
        }

        return nativeDecodeBytes(
                rgba,
                width,
                height,
                rowStride,
                tryRotate,
                tryInvert
        );
    }


    /**
     * BufferedImage -> 原始 bytes。
     */
    public static byte[] decodeBytes(
            BufferedImage image) {

        return decodeBytes(
                image,
                true,
                true
        );
    }


    /**
     * BufferedImage -> 原始 bytes。
     */
    public static byte[] decodeBytes(
            BufferedImage image,
            boolean tryRotate,
            boolean tryInvert) {

        if (image == null) {

            throw new NullPointerException(
                    "image"
            );
        }

        int width =
                image.getWidth();

        int height =
                image.getHeight();

        int[] argb =
                image.getRGB(
                        0,
                        0,
                        width,
                        height,
                        null,
                        0,
                        width
                );

        byte[] rgba =
                new byte[
                        argb.length * 4
                ];

        int p = 0;

        for (int pixel : argb) {

            rgba[p++] =
                    (byte) ((pixel >>> 16)
                            & 0xff);

            rgba[p++] =
                    (byte) ((pixel >>> 8)
                            & 0xff);

            rgba[p++] =
                    (byte) (pixel & 0xff);

            rgba[p++] =
                    (byte) ((pixel >>> 24)
                            & 0xff);
        }

        return decodeBytes(
                rgba,
                width,
                height,
                width * 4,
                tryRotate,
                tryInvert
        );
    }


    /**
     * 完整二维码解码。
     */
    public static DecodeResult decode(
            BufferedImage image) {

        return decode(
                image,
                true,
                true
        );
    }


    /**
     * 完整二维码解码。
     */
    public static DecodeResult decode(
            BufferedImage image,
            boolean tryRotate,
            boolean tryInvert) {

        if (image == null) {

            throw new NullPointerException(
                    "image"
            );
        }

        int width =
                image.getWidth();

        int height =
                image.getHeight();

        int[] argb =
                image.getRGB(
                        0,
                        0,
                        width,
                        height,
                        null,
                        0,
                        width
                );

        byte[] rgba =
                new byte[
                        argb.length * 4
                ];

        int p = 0;

        for (int pixel : argb) {

            rgba[p++] =
                    (byte) ((pixel >>> 16)
                            & 0xff);

            rgba[p++] =
                    (byte) ((pixel >>> 8)
                            & 0xff);

            rgba[p++] =
                    (byte) (pixel & 0xff);

            rgba[p++] =
                    (byte) ((pixel >>> 24)
                            & 0xff);
        }

        return nativeDecode(
                rgba,
                width,
                height,
                width * 4,
                tryRotate,
                tryInvert
        );
    }


    /**
     * 自动根据二维码 ECI 解码 String。
     */
    public static String decodeString(
            BufferedImage image) {

        DecodeResult result =
                decode(image);

        if (result == null) {
            return null;
        }

        if (result.getText() != null) {
            return result.getText();
        }

        return result.decodeString();
    }


    /**
     * 使用指定 fallback Charset。
     */
    public static String decodeString(
            BufferedImage image,
            Charset fallbackCharset) {

        DecodeResult result =
                decode(image);

        if (result == null) {
            return null;
        }

        if (result.hasECI()
                && result.getText() != null) {

            return result.getText();
        }

        return result.decodeString(
                fallbackCharset
        );
    }


    /**
     * 兼容原来的 QrFT API。
     */
    public static String decodeIso88591(
            BufferedImage image) {

        byte[] bytes =
                decodeBytes(image);

        return bytes == null
                ? null
                : new String(
                        bytes,
                        StandardCharsets.ISO_8859_1
                );
    }


    /**
     * ============================================================
     * Encode
     * ============================================================
     */


    /**
     * byte[] -> QR 灰度像素。
     *
     * 默认：
     *
     * 600 x 600
     *
     * 实际使用时可以指定尺寸。
     *
     * quietZone = 4
     * ecLevel = -1
     */
    public static byte[] encodeBytes(
            byte[] data,
            int width,
            int height) {

        return encodeBytes(
                data,
                width,
                height,
                4,
                -1
        );
    }


    /**
     * byte[] -> QR 灰度像素。
     *
     * 返回：
     *
     * width * height
     *
     * 每一个 byte：
     *
     * 0   = 黑色
     * 255 = 白色
     *
     * 参数：
     *
     * quietZone：
     *     最小 quiet zone 像素。
     *
     * ecLevel：
     *
     *     -1 = 默认
     *      1 = L
     *      2 = M
     *      3 = Q
     *      4 = H
     */
    public static byte[] encodeBytes(
            byte[] data,
            int width,
            int height,
            int quietZone,
            int ecLevel) {

        if (data == null) {

            throw new NullPointerException(
                    "data"
            );
        }

        if (data.length == 0) {

            throw new IllegalArgumentException(
                    "data is empty"
            );
        }

        if (width <= 0
                || height <= 0) {

            throw new IllegalArgumentException(
                    "Invalid image size: "
                            + width
                            + "x"
                            + height
            );
        }

        if (quietZone < 0) {

            throw new IllegalArgumentException(
                    "quietZone must be >= 0"
            );
        }

        if (ecLevel != -1
                && ecLevel != 1
                && ecLevel != 2
                && ecLevel != 3
                && ecLevel != 4) {

            throw new IllegalArgumentException(
                    "ecLevel must be "
                            + "-1, 1, 2, 3 or 4"
            );
        }

        return nativeEncodeBytes(
                data,
                width,
                height,
                quietZone,
                ecLevel
        );
    }


    /**
     * byte[] -> BufferedImage。
     *
     * 使用 TYPE_BYTE_GRAY。
     */
    public static BufferedImage encodeImage(
            byte[] data,
            int width,
            int height) {

        return encodeImage(
                data,
                width,
                height,
                4,
                -1
        );
    }


    /**
     * byte[] -> BufferedImage。
     */
    public static BufferedImage encodeImage(
            byte[] data,
            int width,
            int height,
            int quietZone,
            int ecLevel) {

        byte[] pixels =
                encodeBytes(
                        data,
                        width,
                        height,
                        quietZone,
                        ecLevel
                );

        BufferedImage image =
                new BufferedImage(
                        width,
                        height,
                        BufferedImage.TYPE_BYTE_GRAY
                );

        /*
         * TYPE_BYTE_GRAY 的底层就是 byte[]。
         *
         * 这里直接写入 Raster。
         */
        WritableRaster raster =
                image.getRaster();

        raster.setDataElements(
                0,
                0,
                width,
                height,
                pixels
        );

        return image;
    }


    /**
     * ============================================================
     * Native
     * ============================================================
     */

    private static native byte[] nativeDecodeBytes(
            byte[] rgba,
            int width,
            int height,
            int rowStride,
            boolean tryRotate,
            boolean tryInvert
    );


    private static native DecodeResult nativeDecode(
            byte[] rgba,
            int width,
            int height,
            int rowStride,
            boolean tryRotate,
            boolean tryInvert
    );


    private static native byte[] nativeEncodeBytes(
            byte[] data,
            int width,
            int height,
            int quietZone,
            int ecLevel
    );


    private static native String nativeVersion();


    /**
     * 返回 ZXing-C++ 版本。
     */
    public static String version() {
        return nativeVersion();
    }
}
