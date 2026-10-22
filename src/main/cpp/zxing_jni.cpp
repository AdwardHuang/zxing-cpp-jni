#include <jni.h>

#include <algorithm>
#include <cstdint>
#include <cstring>
#include <string>
#include <vector>

#include "ZXingCpp.h"
#include "CharacterSet.h"
#include "CreateBarcode.h"
#include "ECI.h"


namespace {


/**
 * 向 Java 抛出异常。
 */
void ThrowJavaException(
        JNIEnv* env,
        const char* className,
        const std::string& message)
{
    jclass cls = env->FindClass(className);

    if (cls != nullptr) {
        env->ThrowNew(
                cls,
                message.c_str()
        );
    }
}


/**
 * 将 ZXing-C++ 的 CharacterSet 转换成 Java Charset 可识别的名称。
 */
std::string CharacterSetName(ZXing::ECI eci)
{
    const auto characterSet =
            ZXing::ToCharacterSet(eci);

    if (characterSet ==
        ZXing::CharacterSet::Unknown) {

        return {};
    }

    return ZXing::ToString(
            characterSet
    );
}


/**
 * 从 ZXing-C++ 的 bytesECI() 中提取第一个 ECI。
 *
 * ZXing-C++ v3.1.1 的 bytesECI() 形式例如：
 *
 *     ]Q3\000026...
 *
 * 这里寻找：
 *
 *     \ + 6 位数字
 *
 * 注意：
 *
 * 这里只提取第一个 ECI。
 */
int ExtractFirstECI(
        const std::vector<uint8_t>& bytesECI)
{
    for (size_t i = 0;
         i < bytesECI.size();
         ++i) {

        if (bytesECI[i] != '\\') {
            continue;
        }

        if (i + 6 >= bytesECI.size()) {
            continue;
        }

        bool digits = true;
        int value = 0;

        for (size_t j = 1;
             j <= 6;
             ++j) {

            const uint8_t c =
                    bytesECI[i + j];

            if (c < '0' || c > '9') {
                digits = false;
                break;
            }

            value =
                    value * 10
                    + (c - '0');
        }

        if (digits) {
            return value;
        }
    }

    return -1;
}


/**
 * 将 ZXing-C++ 生成的 symbol 渲染成指定大小的灰度图。
 *
 * 输入：
 *
 *     symbol
 *         ZXing-C++ 生成的二维码矩阵
 *
 * 输出：
 *
 *     pixels
 *         width * height
 *
 *         0   = 黑色
 *         255 = 白色
 *
 * 特点：
 *
 * 1. 输出尺寸严格等于 width * height
 * 2. QR module 使用整数倍缩放
 * 3. 保留 quiet zone
 * 4. 二维码居中
 */
std::vector<uint8_t> RenderQRCode(
        const ZXing::ImageView& symbol,
        int width,
        int height,
        int quietZone)
{
    if (!symbol.data()) {
        throw std::runtime_error(
                "ZXing-C++ returned empty QR symbol"
        );
    }

    if (symbol.width() <= 0 ||
        symbol.height() <= 0) {

        throw std::runtime_error(
                "ZXing-C++ returned invalid QR symbol"
        );
    }

    if (width <= 0 ||
        height <= 0) {

        throw std::invalid_argument(
                "Invalid output image size"
        );
    }

    if (quietZone < 0) {
        quietZone = 0;
    }

    /*
     * QR Code 正常情况下是正方形。
     */
    const int symbolWidth =
            symbol.width();

    const int symbolHeight =
            symbol.height();

    /*
     * 可用于二维码模块的区域。
     */
    const int availableWidth =
            width - quietZone * 2;

    const int availableHeight =
            height - quietZone * 2;

    if (availableWidth <= 0 ||
        availableHeight <= 0) {

        throw std::invalid_argument(
                "Image size is too small for quiet zone"
        );
    }

    /*
     * 为了避免二维码模块被非整数缩放，
     * 使用整数 moduleScale。
     */
    const int moduleScale =
            std::min(
                    availableWidth / symbolWidth,
                    availableHeight / symbolHeight
            );

    if (moduleScale <= 0) {
        throw std::invalid_argument(
                "Image size is too small for QR Code"
        );
    }

    const int renderedWidth =
            symbolWidth * moduleScale;

    const int renderedHeight =
            symbolHeight * moduleScale;

    /*
     * 将整个二维码居中。
     *
     * 注意：
     *
     * quietZone 是最小 quiet zone。
     * 如果目标尺寸还有多余空间，
     * 会平均分布到四周。
     */
    const int offsetX =
            (width - renderedWidth) / 2;

    const int offsetY =
            (height - renderedHeight) / 2;

    const size_t pixelCount =
            static_cast<size_t>(width)
            * static_cast<size_t>(height);

    std::vector<uint8_t> pixels(
            pixelCount,
            255
    );

    /*
     * ImageView 的 symbol() 是 Lum 图像。
     *
     * 0   = 黑色
     * 非0 = 白色
     */
    for (int sy = 0;
         sy < symbolHeight;
         ++sy) {

        for (int sx = 0;
             sx < symbolWidth;
             ++sx) {

            const uint8_t value =
                    *symbol.data(sx, sy);

            if (value == 0) {

                const int startX =
                        offsetX
                        + sx * moduleScale;

                const int startY =
                        offsetY
                        + sy * moduleScale;

                for (int dy = 0;
                     dy < moduleScale;
                     ++dy) {

                    const size_t rowOffset =
                            static_cast<size_t>(
                                    startY + dy
                            )
                            * static_cast<size_t>(
                                    width
                            );

                    for (int dx = 0;
                         dx < moduleScale;
                         ++dx) {

                        pixels[
                                rowOffset
                                + static_cast<size_t>(
                                        startX + dx
                                )
                        ] = 0;
                    }
                }
            }
        }
    }

    return pixels;
}


} // namespace


extern "C" {


/**
 * ============================================================
 * QrFT / OpenRQ
 *
 * Buffered RGBA -> raw bytes
 * ============================================================
 */
JNIEXPORT jbyteArray JNICALL
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

        ThrowJavaException(
                env,
                "java/lang/NullPointerException",
                "rgba"
        );

        return nullptr;
    }

    if (width <= 0 ||
        height <= 0 ||
        rowStride < width * 4) {

        ThrowJavaException(
                env,
                "java/lang/IllegalArgumentException",
                "Invalid image dimensions or rowStride"
        );

        return nullptr;
    }

    const jsize length =
            env->GetArrayLength(
                    rgbaArray
            );

    const jlong required =
            static_cast<jlong>(
                    rowStride
            ) * height;

    if (length < required) {

        ThrowJavaException(
                env,
                "java/lang/IllegalArgumentException",
                "RGBA array is smaller than rowStride * height"
        );

        return nullptr;
    }

    jbyte* raw =
            env->GetByteArrayElements(
                    rgbaArray,
                    nullptr
            );

    if (raw == nullptr) {
        return nullptr;
    }

    bool released = false;

    try {

        const auto* pixels =
                reinterpret_cast<const uint8_t*>(
                        raw
                );

        ZXing::ImageView image(
                pixels,
                width,
                height,
                ZXing::ImageFormat::RGBA,
                rowStride
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
                raw,
                JNI_ABORT
        );

        released = true;

        for (const auto& barcode : barcodes) {

            if (!barcode.isValid()) {
                continue;
            }

            const auto& bytes =
                    barcode.bytes();

            jbyteArray result =
                    env->NewByteArray(
                            static_cast<jsize>(
                                    bytes.size()
                            )
                    );

            if (result == nullptr) {
                return nullptr;
            }

            if (!bytes.empty()) {

                env->SetByteArrayRegion(
                        result,
                        0,
                        static_cast<jsize>(
                                bytes.size()
                        ),
                        reinterpret_cast<const jbyte*>(
                                bytes.data()
                        )
                );
            }

            return result;
        }

        return nullptr;

    } catch (const std::exception& e) {

        if (!released) {

            env->ReleaseByteArrayElements(
                    rgbaArray,
                    raw,
                    JNI_ABORT
            );
        }

        ThrowJavaException(
                env,
                "java/lang/RuntimeException",
                e.what()
        );

        return nullptr;

    } catch (...) {

        if (!released) {

            env->ReleaseByteArrayElements(
                    rgbaArray,
                    raw,
                    JNI_ABORT
            );
        }

        ThrowJavaException(
                env,
                "java/lang/RuntimeException",
                "Unknown ZXing-C++ exception"
        );

        return nullptr;
    }
}


/**
 * ============================================================
 * ZXing-C++ Writer
 *
 * byte[] -> QR Code -> grayscale pixels
 *
 * 这是 QrFT 推荐的编码 API。
 * ============================================================
 */
JNIEXPORT jbyteArray JNICALL
Java_com_hrz_zxingcpp_ZXingCpp_nativeEncodeBytes(
        JNIEnv* env,
        jclass,
        jbyteArray dataArray,
        jint width,
        jint height,
        jint quietZone,
        jint ecLevel)
{
    if (dataArray == nullptr) {

        ThrowJavaException(
                env,
                "java/lang/NullPointerException",
                "data"
        );

        return nullptr;
    }

    if (width <= 0 ||
        height <= 0) {

        ThrowJavaException(
                env,
                "java/lang/IllegalArgumentException",
                "Invalid image dimensions"
        );

        return nullptr;
    }

    if (quietZone < 0) {

        ThrowJavaException(
                env,
                "java/lang/IllegalArgumentException",
                "quietZone must be >= 0"
        );

        return nullptr;
    }

    const jsize dataLength =
            env->GetArrayLength(
                    dataArray
            );

    if (dataLength <= 0) {

        ThrowJavaException(
                env,
                "java/lang/IllegalArgumentException",
                "data is empty"
        );

        return nullptr;
    }

    jbyte* raw =
            env->GetByteArrayElements(
                    dataArray,
                    nullptr
            );

    if (raw == nullptr) {
        return nullptr;
    }

    bool released = false;

    try {

        /*
         * ========================================================
         * CreatorOptions
         * ========================================================
         *
         * 这里使用 ZXing-C++ 3.1.1 新 Writer API。
         *
         * 最重要的是：
         *
         * CreateBarcodeFromBytes()
         *
         * 它直接接收：
         *
         *     void* + size
         *
         * 所以：
         *
         * Java byte[]
         *     ↓
         * JNI
         *     ↓
         * CreateBarcodeFromBytes()
         *
         * 中间没有 String。
         */
        ZXing::CreatorOptions options(
                ZXing::BarcodeFormat::QRCode
        );

        /*
         * ecLevel:
         *
         * -1 = 使用 ZXing-C++ / Zint 默认值
         *
         * 1 = L
         * 2 = M
         * 3 = Q
         * 4 = H
         *
         * 也支持：
         *
         * 5 / 6 / 7 / 8
         *
         * 但 QrFT 第一阶段建议只使用：
         *
         * -1 / 1 / 2 / 3 / 4
         *
         * 因为新 Writer API 的 ecLevel 使用
         * L/M/Q/H 或百分比字符串。
         */
        if (ecLevel == 1) {

            options.options(
                    "ecLevel=L"
            );

        } else if (ecLevel == 2) {

            options.options(
                    "ecLevel=M"
            );

        } else if (ecLevel == 3) {

            options.options(
                    "ecLevel=Q"
            );

        } else if (ecLevel == 4) {

            options.options(
                    "ecLevel=H"
            );

        } else if (ecLevel != -1) {

            ThrowJavaException(
                    env,
                    "java/lang/IllegalArgumentException",
                    "ecLevel must be -1, 1, 2, 3 or 4"
            );

            env->ReleaseByteArrayElements(
                    dataArray,
                    raw,
                    JNI_ABORT
            );

            released = true;

            return nullptr;
        }

        /*
         * 直接使用原始 byte[]。
         *
         * 不进行：
         *
         *     UTF-8
         *     ISO-8859-1
         *     String
         *     Base64
         *
         * 转换。
         */
        const auto barcode =
                ZXing::CreateBarcodeFromBytes(
                        raw,
                        static_cast<int>(
                                dataLength
                        ),
                        options
                );

        /*
         * 现在已经完成二维码编码。
         *
         * 可以释放 Java byte[]。
         */
        env->ReleaseByteArrayElements(
                dataArray,
                raw,
                JNI_ABORT
        );

        released = true;

        /*
         * 获取二维码原始 symbol。
         *
         * 这里不是完整的 600x600 图片，
         * 而是二维码 module 矩阵。
         *
         * 例如可能是：
         *
         *     177 x 177
         *     185 x 185
         *     ...
         */
        const ZXing::ImageView symbol =
                barcode.symbol();

        /*
         * 根据指定的 width / height
         * 渲染成固定尺寸灰度图片。
         */
        const std::vector<uint8_t> pixels =
                RenderQRCode(
                        symbol,
                        width,
                        height,
                        quietZone
                );

        jbyteArray result =
                env->NewByteArray(
                        static_cast<jsize>(
                                pixels.size()
                        )
                );

        if (result == nullptr) {
            return nullptr;
        }

        env->SetByteArrayRegion(
                result,
                0,
                static_cast<jsize>(
                        pixels.size()
                ),
                reinterpret_cast<const jbyte*>(
                        pixels.data()
                )
        );

        return result;

    } catch (const std::exception& e) {

        if (!released) {

            env->ReleaseByteArrayElements(
                    dataArray,
                    raw,
                    JNI_ABORT
            );
        }

        ThrowJavaException(
                env,
                "java/lang/RuntimeException",
                e.what()
        );

        return nullptr;

    } catch (...) {

        if (!released) {

            env->ReleaseByteArrayElements(
                    dataArray,
                    raw,
                    JNI_ABORT
            );
        }

        ThrowJavaException(
                env,
                "java/lang/RuntimeException",
                "Unknown ZXing-C++ writer exception"
        );

        return nullptr;
    }
}


/**
 * ============================================================
 * 完整二维码解码
 * ============================================================
 */
JNIEXPORT jobject JNICALL
Java_com_hrz_zxingcpp_ZXingCpp_nativeDecode(
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

        ThrowJavaException(
                env,
                "java/lang/NullPointerException",
                "rgba"
        );

        return nullptr;
    }

    if (width <= 0 ||
        height <= 0 ||
        rowStride < width * 4) {

        ThrowJavaException(
                env,
                "java/lang/IllegalArgumentException",
                "Invalid image dimensions or rowStride"
        );

        return nullptr;
    }

    const jsize length =
            env->GetArrayLength(
                    rgbaArray
            );

    const jlong required =
            static_cast<jlong>(
                    rowStride
            ) * height;

    if (length < required) {

        ThrowJavaException(
                env,
                "java/lang/IllegalArgumentException",
                "RGBA array is smaller than rowStride * height"
        );

        return nullptr;
    }

    jbyte* raw =
            env->GetByteArrayElements(
                    rgbaArray,
                    nullptr
            );

    if (raw == nullptr) {
        return nullptr;
    }

    bool released = false;

    try {

        const auto* pixels =
                reinterpret_cast<const uint8_t*>(
                        raw
                );

        ZXing::ImageView image(
                pixels,
                width,
                height,
                ZXing::ImageFormat::RGBA,
                rowStride
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
                raw,
                JNI_ABORT
        );

        released = true;

        for (const auto& barcode : barcodes) {

            if (!barcode.isValid()) {
                continue;
            }

            /*
             * ------------------------------------------------------
             * 1. raw bytes
             * ------------------------------------------------------
             */
            const auto& bytes =
                    barcode.bytes();

            /*
             * ------------------------------------------------------
             * 2. ECI
             * ------------------------------------------------------
             */
            const bool hasECI =
                    barcode.hasECI();

            int eci = -1;

            std::string charset;

            if (hasECI) {

                const auto eciBytes =
                        barcode.bytesECI();

                eci =
                        ExtractFirstECI(
                                eciBytes
                        );

                if (eci >= 0) {

                    charset =
                            CharacterSetName(
                                    static_cast<ZXing::ECI>(
                                            eci
                                    )
                            );
                }
            }

            /*
             * ------------------------------------------------------
             * 3. UTF-8 text
             * ------------------------------------------------------
             */
            const std::string text =
                    barcode.text();

            /*
             * ------------------------------------------------------
             * 4. Java DecodeResult
             * ------------------------------------------------------
             */
            jclass resultClass =
                    env->FindClass(
                            "com/hrz/zxingcpp/ZXingCpp$DecodeResult"
                    );

            if (resultClass == nullptr) {
                return nullptr;
            }

            jmethodID constructor =
                    env->GetMethodID(
                            resultClass,
                            "<init>",
                            "([BZILjava/lang/String;Ljava/lang/String;)V"
                    );

            if (constructor == nullptr) {
                return nullptr;
            }

            /*
             * bytes
             */
            jbyteArray javaBytes =
                    env->NewByteArray(
                            static_cast<jsize>(
                                    bytes.size()
                            )
                    );

            if (javaBytes == nullptr) {
                return nullptr;
            }

            if (!bytes.empty()) {

                env->SetByteArrayRegion(
                        javaBytes,
                        0,
                        static_cast<jsize>(
                                bytes.size()
                        ),
                        reinterpret_cast<const jbyte*>(
                                bytes.data()
                        )
                );
            }

            /*
             * charset
             */
            jstring javaCharset = nullptr;

            if (!charset.empty()) {

                javaCharset =
                        env->NewStringUTF(
                                charset.c_str()
                        );

                if (javaCharset == nullptr) {
                    return nullptr;
                }
            }

            /*
             * text
             */
            jstring javaText = nullptr;

            if (!text.empty()) {

                javaText =
                        env->NewStringUTF(
                                text.c_str()
                        );

                if (javaText == nullptr) {
                    return nullptr;
                }
            }

            /*
             * DecodeResult
             */
            jobject result =
                    env->NewObject(
                            resultClass,
                            constructor,
                            javaBytes,
                            hasECI
                                    ? JNI_TRUE
                                    : JNI_FALSE,
                            static_cast<jint>(
                                    eci
                            ),
                            javaCharset,
                            javaText
                    );

            return result;
        }

        return nullptr;

    } catch (const std::exception& e) {

        if (!released) {

            env->ReleaseByteArrayElements(
                    rgbaArray,
                    raw,
                    JNI_ABORT
            );
        }

        ThrowJavaException(
                env,
                "java/lang/RuntimeException",
                e.what()
        );

        return nullptr;

    } catch (...) {

        if (!released) {

            env->ReleaseByteArrayElements(
                    rgbaArray,
                    raw,
                    JNI_ABORT
            );
        }

        ThrowJavaException(
                env,
                "java/lang/RuntimeException",
                "Unknown ZXing-C++ exception"
        );

        return nullptr;
    }
}


/**
 * ============================================================
 * ZXing-C++ version
 * ============================================================
 */
JNIEXPORT jstring JNICALL
Java_com_hrz_zxingcpp_ZXingCpp_nativeVersion(
        JNIEnv* env,
        jclass)
{
    const std::string version =
            ZXing::Version();

    return env->NewStringUTF(
            version.c_str()
    );
}


} // extern "C"
