package com.hrz.zxingcpp;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Random;


/**
 * ZXing-C++ JNI Demo.
 */
public final class ZXingCppDemo {

    private ZXingCppDemo() {
    }


    public static void main(
            String[] args) throws Exception {

        System.out.println(
                "ZXing-C++ version: "
                        + ZXingCpp.version()
        );

        /*
         * ========================================================
         * 1. 二进制 Encode -> Decode 测试
         * ========================================================
         */
        testBinaryEncodeDecode();


        /*
         * ========================================================
         * 2. 如果传入图片，则测试图片解码
         * ========================================================
         */
        if (args.length == 1) {

            decodeImage(
                    new File(args[0])
            );
        }
    }


    /**
     * 测试：
     *
     * byte[]
     *     ↓
     * ZXing-C++ Encode
     *     ↓
     * BufferedImage
     *     ↓
     * ZXing-C++ Decode
     *     ↓
     * byte[]
     */
    private static void testBinaryEncodeDecode() {

        System.out.println();
        System.out.println(
                "========== Binary Encode/Decode =========="
        );

        int[] sizes = {
                10,
                100,
                500,
                1000,
                1500,
                2000,
                2500
        };

        for (int size : sizes) {

            testBinaryEncodeDecode(
                    size
            );
        }
    }


    /**
     * 测试指定长度的随机二进制数据。
     */
    private static void testBinaryEncodeDecode(
            int size) {

        byte[] original =
                new byte[size];

        Random random =
                new Random(
                        123456789L + size
                );

        random.nextBytes(
                original
        );

        long start =
                System.nanoTime();

        BufferedImage image =
                ZXingCpp.encodeImage(
                        original,
                        600,
                        600
                );

        long encodeEnd =
                System.nanoTime();

        byte[] decoded =
                ZXingCpp.decodeBytes(
                        image
                );

        long decodeEnd =
                System.nanoTime();

        boolean equals =
                Arrays.equals(
                        original,
                        decoded
                );

        double encodeMs =
                (encodeEnd - start)
                        / 1_000_000.0;

        double decodeMs =
                (decodeEnd - encodeEnd)
                        / 1_000_000.0;

        System.out.println(
                "size="
                        + size
                        + ", decoded="
                        + (decoded == null
                        ? -1
                        : decoded.length)
                        + ", equals="
                        + equals
                        + ", encode="
                        + String.format(
                                "%.3f",
                                encodeMs
                        )
                        + " ms"
                        + ", decode="
                        + String.format(
                                "%.3f",
                                decodeMs
                        )
                        + " ms"
        );

        if (!equals) {

            throw new IllegalStateException(
                    "Binary encode/decode failed, size="
                            + size
            );
        }
    }


    /**
     * 测试图片解码。
     */
    private static void decodeImage(
            File file) throws Exception {

        System.out.println();
        System.out.println(
                "========== Image Decode =========="
        );

        System.out.println(
                "Image: "
                        + file.getAbsolutePath()
        );

        BufferedImage image =
                ImageIO.read(file);

        if (image == null) {

            throw new IllegalArgumentException(
                    "Unsupported image: "
                            + file
        );
        }

        byte[] bytes =
                ZXingCpp.decodeBytes(
                        image
                );

        if (bytes == null) {

            System.out.println(
                    "No QR code found."
            );

            return;
        }

        System.out.println(
                "Decoded bytes: "
                        + bytes.length
        );

        System.out.println(
                "ISO-8859-1: "
                        + new String(
                                bytes,
                                StandardCharsets.ISO_8859_1
                        )
        );
    }
}
