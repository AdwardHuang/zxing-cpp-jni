package com.hrz.zxingcpp;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Locale;

public final class ZXingCpp {

    private ZXingCpp() {
    }


    // ========================================================
    // ECI constants
    // ========================================================

    /**
     * ISO-8859-1。
     *
     * 对 byte[0..255] 可以做到一一映射。
     *
     * 适合 QrFT / 二进制数据兼容模式。
     */
    public static final int ECI_ISO_8859_1 = 3;


    /**
     * UTF-8。
     */
    public static final int ECI_UTF8 = 26;


    /**
     * 8-bit binary。
     */
    public static final int ECI_BINARY = 899;


    /**
     * 默认 ECI。
     *
     * 对 QrFT 来说推荐使用 3。
     */
    public static final int DEFAULT_ECI =
            ECI_ISO_8859_1;


    /**
     * 默认 QR Error Correction。
     *
     * 1 = L
     * 2 = M
     * 3 = Q
     * 4 = H
     */
    public static final int DEFAULT_EC_LEVEL = 2;


    /**
     * 默认 quiet zone。
     */
    public static final int DEFAULT_QUIET_ZONE = 4;


    // ========================================================
    // Native loading
    // ========================================================

    private static boolean loaded = false;

    private static Path nativeTempFile;


    static {
        loadNativeLibrary();
    }


    private static void loadNativeLibrary() {

        if (loaded) {
            return;
        }


        String os =
                System.getProperty("os.name")
                        .toLowerCase(Locale.ROOT);

        String arch =
                System.getProperty("os.arch")
                        .toLowerCase(Locale.ROOT);


        String resource;


        if (os.contains("win")) {

            if (isX86_64(arch)) {

                resource =
                        "/native/windows-x86_64/zxing_jni.dll";

            }
            else {

                throw new UnsupportedOperationException(
                        "Unsupported Windows architecture: "
                                + arch
                );

            }

        }
        else if (os.contains("linux")) {

            if (isX86_64(arch)) {

                resource =
                        "/native/linux-x86_64/libzxing_jni.so";

            }
            else if (isAarch64(arch)) {

                resource =
                        "/native/linux-aarch64/libzxing_jni.so";

            }
            else {

                throw new UnsupportedOperationException(
                        "Unsupported Linux architecture: "
                                + arch
                );

            }

        }
        else {

            throw new UnsupportedOperationException(
                    "Unsupported operating system: "
                            + os
            );

        }


        try (InputStream input =
                     ZXingCpp.class.getResourceAsStream(
                             resource
                     ))
        {

            if (input == null) {

                throw new IllegalStateException(
                        "Native library not found: "
                                + resource
                );

            }


            String fileName =
                    resource.substring(
                            resource.lastIndexOf('/') + 1
                    );


            String suffix =
                    fileName.endsWith(".dll")
                            ? ".dll"
                            : ".so";


            nativeTempFile =
                    Files.createTempFile(
                            "zxing_jni_",
                            suffix
                    );


            Files.copy(
                    input,
                    nativeTempFile,
                    StandardCopyOption.REPLACE_EXISTING
            );


            nativeTempFile.toFile().deleteOnExit();


            System.load(
                    nativeTempFile.toAbsolutePath()
                            .toString()
            );


            loaded = true;

        }
        catch (IOException e) {

            throw new ExceptionInInitializerError(e);

        }
    }


    private static boolean isX86_64(
            String arch)
    {
        return arch.equals("amd64")
                || arch.equals("x86_64")
                || arch.equals("x64");
    }


    private static boolean isAarch64(
            String arch)
    {
        return arch.equals("aarch64")
                || arch.equals("arm64");
    }


    // ========================================================
    // DecodeResult
    // ========================================================

    public static final class DecodeResult {

        private final byte[] bytes;

        private final boolean hasECI;

        private final int eci;

        private final String charset;

        private final String text;


        public DecodeResult(
                byte[] bytes,
                boolean hasECI,
                int eci,
                String charset,
                String text)
        {
            this.bytes =
                    bytes == null
                            ? new byte[0]
                            : bytes;

            this.hasECI =
                    hasECI;

            this.eci =
                    eci;

            this.charset =
                    charset;

            this.text =
                    text;
        }


        public byte[] getBytes() {
            return bytes;
        }


        public boolean hasECI() {
            return hasECI;
        }


        public int getECI() {
            return eci;
        }


        public String getCharset() {
            return charset;
        }


        public String getText() {
            return text;
        }


        /**
         * 按指定 charset 将 bytes 转成 String。
         */
        public String decodeString(
                String charsetName)
        {
            return new String(
                    bytes,
                    java.nio.charset.Charset.forName(
                            charsetName
                    )
            );
        }


        @Override
        public String toString() {

            return "DecodeResult{" +
                    "bytes=" + bytes.length +
                    ", hasECI=" + hasECI +
                    ", eci=" + eci +
                    ", charset='" + charset + '\'' +
                    ", text='" + text + '\'' +
                    '}';
        }
    }


    // ========================================================
    // Decode byte[]
    // ========================================================

    public static DecodeResult decodeBytes(
            byte[] rgba,
            int width,
            int height)
    {
        return decodeBytes(
                rgba,
                width,
                height,
                width * 4,
                true,
                true
        );
    }


    public static DecodeResult decodeBytes(
            byte[] rgba,
            int width,
            int height,
            int rowStride,
            boolean tryRotate,
            boolean tryInvert)
    {
        if (rgba == null) {

            throw new IllegalArgumentException(
                    "rgba == null"
            );

        }


        if (width <= 0 || height <= 0) {

            throw new IllegalArgumentException(
                    "width/height must be > 0"
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


    // ========================================================
    // Decode BufferedImage
    // ========================================================

    public static DecodeResult decodeBytes(
            BufferedImage image)
    {
        if (image == null) {

            throw new IllegalArgumentException(
                    "image == null"
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
                true,
                true
        );
    }


    /**
     * 兼容旧 API。
     */
    public static String decode(
            BufferedImage image)
    {
        DecodeResult result =
                decodeBytes(image);

        if (result == null) {
            return null;
        }

        return result.getText();
    }


    /**
     * ISO-8859-1 兼容模式。
     */
    public static String decodeIso88591(
            BufferedImage image)
    {
        DecodeResult result =
                decodeBytes(image);

        if (result == null) {
            return null;
        }

        return new String(
                result.getBytes(),
                StandardCharsets.ISO_8859_1
        );
    }


    // ========================================================
    // Encode byte[]
    // ========================================================

    /**
     * 默认编码：
     *
     * ECI = 3
     * ISO-8859-1
     * EC = M
     * quietZone = 4
     *
     * 推荐 QrFT 使用。
     */
    public static byte[] encodeBytes(
            byte[] data,
            int width,
            int height)
    {
        return encodeBytes(
                data,
                width,
                height,
                DEFAULT_QUIET_ZONE,
                DEFAULT_EC_LEVEL,
                DEFAULT_ECI
        );
    }


    /**
     * 指定 quietZone / EC。
     *
     * 默认 ECI=3。
     */
    public static byte[] encodeBytes(
            byte[] data,
            int width,
            int height,
            int quietZone,
            int ecLevel)
    {
        return encodeBytes(
                data,
                width,
                height,
                quietZone,
                ecLevel,
                DEFAULT_ECI
        );
    }


    /**
     * 完整编码接口。
     *
     * @param data       原始数据
     * @param width      输出图片宽度
     * @param height     输出图片高度
     * @param quietZone  QR quiet zone，单位 module
     * @param ecLevel    1=L, 2=M, 3=Q, 4=H
     * @param eci        ECI，例如 3 / 26 / 899
     *
     * @return 8-bit grayscale image
     */
    public static byte[] encodeBytes(
            byte[] data,
            int width,
            int height,
            int quietZone,
            int ecLevel,
            int eci)
    {
        validateEncodeArguments(
                data,
                width,
                height,
                quietZone,
                ecLevel,
                eci
        );


        return nativeEncodeBytes(
                data,
                width,
                height,
                quietZone,
                ecLevel,
                eci
        );
    }


    // ========================================================
    // Encode BufferedImage
    // ========================================================

    /**
     * 默认：
     *
     * ECI=3
     */
    public static BufferedImage encodeImage(
            byte[] data,
            int width,
            int height)
    {
        return encodeImage(
                data,
                width,
                height,
                DEFAULT_QUIET_ZONE,
                DEFAULT_EC_LEVEL,
                DEFAULT_ECI
        );
    }


    /**
     * ECI=3。
     */
    public static BufferedImage encodeImage(
            byte[] data,
            int width,
            int height,
            int quietZone,
            int ecLevel)
    {
        return encodeImage(
                data,
                width,
                height,
                quietZone,
                ecLevel,
                DEFAULT_ECI
        );
    }


    /**
     * 完整版本。
     */
    public static BufferedImage encodeImage(
            byte[] data,
            int width,
            int height,
            int quietZone,
            int ecLevel,
            int eci)
    {
        byte[] gray =
                encodeBytes(
                        data,
                        width,
                        height,
                        quietZone,
                        ecLevel,
                        eci
                );


        BufferedImage image =
                new BufferedImage(
                        width,
                        height,
                        BufferedImage.TYPE_BYTE_GRAY
                );


        WritableRaster raster =
                image.getRaster();


        raster.setDataElements(
                0,
                0,
                width,
                height,
                gray
        );


        return image;
    }


    // ========================================================
    // UTF-8
    // ========================================================

    /**
     * UTF-8 + ECI 26。
     */
    public static BufferedImage encodeUtf8(
            String text,
            int width,
            int height)
    {
        if (text == null) {

            throw new IllegalArgumentException(
                    "text == null"
            );

        }


        return encodeImage(
                text.getBytes(
                        StandardCharsets.UTF_8
                ),
                width,
                height,
                DEFAULT_QUIET_ZONE,
                DEFAULT_EC_LEVEL,
                ECI_UTF8
        );
    }


    /**
     * ISO-8859-1 + ECI 3。
     *
     * byte[] 一一映射。
     */
    public static BufferedImage encodeBytesCompatible(
            byte[] data,
            int width,
            int height)
    {
        return encodeImage(
                data,
                width,
                height,
                DEFAULT_QUIET_ZONE,
                DEFAULT_EC_LEVEL,
                ECI_ISO_8859_1
        );
    }


    /**
     * Binary + ECI 899。
     */
    public static BufferedImage encodeBinary(
            byte[] data,
            int width,
            int height)
    {
        return encodeImage(
                data,
                width,
                height,
                DEFAULT_QUIET_ZONE,
                DEFAULT_EC_LEVEL,
                ECI_BINARY
        );
    }


    // ========================================================
    // Validation
    // ========================================================

    private static void validateEncodeArguments(
            byte[] data,
            int width,
            int height,
            int quietZone,
            int ecLevel,
            int eci)
    {
        if (data == null) {

            throw new IllegalArgumentException(
                    "data == null"
            );

        }


        if (data.length == 0) {

            throw new IllegalArgumentException(
                    "data is empty"
            );

        }


        if (width <= 0 ||
            height <= 0)
        {
            throw new IllegalArgumentException(
                    "width/height must be > 0"
            );
        }


        if (quietZone < 0) {

            throw new IllegalArgumentException(
                    "quietZone must be >= 0"
            );

        }


        if (ecLevel < 1 ||
            ecLevel > 4)
        {
            throw new IllegalArgumentException(
                    "ecLevel must be 1(L), 2(M), 3(Q), 4(H)"
            );
        }


        if (eci < 0 ||
            eci > 999999)
        {
            throw new IllegalArgumentException(
                    "ECI must be between 0 and 999999"
            );
        }
    }


    // ========================================================
    // Version
    // ========================================================

    public static String version()
    {
        return nativeVersion();
    }


    // ========================================================
    // Native methods
    // ========================================================

    private static native DecodeResult nativeDecodeBytes(
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
            int ecLevel,
            int eci
    );


    private static native String nativeVersion();
}
