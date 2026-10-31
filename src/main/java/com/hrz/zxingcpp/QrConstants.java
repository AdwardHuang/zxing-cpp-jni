package com.hrz.zxingcpp;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Qr使用到的常量
 * @author HRZ
 */
public class QrConstants {
    // ========================================================
    // ECI constants
    // ========================================================
    /**
     * ISO-8859-1。
     * 对 byte[0..255] 可以做到一一映射。
     * 适合 QrFT / 二进制数据兼容模式。
     */
    public static final int ECI_ISO_8859_1 = 3;
    /**
     * 默认 ECI。
     * 对 QOcr 来说推荐使用 3。
     */
    public static final int DEFAULT_ECI = ECI_ISO_8859_1;
    /**
     * UTF-8。
     */
    public static final int ECI_UTF8 = 26;/**
     * GBK。
     */
    public static final int ECI_GBK = 31;

    /**
     * GB18030。
     */
    public static final int ECI_GB18030 = 32;
    
    /**
     * 8-bit binary。
     */
    public static final int ECI_BINARY = 899;
    /**
     * 默认 QR Error Correction。
     * 1 = L
     * 2 = M
     * 3 = Q
     * 4 = H
     */
    public static final int EC_LEVEL_L = 1;
    public static final int DEFAULT_EC_LEVEL = EC_LEVEL_L;
    public static final int EC_LEVEL_M = 2;
    public static final int EC_LEVEL_Q = 3;
    public static final int EC_LEVEL_H = 4;
    /**
     * 默认 quiet zone。
     */
    public static final int DEFAULT_QUIET_ZONE = 4;
     
    public static int toEci(Charset charset){
        int eci = ECI_BINARY;
        if (StandardCharsets.ISO_8859_1 == charset){
            eci = ECI_ISO_8859_1;
        } else if (StandardCharsets.UTF_8 == charset){
            eci = ECI_UTF8;
        }
        return eci;
    }
}
