package com.hrz.zxingcpp;

/**
 * QR Code 错误纠正等级。
 * L：约 7%
 * M：约 15%
 * Q：约 25%
 * H：约 30%
 *
 * @author HRZ
 */
public enum QrErrorCorrection {

    L(QrConstants.EC_LEVEL_L),
    M(QrConstants.EC_LEVEL_M),
    Q(QrConstants.EC_LEVEL_Q),
    H(QrConstants.EC_LEVEL_H);

    private final int value;

    QrErrorCorrection(int value) {
        this.value = value;
    }

    /**
     * 获取 ZXing-C++ 使用的整数值。
     */
    public int value() {
        return value;
    }
}