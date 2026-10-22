package com.hrz.zxingcpp;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Minimal Java API for ZXing-C++ through JNI.
 *
 * <p>The native library is packaged inside the JAR under native/&lt;os&gt;-&lt;arch&gt;.
 * Supported by the release workflow: Windows x86_64 and Linux x86_64.</p>
 */
public final class ZXingCpp {

    private static final String NATIVE_NAME = "zxing_jni";
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
            try (InputStream in = ZXingCpp.class.getResourceAsStream(resource)) {
                if (in == null) {
                    throw new UnsatisfiedLinkError("Native library not found in JAR: " + resource);
                }

                String suffix = resource.endsWith(".dll") ? ".dll" : ".so";
                java.nio.file.Path temp = java.nio.file.Files.createTempFile("zxing-jni-", suffix);
                java.nio.file.Files.deleteIfExists(temp);
                temp.toFile().deleteOnExit();

                try (java.io.OutputStream out = java.nio.file.Files.newOutputStream(temp)) {
                    byte[] buffer = new byte[8192];
                    int n;
                    while ((n = in.read(buffer)) != -1) {
                        out.write(buffer, 0, n);
                    }
                }

                System.load(temp.toAbsolutePath().toString());
                loaded = true;
            } catch (IOException e) {
                UnsatisfiedLinkError error = new UnsatisfiedLinkError("Failed to extract native library: " + resource);
                error.initCause(e);
                throw error;
            }
        }
    }

    private static String nativeResourcePath() {
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        String arch = normalizeArch(System.getProperty("os.arch", ""));

        if (os.contains("win")) {
            return "/native/windows-" + arch + "/zxing_jni.dll";
        }
        if (os.contains("linux")) {
            return "/native/linux-" + arch + "/libzxing_jni.so";
        }

        throw new UnsupportedOperationException("Unsupported OS: " + os);
    }

    private static String normalizeArch(String arch) {
        String value = arch.toLowerCase(java.util.Locale.ROOT);
        if ("amd64".equals(value) || "x86_64".equals(value)) {
            return "x86_64";
        }
        if ("aarch64".equals(value) || "arm64".equals(value)) {
            return "aarch64";
        }
        return value;
    }

    /**
     * Decodes a QR code from RGBA8888 pixels and returns the raw barcode bytes.
     * The returned byte[] is suitable for QrFT/OpenRQ packet data.
     *
     * @return decoded raw bytes, or null when no barcode is found
     */
    public static byte[] decodeBytes(byte[] rgba, int width, int height, int rowStride,
                                     boolean tryRotate, boolean tryInvert) {
        if (rgba == null) {
            throw new NullPointerException("rgba");
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Invalid image size: " + width + "x" + height);
        }
        if (rowStride < width * 4) {
            throw new IllegalArgumentException("rowStride is too small");
        }
        return nativeDecodeBytes(rgba, width, height, rowStride, tryRotate, tryInvert);
    }

    /**
     * Convenience method for a Java BufferedImage.
     * Pixels are converted to RGBA before entering JNI.
     */
    public static byte[] decodeBytes(BufferedImage image) {
        return decodeBytes(image, true, true);
    }

    public static byte[] decodeBytes(BufferedImage image, boolean tryRotate, boolean tryInvert) {
        if (image == null) {
            throw new NullPointerException("image");
        }

        int width = image.getWidth();
        int height = image.getHeight();
        int[] argb = image.getRGB(0, 0, width, height, null, 0, width);
        byte[] rgba = new byte[argb.length * 4];

        int p = 0;
        for (int pixel : argb) {
            rgba[p++] = (byte) ((pixel >>> 16) & 0xff);
            rgba[p++] = (byte) ((pixel >>> 8) & 0xff);
            rgba[p++] = (byte) (pixel & 0xff);
            rgba[p++] = (byte) ((pixel >>> 24) & 0xff);
        }

        return decodeBytes(rgba, width, height, width * 4, tryRotate, tryInvert);
    }

    /**
     * Decodes and converts the raw bytes to ISO-8859-1 String.
     * This is useful for the current QrFT protocol where packet.asArray()
     * is transported through an ISO-8859-1 one-byte String mapping.
     */
    public static String decodeIso88591(BufferedImage image) {
        byte[] bytes = decodeBytes(image);
        return bytes == null ? null : new String(bytes, StandardCharsets.ISO_8859_1);
    }

    /**
     * Returns the ZXing-C++ version compiled into the native library.
     */
    public static String version() {
        return nativeVersion();
    }

    private static native byte[] nativeDecodeBytes(byte[] rgba, int width, int height, int rowStride,
                                                   boolean tryRotate, boolean tryInvert);

    private static native String nativeVersion();
}
