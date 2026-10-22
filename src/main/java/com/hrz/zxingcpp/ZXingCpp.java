package com.hrz.zxingcpp;

import java.awt.image.BufferedImage;
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

            String resource = nativeResourcePath();

            try (InputStream in =
                         ZXingCpp.class.getResourceAsStream(resource)) {

                if (in == null) {
                    throw new UnsatisfiedLinkError(
                            "Native library not found in JAR: "
                                    + resource);
                }

                String suffix =
                        resource.endsWith(".dll")
                                ? ".dll"
                                : ".so";

                Path temp =
                        Files.createTempFile(
                                "zxing-jni-",
                                suffix);

                Files.deleteIfExists(temp);

                temp.toFile().deleteOnExit();

                try (OutputStream out =
                             Files.newOutputStream(temp)) {

                    byte[] buffer = new byte[8192];

                    int n;

                    while ((n = in.read(buffer)) != -1) {
                        out.write(buffer, 0, n);
                    }
                }

                System.load(
                        temp.toAbsolutePath().toString());

                loaded = true;

            } catch (IOException e) {

                UnsatisfiedLinkError error =
                        new UnsatisfiedLinkError(
                                "Failed to extract native library: "
                                        + resource);

                error.initCause(e);

                throw error;
            }
        }
    }

    private static String nativeResourcePath() {

        String os =
                System.getProperty(
                        "os.name",
                        "")
                        .toLowerCase(Locale.ROOT);

        String arch =
                normalizeArch(
                        System.getProperty(
                                "os.arch",
                                ""));

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
                "Unsupported OS: " + os);
    }

    private static String normalizeArch(String arch) {

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
     * 二维码完整解码结果。
     *
     * <p>
     * bytes：
     * 二维码解码后的原始 bytes。
     *
     * <p>
     * hasECI：
     * 二维码中是否明确存在 ECI。
     *
     * <p>
     * eci：
     * ECI 数值。
     * 没有 ECI 时返回 -1。
     *
     * <p>
     * charset：
     * ECI 对应的字符集。
     * 例如 UTF-8、ISO-8859-1、Shift_JIS。
     *
     * <p>
     * text：
     * ZXing-C++ 根据 ECI 转换后的 UTF-8 文本。
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
         * 根据二维码中的 ECI 将 bytes 转换成 Java String。
         *
         * <p>
         * 如果二维码存在 ECI，并且 charset 是 Java 支持的字符集，
         * 则直接按照该字符集转换。
         *
         * <p>
         * 如果没有 ECI，则使用 fallbackCharset。
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
                            Charset.forName(charset);

                } catch (Exception ignored) {
                    /*
                     * ZXing-C++ 已经给出了 charset，
                     * 但是 Java 当前环境可能没有对应
                     * Charset。
                     *
                     * 此时使用 fallbackCharset。
                     */
                }
            }

            if (charsetToUse == null) {
                charsetToUse =
                        StandardCharsets.UTF_8;
            }

            return new String(
                    bytes,
                    charsetToUse);
        }

        /**
         * 使用 UTF-8 作为没有 ECI 时的 fallback。
         */
        public String decodeString() {
            return decodeString(
                    StandardCharsets.UTF_8);
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
     * BufferedImage → 原始 bytes。
     *
     * <p>
     * 这是 QrFT/OpenRQ 推荐使用的 API。
     *
     * <p>
     * 不进行任何字符集转换。
     */
    public static byte[] decodeBytes(
            byte[] rgba,
            int width,
            int height,
            int rowStride,
            boolean tryRotate,
            boolean tryInvert) {

        if (rgba == null) {
            throw new NullPointerException("rgba");
        }

        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException(
                    "Invalid image size: "
                            + width
                            + "x"
                            + height);
        }

        if (rowStride < width * 4) {
            throw new IllegalArgumentException(
                    "rowStride is too small");
        }

        return nativeDecodeBytes(
                rgba,
                width,
                height,
                rowStride,
                tryRotate,
                tryInvert);
    }


    /**
     * BufferedImage → 原始 bytes。
     */
    public static byte[] decodeBytes(
            BufferedImage image) {

        return decodeBytes(
                image,
                true,
                true);
    }


    /**
     * BufferedImage → 原始 bytes。
     */
    public static byte[] decodeBytes(
            BufferedImage image,
            boolean tryRotate,
            boolean tryInvert) {

        if (image == null) {
            throw new NullPointerException(
                    "image");
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
                        width);

        byte[] rgba =
                new byte[argb.length * 4];

        int p = 0;

        for (int pixel : argb) {

            rgba[p++] =
                    (byte) ((pixel >>> 16) & 0xff);

            rgba[p++] =
                    (byte) ((pixel >>> 8) & 0xff);

            rgba[p++] =
                    (byte) (pixel & 0xff);

            rgba[p++] =
                    (byte) ((pixel >>> 24) & 0xff);
        }

        return decodeBytes(
                rgba,
                width,
                height,
                width * 4,
                tryRotate,
                tryInvert);
    }


    /**
     * 完整解码。
     *
     * <p>
     * 同时返回：
     *
     * <ul>
     *     <li>原始 bytes</li>
     *     <li>ECI</li>
     *     <li>字符集</li>
     *     <li>ZXing-C++ 转换后的 text</li>
     * </ul>
     */
    public static DecodeResult decode(
            BufferedImage image) {

        return decode(
                image,
                true,
                true);
    }


    /**
     * 完整解码。
     */
    public static DecodeResult decode(
            BufferedImage image,
            boolean tryRotate,
            boolean tryInvert) {

        if (image == null) {
            throw new NullPointerException(
                    "image");
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
                        width);

        byte[] rgba =
                new byte[argb.length * 4];

        int p = 0;

        for (int pixel : argb) {

            rgba[p++] =
                    (byte) ((pixel >>> 16) & 0xff);

            rgba[p++] =
                    (byte) ((pixel >>> 8) & 0xff);

            rgba[p++] =
                    (byte) (pixel & 0xff);

            rgba[p++] =
                    (byte) ((pixel >>> 24) & 0xff);
        }

        return nativeDecode(
                rgba,
                width,
                height,
                width * 4,
                tryRotate,
                tryInvert);
    }


    /**
     * 自动根据二维码 ECI 解码 String。
     *
     * <p>
     * 如果二维码没有 ECI，
     * 默认使用 UTF-8。
     */
    public static String decodeString(
            BufferedImage image) {

        DecodeResult result =
                decode(image);

        if (result == null) {
            return null;
        }

        /*
         * 对于有明确 ECI 的二维码，
         * ZXing-C++ 已经根据 ECI 生成了 text。
         *
         * 直接使用 text 是最准确的。
         */
        if (result.getText() != null) {
            return result.getText();
        }

        return result.decodeString();
    }


    /**
     * 使用指定 fallback Charset 解码 String。
     *
     * <p>
     * 如果二维码存在 ECI，则优先使用二维码中的 ECI。
     *
     * <p>
     * 如果二维码没有 ECI，则使用 fallbackCharset。
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
                fallbackCharset);
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
                        StandardCharsets.ISO_8859_1);
    }


    /**
     * 返回 ZXing-C++ 版本。
     */
    public static String version() {
        return nativeVersion();
    }


    private static native byte[] nativeDecodeBytes(
            byte[] rgba,
            int width,
            int height,
            int rowStride,
            boolean tryRotate,
            boolean tryInvert);


    private static native DecodeResult nativeDecode(
            byte[] rgba,
            int width,
            int height,
            int rowStride,
            boolean tryRotate,
            boolean tryInvert);


    private static native String nativeVersion();
}
