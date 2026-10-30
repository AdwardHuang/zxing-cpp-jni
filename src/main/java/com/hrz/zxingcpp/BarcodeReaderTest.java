
package com.hrz.zxingcpp;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BarcodeReaderTest {

    @Test
    void testVersion() {
        String version = BarcodeReader.version();
        assertNotNull(version);
        assertFalse(version.isBlank());
        System.out.println("ZXing-C++ version: " + version);
    }

    @Test
    void testQrCodeText() {
        String expected = "Hello ZXing-C++ JNI 3.1.1";
        byte[] data = expected.getBytes(StandardCharsets.UTF_8);

        BufferedImage image = ZXingCpp.encodeImage(data, 400, 400,
                ZXingCpp.DEFAULT_QUIET_ZONE,
                ZXingCpp.EC_LEVEL_M,
                ZXingCpp.ECI_UTF8);

        BarcodeReader reader = new BarcodeReader();
        List<BarcodeHit> results = reader.read(image);

        assertFalse(results.isEmpty());

        BarcodeHit hit = results.get(0);
        assertEquals(Symbology.QR_CODE, hit.symbology());
        assertEquals(expected, hit.text());
        assertArrayEquals(data, hit.bytes());

        System.out.println(hit);
    }

    @Test
    void testBinaryBytes() {
        byte[] expected = new byte[]{
                0, 1, 2, 3, 10, 13, 127, (byte) 128,
                (byte) 200, (byte) 255
        };

        BufferedImage image = ZXingCpp.encodeImage(expected, 400, 400,
                ZXingCpp.DEFAULT_QUIET_ZONE,
                ZXingCpp.EC_LEVEL_M,
                ZXingCpp.ECI_ISO_8859_1);

        BarcodeReader reader = new BarcodeReader();
        BarcodeHit hit = reader.readFirst(image);

        assertNotNull(hit);
        assertArrayEquals(expected, hit.bytes());
        assertEquals(expected.length, hit.bytes().length);
    }

    @Test
    void testQrCodeOnly() {
        byte[] data = "QR_ONLY_TEST".getBytes(StandardCharsets.UTF_8);
        BufferedImage image = ZXingCpp.encodeImage(data, 300, 300);

        BarcodeReader reader = new BarcodeReader(ReadOptions.qrCodeOnly());
        BarcodeHit hit = reader.readFirst(image);

        assertNotNull(hit);
        assertEquals(Symbology.QR_CODE, hit.symbology());
    }

    @Test
    void testDecodeModes() {
        byte[] data = "DECODE_MODE_TEST".getBytes(StandardCharsets.UTF_8);
        BufferedImage image = ZXingCpp.encodeImage(data, 300, 300);

        for (DecodeMode mode : DecodeMode.values()) {
            ReadOptions options = ReadOptions.builder()
                    .symbologies(Symbology.QR_CODE)
                    .decodeMode(mode)
                    .build();

            BarcodeReader reader = new BarcodeReader(options);
            BarcodeHit hit = reader.readFirst(image);

            assertNotNull(hit, "Failed with mode: " + mode);
            assertArrayEquals(data, hit.bytes());
        }
    }

    @Test
    void testPosition() {
        byte[] data = "POSITION_TEST".getBytes(StandardCharsets.UTF_8);
        BufferedImage image = ZXingCpp.encodeImage(data, 400, 400);

        BarcodeHit hit = new BarcodeReader().readFirst(image);

        assertNotNull(hit);
        assertNotNull(hit.topLeft());
        assertNotNull(hit.topRight());
        assertNotNull(hit.bottomRight());
        assertNotNull(hit.bottomLeft());

        System.out.println("TopLeft: " + hit.topLeft());
        System.out.println("TopRight: " + hit.topRight());
        System.out.println("BottomRight: " + hit.bottomRight());
        System.out.println("BottomLeft: " + hit.bottomLeft());
    }

    @Test
    void testOptionsValidation() {
        assertThrows(IllegalArgumentException.class,
                () -> ReadOptions.builder().maxSymbols(0));

        assertThrows(IllegalArgumentException.class,
                () -> ReadOptions.builder().maxSymbols(256));

        assertThrows(IllegalArgumentException.class,
                () -> ReadOptions.builder().symbologies(new Symbology[0]));
    }

    @Test
    void testOldQrCodeApiCompatibility() {
        byte[] data = "OLD_QR_CODE_API".getBytes(StandardCharsets.UTF_8);

        try (QrCode qr = QrCode.encode(data)) {
            assertTrue(qr.size() >= 21);
            assertTrue(qr.version() >= 1);
            assertNotNull(qr.errorCorrection());

            BufferedImage image = qr.toImage(8);
            BarcodeHit hit = new BarcodeReader().readFirst(image);

            assertNotNull(hit);
            assertArrayEquals(data, hit.bytes());
        }
    }
}
