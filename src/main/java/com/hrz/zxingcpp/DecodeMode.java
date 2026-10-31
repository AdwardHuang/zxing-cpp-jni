
package com.hrz.zxingcpp;

/**
 * 解码策略。
 * @author HRZ
 */
public enum DecodeMode {

    /**
     * 优先速度，关闭 tryHarder。
     */
    FAST,

    /**
     * 使用 ZXing-C++ 默认检测策略。
     */
    NORMAL,

    /**
     * 优先识别率，开启 tryHarder。
     */
    TRY_HARDER
}
