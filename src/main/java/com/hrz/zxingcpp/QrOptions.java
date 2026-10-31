package com.hrz.zxingcpp;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * QR Code 编码选项。
 * <p>QrOptions 为不可变对象，支持链式调用。</p>
 * <pre>
 * QrOptions options = QrOptions.defaults()
 *         .errorCorrection(QrErrorCorrection.L)
 *         .binary();
 * </pre>
 * @author HRZ
 */
public final class QrOptions {

    /**
     * 默认级别为 L
     * 默认ECI为 ECI_ISO_8859_1
     */
    private static final QrOptions DEFAULTS = new QrOptions(
            QrErrorCorrection.L,
            QrConstants.DEFAULT_ECI
    );

    private final QrErrorCorrection errorCorrection;
    private final int eci;

    private QrOptions(QrErrorCorrection errorCorrection, int eci) {
        this.errorCorrection = Objects.requireNonNull(errorCorrection, "errorCorrection");

        if (eci < 0 || eci > 999999) {
            throw new IllegalArgumentException("eci must be between 0 and 999999");
        }

        this.eci = eci;
    }

    /**
     * 返回默认 QR Code 编码选项。
     *
     * <p>默认：</p>
     * <ul>
     *     <li>错误纠正：L</li>
     *     <li>ECI：ISO-8859-1（3）</li>
     * </ul>
     */
    public static QrOptions defaults() {
        return DEFAULTS;
    }

    /**
     * 设置错误纠正等级。
     */
    public QrOptions errorCorrection(QrErrorCorrection errorCorrection) {
        return new QrOptions(errorCorrection, eci);
    }

    /**
     * 设置 ECI。
     * @param eci ECI 整数值
     */
    public QrOptions eci(int eci) {
        return new QrOptions(errorCorrection, eci);
    }

    /**
     * 使用 ISO-8859-1。
     * ECI = 3。
     */
    public QrOptions iso88591() {
        return eci(QrConstants.ECI_ISO_8859_1);
    }

    /**
     * 使用 UTF-8。
     * ECI = 26。
     */
    public QrOptions utf8() {
        return eci(QrConstants.ECI_UTF8);
    }

    /**
     * 使用 GBK。
     * ECI = 31。
     */
    public QrOptions gbk() {
        return eci(QrConstants.ECI_GBK);
    }

    /**
     * 使用 GB18030。
     * ECI = 33。
     */
    public QrOptions gb18030() {
        return eci(QrConstants.ECI_GB18030);
    }
    
    /**
     * 使用 Binary。
     * ECI = 899。
     */
    public QrOptions binary() {
        return eci(QrConstants.ECI_BINARY);
    }

    /**
     * 获取错误纠正等级。
     */
    public QrErrorCorrection errorCorrection() {
        return errorCorrection;
    }
    
    public Charset charset(){
        switch (eci){
            case QrConstants.ECI_UTF8 -> {
                return StandardCharsets.UTF_8;
            }
            case QrConstants.ECI_ISO_8859_1 -> {
                return StandardCharsets.ISO_8859_1;
            }
            default -> throw new IllegalStateException("Unexpected value: " + eci);
        }
    }

    /**
     * 获取 ECI。
     */
    public int eci() {
        return eci;
    }

    @Override
    public String toString() {
        return "QrOptions{" +
                "errorCorrection=" + errorCorrection +
                ", eci=" + eci +
                '}';
    }
}