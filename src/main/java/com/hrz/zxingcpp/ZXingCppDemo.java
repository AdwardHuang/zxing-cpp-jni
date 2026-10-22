package com.hrz.zxingcpp;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;

/**
 * Simple command-line demo.
 */
public final class ZXingCppDemo {

    private ZXingCppDemo() {
    }

    public static void main(String[] args) throws Exception {

        if (args.length != 1) {
            System.err.println(
                    "Usage: java -cp zxing-cpp-jni.jar " +
                    "com.hrz.zxingcpp.ZXingCppDemo <image>"
            );

            System.exit(2);
        }


        BufferedImage image =
                ImageIO.read(
                        new File(args[0])
                );


        if (image == null) {
            throw new IllegalArgumentException(
                    "Unsupported image: " + args[0]
            );
        }


        System.out.println(
                "ZXing-C++ version: " +
                ZXingCpp.version()
        );


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
                "Decoded bytes: " +
                bytes.length
        );


        System.out.println(
                "ISO-8859-1: " +
                new String(
                        bytes,
                        StandardCharsets.ISO_8859_1
                )
        );
    }
}
