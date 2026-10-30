#include <jni.h>

#include <algorithm>
#include <cstdint>
#include <cstring>
#include <exception>
#include <stdexcept>
#include <string>
#include <vector>
#include <limits>

#include "ZXingCpp.h"


namespace
{

// ============================================================
// Java exception helpers
// ============================================================

void ThrowException(
        JNIEnv* env,
        const char* className,
        const std::string& message)
{
    jclass clazz =
            env->FindClass(className);

    if (clazz != nullptr) {

        env->ThrowNew(
                clazz,
                message.c_str()
        );

    }
}


void ThrowIllegalArgument(
        JNIEnv* env,
        const std::string& message)
{
    ThrowException(
            env,
            "java/lang/IllegalArgumentException",
            message
    );
}


void ThrowIllegalState(
        JNIEnv* env,
        const std::string& message)
{
    ThrowException(
            env,
            "java/lang/IllegalStateException",
            message
    );
}


// ============================================================
// Java DecodeResult helper
// ============================================================

jobject CreateDecodeResult(
        JNIEnv* env,
        const std::vector<uint8_t>& bytes,
        bool hasECI,
        int eci,
        const std::string& charset,
        const std::string& text)
{
    jclass clazz =
            env->FindClass(
                    "com/hrz/zxingcpp/ZXingCpp$DecodeResult"
            );

    if (clazz == nullptr) {
        return nullptr;
    }


    jmethodID constructor =
            env->GetMethodID(
                    clazz,
                    "<init>",
                    "([BZILjava/lang/String;Ljava/lang/String;)V"
            );

    if (constructor == nullptr) {
        return nullptr;
    }


    jbyteArray byteArray =
            env->NewByteArray(
                    static_cast<jsize>(
                            bytes.size()
                    )
            );

    if (byteArray == nullptr) {
        return nullptr;
    }


    if (!bytes.empty()) {

        env->SetByteArrayRegion(
                byteArray,
                0,
                static_cast<jsize>(
                        bytes.size()
                ),
                reinterpret_cast<const jbyte*>(
                        bytes.data()
                )
        );

    }


    jstring charsetString = nullptr;


    if (!charset.empty()) {

        charsetString =
                env->NewStringUTF(
                        charset.c_str()
                );

    }


    jstring textString = nullptr;


    if (!text.empty()) {

        textString =
                env->NewStringUTF(
                        text.c_str()
                );

    }


    jobject result =
            env->NewObject(
                    clazz,
                    constructor,
                    byteArray,
                    hasECI
                            ? JNI_TRUE
                            : JNI_FALSE,
                    static_cast<jint>(
                            eci
                    ),
                    charsetString,
                    textString
            );


    env->DeleteLocalRef(
            byteArray
    );


    if (charsetString != nullptr) {

        env->DeleteLocalRef(
                charsetString
        );

    }


    if (textString != nullptr) {

        env->DeleteLocalRef(
                textString
        );

    }


    return result;
}


// ============================================================
// ECI parsing
//
// ZXing-C++ bytesECI() 返回带 ECI 标记的内容。
// 当前主要处理普通的单 ECI QR。
// ============================================================

int ExtractFirstECI(
        const std::vector<uint8_t>& bytesECI)
{
    if (bytesECI.empty()) {
        return -1;
    }


    /*
     * ZXing-C++ 的 bytesECI() 使用：
     *
     *     '\' + 6 位 ECI 数字
     *
     * 作为 ECI 标记。
     *
     * 例如：
     *
     *     \000003
     *     \000026
     *     \000899
     */

    for (
            size_t i = 0;
            i + 7 <= bytesECI.size();
            ++i)
    {

        if (bytesECI[i] != '\\') {
            continue;
        }


        bool allDigits = true;

        int value = 0;


        for (
                size_t j = 1;
                j <= 6;
                ++j)
        {

            uint8_t c =
                    bytesECI[
                            i + j
                    ];


            if (c < '0' ||
                c > '9')
            {

                allDigits = false;

                break;
            }


            value =
                    value * 10 +
                    static_cast<int>(
                            c - '0'
                    );
        }


        if (allDigits) {
            return value;
        }

    }


    return -1;
}


// ============================================================
// ECI -> charset name
// ============================================================

std::string ECIName(
        int eci)
{
    switch (eci) {

        case 3:
            return "ISO-8859-1";

        case 20:
            return "Shift_JIS";

        case 26:
            return "UTF-8";

        case 28:
            return "Big5";

        case 29:
            return "ISO-8859-1";

        case 30:
            return "EUC-KR";

        case 37:
            return "ISO-8859-1";

        case 899:
            return "8-bit binary";

        default:
            return "";
    }
}


// ============================================================
// Render ZXing-C++ symbol to fixed-size grayscale image
//
// 输出：
//
//     0   = black
//     255 = white
//
// 采用整数 moduleScale，避免二维码模块被缩放成非整数像素。
// ============================================================

std::vector<uint8_t> RenderQRCode(
        const ZXing::ImageView& symbol,
        int width,
        int height,
        int quietZone)
{
    /*
     * 注意：
     *
     * ZXing-C++ v3.1.1 的 ImageView
     * 没有 empty() 方法。
     *
     * 因此使用 data / width / height
     * 判断 ImageView 是否有效。
     */

    if (symbol.data() == nullptr ||
        symbol.width() <= 0 ||
        symbol.height() <= 0)
    {

        throw std::runtime_error(
                "ZXing symbol is empty"
        );

    }


    const int symbolWidth =
            symbol.width();


    const int symbolHeight =
            symbol.height();


    if (symbolWidth <= 0 ||
        symbolHeight <= 0)
    {

        throw std::runtime_error(
                "Invalid ZXing symbol size"
        );

    }


    /*
     * 实际二维码模块数。
     *
     * symbol() 是二维码矩阵本身。
     */

    const int moduleWidth =
            symbolWidth +
            quietZone * 2;


    const int moduleHeight =
            symbolHeight +
            quietZone * 2;


    if (moduleWidth <= 0 ||
        moduleHeight <= 0)
    {

        throw std::runtime_error(
                "Invalid quiet zone"
        );

    }


    /*
     * 使用相同的整数 scale。
     *
     * 这样 QR module 永远不会被半个像素切割。
     */

    const int scaleX =
            width / moduleWidth;


    const int scaleY =
            height / moduleHeight;


    const int moduleScale =
            std::min(
                    scaleX,
                    scaleY
            );


    if (moduleScale <= 0) {

        throw std::runtime_error(
                "Target image is too small for QR code"
        );

    }


    const int renderedWidth =
            moduleWidth *
            moduleScale;


    const int renderedHeight =
            moduleHeight *
            moduleScale;


    /*
     * 居中。
     */

    const int offsetX =
            (width - renderedWidth) / 2;


    const int offsetY =
            (height - renderedHeight) / 2;


    std::vector<uint8_t> output(
            static_cast<size_t>(width) *
            static_cast<size_t>(height),
            255
    );


    /*
     * symbol() 通常是 Lum 格式：
     *
     *     0   = black
     *     255 = white
     *
     * 对于安全性，这里不直接假定具体值，
     * 只把小于 128 的值当成黑色。
     */

    const uint8_t* source =
            symbol.data();


    const int sourceRowStride =
            symbol.rowStride();


    const int sourcePixStride =
            symbol.pixStride();


    for (
            int y = 0;
            y < symbolHeight;
            ++y)
    {

        for (
                int x = 0;
                x < symbolWidth;
                ++x)
        {

            const uint8_t value =
                    source[
                            y *
                            sourceRowStride
                            +
                            x *
                            sourcePixStride
                    ];


            /*
             * QR symbol 中黑色 module。
             */

            const bool black =
                    value < 128;


            if (!black) {
                continue;
            }


            const int dstX =
                    offsetX +
                    (quietZone + x) *
                    moduleScale;


            const int dstY =
                    offsetY +
                    (quietZone + y) *
                    moduleScale;


            for (
                    int yy = 0;
                    yy < moduleScale;
                    ++yy)
            {

                uint8_t* row =
                        output.data() +
                        static_cast<size_t>(
                                dstY + yy
                        ) *
                        static_cast<size_t>(
                                width
                        );


                std::fill(
                        row + dstX,
                        row +
                        dstX +
                        moduleScale,
                        0
                );

            }

        }

    }


    return output;
}


// ============================================================
// byte[] -> Java byte[]
// ============================================================

jbyteArray VectorToByteArray(
        JNIEnv* env,
        const std::vector<uint8_t>& data)
{
    jbyteArray result =
            env->NewByteArray(
                    static_cast<jsize>(
                            data.size()
                    )
            );


    if (result == nullptr) {
        return nullptr;
    }


    if (!data.empty()) {

        env->SetByteArrayRegion(
                result,
                0,
                static_cast<jsize>(
                        data.size()
                ),
                reinterpret_cast<const jbyte*>(
                        data.data()
                )
        );

    }


    return result;
}

}


// ============================================================
// Decode
// ============================================================

extern "C"
JNIEXPORT jobject JNICALL
Java_com_hrz_zxingcpp_ZXingCpp_nativeDecodeBytes(
        JNIEnv* env,
        jclass,
        jbyteArray rgbaArray,
        jint width,
        jint height,
        jint rowStride,
        jboolean tryRotate,
        jboolean tryInvert)
{
    if (rgbaArray == nullptr) {

        ThrowIllegalArgument(
                env,
                "rgba == null"
        );

        return nullptr;
    }


    if (width <= 0 ||
        height <= 0)
    {

        ThrowIllegalArgument(
                env,
                "width/height must be > 0"
        );

        return nullptr;
    }


    const jsize length =
            env->GetArrayLength(
                    rgbaArray
            );


    const int expectedStride =
            width * 4;


    if (rowStride <= 0) {
        rowStride = expectedStride;
    }


    const int requiredSize =
            (height - 1) *
            rowStride +
            width * 4;


    if (length < requiredSize) {

        ThrowIllegalArgument(
                env,
                "RGBA buffer is smaller than image"
        );

        return nullptr;
    }


    jbyte* data =
            env->GetByteArrayElements(
                    rgbaArray,
                    nullptr
            );


    if (data == nullptr) {
        return nullptr;
    }


    try {

        ZXing::ImageView image(
                reinterpret_cast<const uint8_t*>(
                        data
                ),
                length,
                width,
                height,
                ZXing::ImageFormat::RGBA,
                rowStride,
                4
        );


        auto options =
                ZXing::ReaderOptions()
                    .formats(
                            ZXing::BarcodeFormat::QRCode
                    )
                    .tryRotate(
                            tryRotate == JNI_TRUE
                    )
                    .tryInvert(
                            tryInvert == JNI_TRUE
                    )
                    .tryDownscale(true)
                    .maxNumberOfSymbols(1)
                    .returnErrors(false);


        auto barcodes =
                ZXing::ReadBarcodes(
                        image,
                        options
                );


        env->ReleaseByteArrayElements(
                rgbaArray,
                data,
                JNI_ABORT
        );

        data = nullptr;


        if (barcodes.empty()) {
            return nullptr;
        }


        const auto& barcode =
                barcodes.front();


        const auto& bytes =
                barcode.bytes();


        const bool hasECI =
                barcode.hasECI();


        const auto bytesECI =
                barcode.bytesECI();


        const int eci =
                ExtractFirstECI(
                        bytesECI
                );


        const std::string charset =
                eci >= 0
                ? ECIName(eci)
                : "";


        const std::string text =
                barcode.text();


        return CreateDecodeResult(
                env,
                bytes,
                hasECI,
                eci,
                charset,
                text
        );

    }
    catch (const std::exception& e) {

        if (data != nullptr) {

            env->ReleaseByteArrayElements(
                    rgbaArray,
                    data,
                    JNI_ABORT
            );

        }


        ThrowIllegalState(
                env,
                e.what()
        );


        return nullptr;

    }
    catch (...) {

        if (data != nullptr) {

            env->ReleaseByteArrayElements(
                    rgbaArray,
                    data,
                    JNI_ABORT
            );

        }


        ThrowIllegalState(
                env,
                "Unknown native exception"
        );


        return nullptr;
    }
}


// ============================================================
// Encode byte[]
//
// 参数：
//
//     data
//     width
//     height
//     quietZone
//     ecLevel
//     eci
//
// ecLevel:
//
//     1 = L
//     2 = M
//     3 = Q
//     4 = H
//
// eci:
//
//     3   = ISO-8859-1
//     26  = UTF-8
//     899 = 8-bit binary
// ============================================================

extern "C"
JNIEXPORT jbyteArray JNICALL
Java_com_hrz_zxingcpp_ZXingCpp_nativeEncodeBytes(
        JNIEnv* env,
        jclass,
        jbyteArray dataArray,
        jint width,
        jint height,
        jint quietZone,
        jint ecLevel,
        jint eci)
{
    if (dataArray == nullptr) {

        ThrowIllegalArgument(
                env,
                "data == null"
        );

        return nullptr;
    }


    if (width <= 0 ||
        height <= 0)
    {

        ThrowIllegalArgument(
                env,
                "width/height must be > 0"
        );

        return nullptr;
    }


    if (quietZone < 0) {

        ThrowIllegalArgument(
                env,
                "quietZone must be >= 0"
        );

        return nullptr;
    }


    if (ecLevel < 1 ||
        ecLevel > 4)
    {

        ThrowIllegalArgument(
                env,
                "ecLevel must be 1(L), 2(M), 3(Q), or 4(H)"
        );

        return nullptr;
    }


    if (eci < 0 ||
        eci > 999999)
    {

        ThrowIllegalArgument(
                env,
                "ECI must be between 0 and 999999"
        );

        return nullptr;
    }


    const jsize dataLength =
            env->GetArrayLength(
                    dataArray
            );


    if (dataLength <= 0) {

        ThrowIllegalArgument(
                env,
                "data is empty"
        );

        return nullptr;
    }


    jbyte* data =
            env->GetByteArrayElements(
                    dataArray,
                    nullptr
            );


    if (data == nullptr) {
        return nullptr;
    }


    try {

        /*
         * ZXing-C++ 新 Writer API：
         *
         * CreatorOptions(
         *     BarcodeFormat::QRCode,
         *     "ecLevel=M,eci=3"
         * );
         */

        std::string options;


        /*
         * QRCode 的 ECC。
         */

        switch (ecLevel) {

            case 1:

                options +=
                        "ecLevel=L";

                break;


            case 2:

                options +=
                        "ecLevel=M";

                break;


            case 3:

                options +=
                        "ecLevel=Q";

                break;


            case 4:

                options +=
                        "ecLevel=H";

                break;
        }


        /*
         * ECI。
         *
         * 例如：
         *
         *     eci=3
         *     eci=26
         *     eci=899
         */

        options +=
                ",eci=";

        options +=
                std::to_string(
                        eci
                );


        ZXing::CreatorOptions creatorOptions(
                ZXing::BarcodeFormat::QRCode,
                options
        );


        /*
         * 关键：
         *
         * CreateBarcodeFromBytes()
         *
         * 不经过 Java String。
         */

        const ZXing::Barcode barcode =
                ZXing::CreateBarcodeFromBytes(
                        reinterpret_cast<const uint8_t*>(
                                data
                        ),
                        static_cast<int>(
                                dataLength
                        ),
                        creatorOptions
                );


        /*
         * Java byte[] 不再使用。
         */

        env->ReleaseByteArrayElements(
                dataArray,
                data,
                JNI_ABORT
        );

        data = nullptr;


        if (!barcode.isValid()) {

            ThrowIllegalState(
                    env,
                    barcode.error().msg()
            );

            return nullptr;
        }


        /*
         * 取得二维码 module matrix。
         */

        const ZXing::ImageView symbol =
                barcode.symbol();


        /*
         * 重要：
         *
         * ZXing-C++ v3.1.1 的 ImageView
         * 没有 empty() 方法。
         *
         * 使用 data / width / height
         * 判断是否有效。
         */

        if (symbol.data() == nullptr ||
            symbol.width() <= 0 ||
            symbol.height() <= 0)
        {

            ThrowIllegalState(
                    env,
                    "ZXing-C++ returned empty QR symbol"
            );

            return nullptr;
        }


        /*
         * 渲染为固定 width x height
         * 的 8-bit grayscale byte[]。
         */

        const std::vector<uint8_t> image =
                RenderQRCode(
                        symbol,
                        width,
                        height,
                        quietZone
                );


        return VectorToByteArray(
                env,
                image
        );

    }
    catch (const std::exception& e) {

        if (data != nullptr) {

            env->ReleaseByteArrayElements(
                    dataArray,
                    data,
                    JNI_ABORT
            );

        }


        ThrowIllegalState(
                env,
                e.what()
        );


        return nullptr;

    }
    catch (...) {

        if (data != nullptr) {

            env->ReleaseByteArrayElements(
                    dataArray,
                    data,
                    JNI_ABORT
            );

        }


        ThrowIllegalState(
                env,
                "Unknown native exception"
        );


        return nullptr;
    }
}


// ============================================================
// ZXing-C++ version
// ============================================================

extern "C"
JNIEXPORT jstring JNICALL
Java_com_hrz_zxingcpp_ZXingCpp_nativeVersion(
        JNIEnv* env,
        jclass)
{
    return env->NewStringUTF(
            ZXing::Version().c_str()
    );
}

// ============================================================
// QrCode native handle API
// ============================================================

namespace
{

struct QrCodeHandle
{
    ZXing::Barcode barcode;
};

QrCodeHandle* GetQrCodeHandle(
        JNIEnv* env,
        jlong handle)
{
    if (handle == 0) {
        ThrowIllegalState(env, "QrCode has already been closed");
        return nullptr;
    }

    return reinterpret_cast<QrCodeHandle*>(handle);
}

int ParseQrMetadata(
        const ZXing::Barcode& barcode,
        const char* key)
{
    const std::string value = barcode.extra(key);

    if (value.empty()) {
        return -1;
    }

    try {
        return std::stoi(value);
    }
    catch (...) {
        return -1;
    }
}

} // namespace


// ============================================================
// Create QR Code
// ============================================================

extern "C"
JNIEXPORT jlong JNICALL
Java_com_hrz_zxingcpp_QrCode_nativeCreateQrCode(
        JNIEnv* env,
        jclass,
        jbyteArray dataArray,
        jint ecLevel,
        jint eci)
{
    if (dataArray == nullptr) {
        ThrowIllegalArgument(env, "data == null");
        return 0;
    }

    if (ecLevel < 1 || ecLevel > 4) {
        ThrowIllegalArgument(
                env,
                "ecLevel must be 1(L), 2(M), 3(Q), or 4(H)"
        );
        return 0;
    }

    if (eci < 0 || eci > 999999) {
        ThrowIllegalArgument(
                env,
                "ECI must be between 0 and 999999"
        );
        return 0;
    }

    const jsize dataLength = env->GetArrayLength(dataArray);

    if (dataLength <= 0) {
        ThrowIllegalArgument(env, "data is empty");
        return 0;
    }

    jbyte* data = env->GetByteArrayElements(dataArray, nullptr);

    if (data == nullptr) {
        return 0;
    }

    try {
        std::string options;

        switch (ecLevel) {
            case 1:
                options = "ecLevel=L";
                break;

            case 2:
                options = "ecLevel=M";
                break;

            case 3:
                options = "ecLevel=Q";
                break;

            case 4:
                options = "ecLevel=H";
                break;

            default:
                ThrowIllegalArgument(env, "Invalid EC level");
                env->ReleaseByteArrayElements(
                        dataArray,
                        data,
                        JNI_ABORT
                );
                return 0;
        }

        options += ",eci=";
        options += std::to_string(eci);

        ZXing::CreatorOptions creatorOptions(
                ZXing::BarcodeFormat::QRCode,
                options
        );

        ZXing::Barcode barcode =
                ZXing::CreateBarcodeFromBytes(
                        reinterpret_cast<const uint8_t*>(data),
                        static_cast<int>(dataLength),
                        creatorOptions
                );

        env->ReleaseByteArrayElements(
                dataArray,
                data,
                JNI_ABORT
        );

        data = nullptr;

        if (!barcode.isValid()) {
            ThrowIllegalState(
                    env,
                    barcode.error().msg()
            );
            return 0;
        }

        const ZXing::ImageView symbol = barcode.symbol();

        if (symbol.data() == nullptr ||
                symbol.width() <= 0 ||
                symbol.height() <= 0) {
            ThrowIllegalState(
                    env,
                    "ZXing-C++ returned an invalid QR symbol"
            );
            return 0;
        }

        QrCodeHandle* handle = new QrCodeHandle{
                std::move(barcode)
        };

        return reinterpret_cast<jlong>(handle);
    }
    catch (const std::exception& e) {
        if (data != nullptr) {
            env->ReleaseByteArrayElements(
                    dataArray,
                    data,
                    JNI_ABORT
            );
        }

        ThrowIllegalState(env, e.what());
        return 0;
    }
    catch (...) {
        if (data != nullptr) {
            env->ReleaseByteArrayElements(
                    dataArray,
                    data,
                    JNI_ABORT
            );
        }

        ThrowIllegalState(env, "Unknown native exception");
        return 0;
    }
}


// ============================================================
// Destroy QR Code
// ============================================================

extern "C"
JNIEXPORT void JNICALL
Java_com_hrz_zxingcpp_QrCode_nativeDestroyQrCode(
        JNIEnv*,
        jclass,
        jlong handle)
{
    if (handle == 0) {
        return;
    }

    delete reinterpret_cast<QrCodeHandle*>(handle);
}


// ============================================================
// QR module count
// ============================================================

extern "C"
JNIEXPORT jint JNICALL
Java_com_hrz_zxingcpp_QrCode_nativeQrCodeSize(
        JNIEnv* env,
        jclass,
        jlong handle)
{
    QrCodeHandle* qr = GetQrCodeHandle(env, handle);

    if (qr == nullptr) {
        return 0;
    }

    const ZXing::ImageView symbol = qr->barcode.symbol();

    return symbol.width();
}


// ============================================================
// QR version
// ============================================================
extern "C"
JNIEXPORT jint JNICALL
Java_com_hrz_zxingcpp_QrCode_nativeQrCodeVersion(
        JNIEnv* env,
        jclass,
        jlong handle)
{
    QrCodeHandle* qr = GetQrCodeHandle(env, handle);

    if (qr == nullptr) {
        return -1;
    }

    const ZXing::ImageView symbol = qr->barcode.symbol();

    if (symbol.data() == nullptr ||
        symbol.width() != symbol.height()) {
        return -1;
    }

    const int moduleCount = symbol.width();

    /*
     * QR Code Model 2:     *
     * moduleCount = 17 + 4 * version     *
     * version = (moduleCount - 17) / 4
     */
    if (moduleCount < 21 ||
        moduleCount > 177 ||
        (moduleCount - 17) % 4 != 0) {
        return -1;
    }

    return (moduleCount - 17) / 4;
}


// ============================================================
// QR error correction
// ============================================================

extern "C"
JNIEXPORT jstring JNICALL
Java_com_hrz_zxingcpp_QrCode_nativeQrCodeErrorCorrection(
        JNIEnv* env,
        jclass,
        jlong handle)
{
    QrCodeHandle* qr = GetQrCodeHandle(env, handle);

    if (qr == nullptr) {
        return nullptr;
    }

    const std::string value = qr->barcode.extra(
            ZXing::BarcodeExtra::ECLevel
    );

    return env->NewStringUTF(value.c_str());
}


// ============================================================
// QR mask
// ============================================================

extern "C"
JNIEXPORT jint JNICALL
Java_com_hrz_zxingcpp_QrCode_nativeQrCodeMask(
        JNIEnv* env,
        jclass,
        jlong handle)
{
    QrCodeHandle* qr = GetQrCodeHandle(env, handle);

    if (qr == nullptr) {
        return -1;
    }

    return ParseQrMetadata(
            qr->barcode,
            ZXing::BarcodeExtra::DataMask
    );
}


// ============================================================
// Query module color
// ============================================================

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_hrz_zxingcpp_QrCode_nativeQrCodeIsDark(
        JNIEnv* env,
        jclass,
        jlong handle,
        jint x,
        jint y)
{
    QrCodeHandle* qr = GetQrCodeHandle(env, handle);

    if (qr == nullptr) {
        return JNI_FALSE;
    }

    const ZXing::ImageView symbol = qr->barcode.symbol();

    if (x < 0 || x >= symbol.width() ||
            y < 0 || y >= symbol.height()) {
        ThrowIllegalArgument(env, "QR module coordinates out of bounds");
        return JNI_FALSE;
    }

    const uint8_t value = symbol.data()[
            y * symbol.rowStride() +
            x * symbol.pixStride()
    ];

    return value < 128 ? JNI_TRUE : JNI_FALSE;
}


// ============================================================
// Render QR Code
// ============================================================

extern "C"
JNIEXPORT jbyteArray JNICALL
Java_com_hrz_zxingcpp_QrCode_nativeQrCodeRender(
        JNIEnv* env,
        jclass,
        jlong handle,
        jint moduleSize,
        jint quietZone)
{
    QrCodeHandle* qr = GetQrCodeHandle(env, handle);

    if (qr == nullptr) {
        return nullptr;
    }

    if (moduleSize <= 0) {
        ThrowIllegalArgument(env, "moduleSize must be > 0");
        return nullptr;
    }

    if (quietZone < 0) {
        ThrowIllegalArgument(env, "quietZone must be >= 0");
        return nullptr;
    }

    try {
        const ZXing::ImageView symbol = qr->barcode.symbol();

        const int moduleCount = symbol.width();

        const int64_t side64 =
                (static_cast<int64_t>(moduleCount) +
                 static_cast<int64_t>(quietZone) * 2) *
                static_cast<int64_t>(moduleSize);

        if (side64 <= 0 || side64 > 46340) {
            ThrowIllegalArgument(
                    env,
                    "Rendered QR image dimensions are too large"
            );
            return nullptr;
        }

        const int side = static_cast<int>(side64);

        const std::vector<uint8_t> image = RenderQRCode(
                symbol,
                side,
                side,
                quietZone
        );

        return VectorToByteArray(env, image);
    }
    catch (const std::exception& e) {
        ThrowIllegalState(env, e.what());
        return nullptr;
    }
    catch (...) {
        ThrowIllegalState(env, "Unknown native exception");
        return nullptr;
    }
}


// ============================================================
// Multi-format BarcodeReader JNI API
//
// Java:
//   com.hrz.zxingcpp.BarcodeReader.nativeRead()
//
// ZXing-C++:
//   v3.1.1
//
// 注意：本段为追加代码，不替换原有 JNI 实现。
// ============================================================

namespace
{

jstring NewJavaString(JNIEnv* env, const std::string& value)
{
    return env->NewStringUTF(value.c_str());
}

jobject CreateImagePoint(JNIEnv* env, jclass pointClass, jmethodID constructor, int x, int y)
{
    return env->NewObject(pointClass, constructor, static_cast<jint>(x), static_cast<jint>(y));
}

jobject CreateBarcodeHit(
        JNIEnv* env,
        jclass hitClass,
        jmethodID hitConstructor,
        jclass pointClass,
        jmethodID pointConstructor,
        const ZXing::Barcode& barcode)
{
    const auto& bytes = barcode.bytes();

    if (bytes.size() > static_cast<size_t>(std::numeric_limits<jsize>::max())) {
        ThrowIllegalState(env, "Barcode byte array is too large");
        return nullptr;
    }

    jbyteArray rawBytes = env->NewByteArray(static_cast<jsize>(bytes.size()));
    if (rawBytes == nullptr) {
        return nullptr;
    }

    if (!bytes.empty()) {
        env->SetByteArrayRegion(rawBytes, 0, static_cast<jsize>(bytes.size()), reinterpret_cast<const jbyte*>(bytes.data()));
        if (env->ExceptionCheck()) {
            env->DeleteLocalRef(rawBytes);
            return nullptr;
        }
    }

    jstring text = NewJavaString(env, barcode.text());
    jstring format = NewJavaString(env, ZXing::ToString(barcode.format()));
    jstring symbology = NewJavaString(env, ZXing::ToString(barcode.symbology()));

    if (text == nullptr || format == nullptr || symbology == nullptr) {
        env->DeleteLocalRef(rawBytes);
        if (text != nullptr) {
            env->DeleteLocalRef(text);
        }
        if (format != nullptr) {
            env->DeleteLocalRef(format);
        }
        if (symbology != nullptr) {
            env->DeleteLocalRef(symbology);
        }
        return nullptr;
    }

    jobjectArray position = env->NewObjectArray(4, pointClass, nullptr);
    if (position == nullptr) {
        env->DeleteLocalRef(rawBytes);
        env->DeleteLocalRef(text);
        env->DeleteLocalRef(format);
        env->DeleteLocalRef(symbology);
        return nullptr;
    }

    const auto& points = barcode.position();

    for (jsize i = 0; i < 4; ++i) {
        jobject point = CreateImagePoint(
                env,
                pointClass,
                pointConstructor,
                points[static_cast<size_t>(i)].x,
                points[static_cast<size_t>(i)].y
        );

        if (point == nullptr) {
            env->DeleteLocalRef(rawBytes);
            env->DeleteLocalRef(text);
            env->DeleteLocalRef(format);
            env->DeleteLocalRef(symbology);
            env->DeleteLocalRef(position);
            return nullptr;
        }

        env->SetObjectArrayElement(position, i, point);
        env->DeleteLocalRef(point);

        if (env->ExceptionCheck()) {
            env->DeleteLocalRef(rawBytes);
            env->DeleteLocalRef(text);
            env->DeleteLocalRef(format);
            env->DeleteLocalRef(symbology);
            env->DeleteLocalRef(position);
            return nullptr;
        }
    }

    jstring sequenceId = NewJavaString(env, barcode.sequenceId());
    jstring symbologyIdentifier = NewJavaString(env, barcode.symbologyIdentifier());

    const std::string errorMessage = barcode.error().msg();
    jstring error = NewJavaString(env, errorMessage);

    if (sequenceId == nullptr || symbologyIdentifier == nullptr || error == nullptr) {
        env->DeleteLocalRef(rawBytes);
        env->DeleteLocalRef(text);
        env->DeleteLocalRef(format);
        env->DeleteLocalRef(symbology);
        env->DeleteLocalRef(position);

        if (sequenceId != nullptr) {
            env->DeleteLocalRef(sequenceId);
        }
        if (symbologyIdentifier != nullptr) {
            env->DeleteLocalRef(symbologyIdentifier);
        }
        if (error != nullptr) {
            env->DeleteLocalRef(error);
        }

        return nullptr;
    }

    jobject result = env->NewObject(
            hitClass,
            hitConstructor,
            rawBytes,
            text,
            format,
            symbology,
            position,
            static_cast<jint>(barcode.orientation()),
            barcode.isMirrored() ? JNI_TRUE : JNI_FALSE,
            barcode.isInverted() ? JNI_TRUE : JNI_FALSE,
            barcode.hasECI() ? JNI_TRUE : JNI_FALSE,
            static_cast<jint>(barcode.sequenceSize()),
            static_cast<jint>(barcode.sequenceIndex()),
            sequenceId,
            symbologyIdentifier,
            error
    );

    env->DeleteLocalRef(rawBytes);
    env->DeleteLocalRef(text);
    env->DeleteLocalRef(format);
    env->DeleteLocalRef(symbology);
    env->DeleteLocalRef(position);
    env->DeleteLocalRef(sequenceId);
    env->DeleteLocalRef(symbologyIdentifier);
    env->DeleteLocalRef(error);

    return result;
}

} // namespace


extern "C"
JNIEXPORT jobjectArray JNICALL
Java_com_hrz_zxingcpp_BarcodeReader_nativeRead(
        JNIEnv* env,
        jclass,
        jbyteArray rgbaArray,
        jint width,
        jint height,
        jint rowStride,
        jobjectArray formatsArray,
        jboolean tryHarder,
        jboolean tryRotate,
        jboolean tryInvert,
        jboolean tryDownscale,
        jint maxSymbols,
        jboolean returnErrors)
{
    if (rgbaArray == nullptr) {
        ThrowIllegalArgument(env, "rgba == null");
        return nullptr;
    }

    if (formatsArray == nullptr) {
        ThrowIllegalArgument(env, "formats == null");
        return nullptr;
    }

    if (width <= 0 || height <= 0) {
        ThrowIllegalArgument(env, "width/height must be > 0");
        return nullptr;
    }

    if (rowStride < width * 4) {
        ThrowIllegalArgument(env, "rowStride must be >= width * 4");
        return nullptr;
    }

    if (maxSymbols < 1 || maxSymbols > 255) {
        ThrowIllegalArgument(env, "maxSymbols must be between 1 and 255");
        return nullptr;
    }

    const jsize rgbaLength = env->GetArrayLength(rgbaArray);
    const int64_t requiredSize =
            static_cast<int64_t>(rowStride) * static_cast<int64_t>(height - 1) +
            static_cast<int64_t>(width) * 4;

    if (requiredSize > rgbaLength) {
        ThrowIllegalArgument(env, "RGBA buffer is smaller than image");
        return nullptr;
    }

    const jsize formatCount = env->GetArrayLength(formatsArray);
    if (formatCount <= 0) {
        ThrowIllegalArgument(env, "formats must not be empty");
        return nullptr;
    }

    std::string formatNames;

    for (jsize i = 0; i < formatCount; ++i) {
        auto formatString = static_cast<jstring>(env->GetObjectArrayElement(formatsArray, i));

        if (formatString == nullptr) {
            ThrowIllegalArgument(env, "format name must not be null");
            return nullptr;
        }

        const char* chars = env->GetStringUTFChars(formatString, nullptr);
        if (chars == nullptr) {
            env->DeleteLocalRef(formatString);
            return nullptr;
        }

        if (!formatNames.empty()) {
            formatNames += '|';
        }

        formatNames += chars;

        env->ReleaseStringUTFChars(formatString, chars);
        env->DeleteLocalRef(formatString);

        if (env->ExceptionCheck()) {
            return nullptr;
        }
    }

    jbyte* rgbaData = env->GetByteArrayElements(rgbaArray, nullptr);
    if (rgbaData == nullptr) {
        return nullptr;
    }

    try {
        const ZXing::BarcodeFormats barcodeFormats = ZXing::BarcodeFormatsFromString(formatNames);

        ZXing::ImageView image(
                reinterpret_cast<const uint8_t*>(rgbaData),
                rgbaLength,
                width,
                height,
                ZXing::ImageFormat::RGBA,
                rowStride,
                4
        );

        auto options = ZXing::ReaderOptions()
                .formats(barcodeFormats)
                .tryHarder(tryHarder == JNI_TRUE)
                .tryRotate(tryRotate == JNI_TRUE)
                .tryInvert(tryInvert == JNI_TRUE)
                .tryDownscale(tryDownscale == JNI_TRUE)
                .maxNumberOfSymbols(static_cast<uint8_t>(maxSymbols))
                .returnErrors(returnErrors == JNI_TRUE);

        const ZXing::Barcodes barcodes = ZXing::ReadBarcodes(image, options);

        env->ReleaseByteArrayElements(rgbaArray, rgbaData, JNI_ABORT);
        rgbaData = nullptr;

        jclass hitClass = env->FindClass("com/hrz/zxingcpp/BarcodeHit");
        if (hitClass == nullptr) {
            return nullptr;
        }

        jmethodID hitConstructor = env->GetMethodID(
                hitClass,
                "<init>",
                "([BLjava/lang/String;Ljava/lang/String;Ljava/lang/String;[Lcom/hrz/zxingcpp/ImagePoint;IZZZIILjava/lang/String;Ljava/lang/String;Ljava/lang/String;)V"
        );

        if (hitConstructor == nullptr) {
            env->DeleteLocalRef(hitClass);
            return nullptr;
        }

        jclass pointClass = env->FindClass("com/hrz/zxingcpp/ImagePoint");
        if (pointClass == nullptr) {
            env->DeleteLocalRef(hitClass);
            return nullptr;
        }

        jmethodID pointConstructor = env->GetMethodID(pointClass, "<init>", "(II)V");
        if (pointConstructor == nullptr) {
            env->DeleteLocalRef(hitClass);
            env->DeleteLocalRef(pointClass);
            return nullptr;
        }

        jobjectArray result = env->NewObjectArray(
                static_cast<jsize>(barcodes.size()),
                hitClass,
                nullptr
        );

        if (result == nullptr) {
            env->DeleteLocalRef(hitClass);
            env->DeleteLocalRef(pointClass);
            return nullptr;
        }

        for (jsize i = 0; i < static_cast<jsize>(barcodes.size()); ++i) {
            jobject hit = CreateBarcodeHit(
                    env,
                    hitClass,
                    hitConstructor,
                    pointClass,
                    pointConstructor,
                    barcodes[static_cast<size_t>(i)]
            );

            if (hit == nullptr) {
                env->DeleteLocalRef(result);
                env->DeleteLocalRef(hitClass);
                env->DeleteLocalRef(pointClass);
                return nullptr;
            }

            env->SetObjectArrayElement(result, i, hit);
            env->DeleteLocalRef(hit);

            if (env->ExceptionCheck()) {
                env->DeleteLocalRef(result);
                env->DeleteLocalRef(hitClass);
                env->DeleteLocalRef(pointClass);
                return nullptr;
            }
        }

        env->DeleteLocalRef(hitClass);
        env->DeleteLocalRef(pointClass);

        return result;
    }
    catch (const std::exception& e) {
        if (rgbaData != nullptr) {
            env->ReleaseByteArrayElements(rgbaArray, rgbaData, JNI_ABORT);
        }

        ThrowIllegalState(env, e.what());
        return nullptr;
    }
    catch (...) {
        if (rgbaData != nullptr) {
            env->ReleaseByteArrayElements(rgbaArray, rgbaData, JNI_ABORT);
        }

        ThrowIllegalState(env, "Unknown native exception");
        return nullptr;
    }
}
