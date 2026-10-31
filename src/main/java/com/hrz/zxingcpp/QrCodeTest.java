package com.hrz.zxingcpp;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * @author HRZ
 */
public class QrCodeTest {

    public static void main(String[] args) {

        byte[] source = ("QOcr ZXing-C++ JNI Test 1234567890").getBytes(StandardCharsets.ISO_8859_1);

        try (QrCode qr = QrCode.encodeBytesCompatible(source)) {

            System.out.println("ZXing-C++ version: " + ZXingCpp.version());
            System.out.println("QR module count: " + qr.size());
            System.out.println("QR version: " + qr.version());
            System.out.println("EC level: " + qr.errorCorrection());
            System.out.println("Mask: " + qr.mask());

            int moduleSize = qr.calculateModuleSize(500, QrConstants.DEFAULT_QUIET_ZONE);

            System.out.println("Calculated moduleSize: " + moduleSize);

            BufferedImage image = qr.toImage(moduleSize);

            System.out.println("Image size: " + image.getWidth() + " x " + image.getHeight());

            System.out.println("Module (0,0) dark: " + qr.isDark(0, 0));

            ZXingCpp.DecodeResult result = ZXingCpp.decodeBytes(image);

            if (result == null) {
                throw new IllegalStateException("QR decode failed");
            }

            byte[] decoded = result.getBytes();

            System.out.println("Decoded bytes: " + decoded.length);
            System.out.println("Decoded text: " + result.getText());

            if (!Arrays.equals(source, decoded)) {
                throw new IllegalStateException("Decoded data does not match source data");
            }

            System.out.println("QR encode/decode test passed.");
        }
    }
}