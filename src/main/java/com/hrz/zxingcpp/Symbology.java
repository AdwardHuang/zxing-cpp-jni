
package com.hrz.zxingcpp;

/**
 * ZXing-C++ 3.1.1 支持的条码格式。
 * nativeName 必须与 ZXing::BarcodeFormat 的名称一致。
 * @author HRZ
 */
public enum Symbology {

    ALL("AllReadable"),

    CODABAR("Codabar"),
    CODE_39("Code39"),
    CODE_39_STD("Code39Std"),
    CODE_39_EXT("Code39Ext"),
    CODE_32("Code32"),
    PZN("PZN"),
    CODE_93("Code93"),
    CODE_128("Code128"),
    ITF("ITF"),
    ITF_14("ITF14"),

    DATA_BAR("DataBar"),
    DATA_BAR_OMNI("DataBarOmni"),
    DATA_BAR_STACKED("DataBarStk"),
    DATA_BAR_STACKED_OMNI("DataBarStkOmni"),
    DATA_BAR_LIMITED("DataBarLtd"),
    DATA_BAR_EXPANDED("DataBarExp"),
    DATA_BAR_EXPANDED_STACKED("DataBarExpStk"),

    EAN_UPC("EANUPC"),
    EAN_13("EAN13"),
    EAN_8("EAN8"),
    EAN_5("EAN5"),
    EAN_2("EAN2"),
    ISBN("ISBN"),
    UPC_A("UPCA"),
    UPC_E("UPCE"),

    TELEPEN("Telepen"),
    TELEPEN_ALPHA("TelepenAlpha"),
    TELEPEN_NUMERIC("TelepenNumeric"),
    DX_FILM_EDGE("DXFilmEdge"),

    PDF_417("PDF417"),
    COMPACT_PDF_417("CompactPDF417"),
    MICRO_PDF_417("MicroPDF417"),

    AZTEC("Aztec"),
    AZTEC_CODE("AztecCode"),
    AZTEC_RUNE("AztecRune"),

    QR_CODE("QRCode"),
    QR_CODE_MODEL_1("QRCodeModel1"),
    QR_CODE_MODEL_2("QRCodeModel2"),
    MICRO_QR_CODE("MicroQRCode"),
    RM_QR_CODE("RMQRCode"),

    DATA_MATRIX("DataMatrix"),
    MAXI_CODE("MaxiCode");

    private final String nativeName;

    Symbology(String nativeName) {
        this.nativeName = nativeName;
    }

    public String nativeName() {
        return nativeName;
    }
}
