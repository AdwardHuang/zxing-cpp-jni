private static void testAllBytes()
        throws Exception
{
    System.out.println(
            "========== All bytes 0x00 ~ 0xFF =========="
    );


    byte[] source =
            new byte[256];


    for (int i = 0;
         i < 256;
         i++)
    {
        source[i] =
                (byte) i;
    }


    BufferedImage image =
            ZXingCpp.encodeImage(
                    source,
                    600,
                    600
            );


    ZXingCpp.DecodeResult result =
            ZXingCpp.decodeBytes(
                    image
            );


    if (result == null) {

        throw new IllegalStateException(
                "All-bytes decode failed"
        );

    }


    boolean equals =
            Arrays.equals(
                    source,
                    result.getBytes()
            );


    System.out.println(
            "ECI      : "
                    + result.getECI()
    );


    System.out.println(
            "Bytes    : "
                    + result.getBytes().length
    );


    System.out.println(
            "Equals   : "
                    + equals
    );


    if (!equals) {

        throw new IllegalStateException(
                "0x00~0xFF mismatch"
        );

    }
}
