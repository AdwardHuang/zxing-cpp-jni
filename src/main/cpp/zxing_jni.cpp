#include <jni.h>

#include <cstdint>
#include <string>
#include <vector>

#include "ZXingCpp.h"
#include "CharacterSet.h"
#include "ECI.h"

namespace {

void ThrowJavaException(
        JNIEnv* env,
        const char* className,
        const std::string& message)
{
    jclass cls = env->FindClass(className);

    if (cls != nullptr) {
        env->ThrowNew(cls, message.c_str());
    }
}

/**
 * 将 ZXing-C++ 的 CharacterSet 转换成 Java Charset 可识别的名称。
 *
 * ZXing-C++ 自己的 CharacterSet::ToString() 返回：
 *
 * UTF-8
 * ISO-8859-1
 * Shift_JIS
 * GB2312
 * GB18030
 * ...
 *
 * 这些名称 Java Charset 基本都可以识别。
 */
std::string CharacterSetName(ZXing::ECI eci)
{
    const auto characterSet = ZXing::ToCharacterSet(eci);

    if (characterSet == ZXing::CharacterSet::Unknown) {
        return {};
    }

    return ZXing::ToString(characterSet);
}

/**
 * 从 ZXing-C++ 的 bytesECI() 中提取第一个 ECI。
 *
 * ZXing-C++ v3.1.1 的 ECI protocol：
 *
 *     \ + 6 位数字
 *
 * 例如：
 *
 *     UTF-8
 *     ECI = 26
 *     表示为：
 *
 *     \000026
 *
 * bytesECI() 的开头还可能包含 symbology identifier，
 * 例如 QR Code：
 *
 *     ]Q3
 *
 * 因此这里从第一个 '\' 开始寻找 ECI。
 *
 * 注意：
 * 这里只提取第一个 ECI。
 * 对于普通文本二维码已经足够。
 */
int ExtractFirstECI(const std::vector<uint8_t>& bytesECI)
{
    for (size_t i = 0; i < bytesECI.size(); ++i) {

        if (bytesECI[i] != '\\') {
            continue;
        }

        if (i + 6 >= bytesECI.size()) {
            continue;
        }

        bool digits = true;
        int value = 0;

        for (size_t j = 1; j <= 6; ++j) {

            const uint8_t c = bytesECI[i + j];

            if (c < '0' || c > '9') {
                digits = false;
                break;
            }

            value = value * 10 + (c - '0');
        }

        if (digits) {
            return value;
        }
    }

    return -1;
}

} // namespace


extern "C" {


/**
 * 原始 bytes 解码。
 *
 * 用于 QrFT / OpenRQ。
 *
 * 不进行字符集转换。
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
                "rgba");

        return nullptr;
    }

    if (width <= 0 ||
        height <= 0 ||
        rowStride < width * 4) {

        ThrowJavaException(
                env,
                "java/lang/IllegalArgumentException",
                "Invalid image dimensions or rowStride");

        return nullptr;
    }

    const jsize length =
            env->GetArrayLength(rgbaArray);

    const jlong required =
            static_cast<jlong>(rowStride) * height;

    if (length < required) {

        ThrowJavaException(
                env,
                "java/lang/IllegalArgumentException",
                "RGBA array is smaller than rowStride * height");

        return nullptr;
    }

    jbyte* raw =
            env->GetByteArrayElements(
                    rgbaArray,
                    nullptr);

    if (raw == nullptr) {
        return nullptr;
    }

    bool released = false;

    try {

        const auto* pixels =
                reinterpret_cast<const uint8_t*>(raw);

        ZXing::ImageView image(
                pixels,
                width,
                height,
                ZXing::ImageFormat::RGBA,
                rowStride);

        auto options =
                ZXing::ReaderOptions()
                        .formats(ZXing::BarcodeFormat::QRCode)
                        .tryRotate(tryRotate == JNI_TRUE)
                        .tryInvert(tryInvert == JNI_TRUE)
                        .tryDownscale(true)
                        .maxNumberOfSymbols(1)
                        .returnErrors(false);

        auto barcodes =
                ZXing::ReadBarcodes(image, options);

        env->ReleaseByteArrayElements(
                rgbaArray,
                raw,
                JNI_ABORT);

        released = true;

        for (const auto& barcode : barcodes) {

            if (!barcode.isValid()) {
                continue;
            }

            const auto& bytes =
                    barcode.bytes();

            jbyteArray result =
                    env->NewByteArray(
                            static_cast<jsize>(bytes.size()));

            if (result == nullptr) {
                return nullptr;
            }

            if (!bytes.empty()) {

                env->SetByteArrayRegion(
                        result,
                        0,
                        static_cast<jsize>(bytes.size()),
                        reinterpret_cast<const jbyte*>(
                                bytes.data()));
            }

            return result;
        }

        return nullptr;

    } catch (const std::exception& e) {

        if (!released) {
            env->ReleaseByteArrayElements(
                    rgbaArray,
                    raw,
                    JNI_ABORT);
        }

        ThrowJavaException(
                env,
                "java/lang/RuntimeException",
                e.what());

        return nullptr;

    } catch (...) {

        if (!released) {
            env->ReleaseByteArrayElements(
                    rgbaArray,
                    raw,
                    JNI_ABORT);
        }

        ThrowJavaException(
                env,
                "java/lang/RuntimeException",
                "Unknown ZXing-C++ exception");

        return nullptr;
    }
}


/**
 * 完整二维码解码。
 *
 * 返回：
 *
 * DecodeResult
 *
 * 包含：
 *
 * bytes
 * hasECI
 * eci
 * charset
 * text
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
                "rgba");

        return nullptr;
    }

    if (width <= 0 ||
        height <= 0 ||
        rowStride < width * 4) {

        ThrowJavaException(
                env,
                "java/lang/IllegalArgumentException",
                "Invalid image dimensions or rowStride");

        return nullptr;
    }

    const jsize length =
            env->GetArrayLength(rgbaArray);

    const jlong required =
            static_cast<jlong>(rowStride) * height;

    if (length < required) {

        ThrowJavaException(
                env,
                "java/lang/IllegalArgumentException",
                "RGBA array is smaller than rowStride * height");

        return nullptr;
    }

    jbyte* raw =
            env->GetByteArrayElements(
                    rgbaArray,
                    nullptr);

    if (raw == nullptr) {
        return nullptr;
    }

    bool released = false;

    try {

        const auto* pixels =
                reinterpret_cast<const uint8_t*>(raw);

        ZXing::ImageView image(
                pixels,
                width,
                height,
                ZXing::ImageFormat::RGBA,
                rowStride);

        auto options =
                ZXing::ReaderOptions()
                        .formats(ZXing::BarcodeFormat::QRCode)
                        .tryRotate(tryRotate == JNI_TRUE)
                        .tryInvert(tryInvert == JNI_TRUE)
                        .tryDownscale(true)
                        .maxNumberOfSymbols(1)
                        .returnErrors(false);

        auto barcodes =
                ZXing::ReadBarcodes(image, options);

        env->ReleaseByteArrayElements(
                rgbaArray,
                raw,
                JNI_ABORT);

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

                /*
                 * bytesECI() 例如：
                 *
                 * ]Q3\000026...
                 *
                 * 找到第一个 ECI。
                 */
                const auto eciBytes =
                        barcode.bytesECI();

                eci =
                        ExtractFirstECI(eciBytes);

                if (eci >= 0) {

                    charset =
                            CharacterSetName(
                                    static_cast<ZXing::ECI>(eci));
                }
            }


            /*
             * ------------------------------------------------------
             * 3. UTF-8 text
             * ------------------------------------------------------
             *
             * ZXing-C++ 已经根据 ECI 完成字符集转换。
             *
             * text() 返回 UTF-8。
             */
            const std::string text =
                    barcode.text();


            /*
             * ------------------------------------------------------
             * 4. 创建 Java DecodeResult
             * ------------------------------------------------------
             */
            jclass resultClass =
                    env->FindClass(
                            "com/hrz/zxingcpp/ZXingCpp$DecodeResult");

            if (resultClass == nullptr) {
                return nullptr;
            }


            /*
             * DecodeResult(
             *      byte[],
             *      boolean,
             *      int,
             *      String,
             *      String
             * )
             */
            jmethodID constructor =
                    env->GetMethodID(
                            resultClass,
                            "<init>",
                            "([BZILjava/lang/String;Ljava/lang/String;)V");

            if (constructor == nullptr) {
                return nullptr;
            }


            /*
             * bytes
             */
            jbyteArray javaBytes =
                    env->NewByteArray(
                            static_cast<jsize>(bytes.size()));

            if (javaBytes == nullptr) {
                return nullptr;
            }

            if (!bytes.empty()) {

                env->SetByteArrayRegion(
                        javaBytes,
                        0,
                        static_cast<jsize>(bytes.size()),
                        reinterpret_cast<const jbyte*>(
                                bytes.data()));
            }


            /*
             * charset
             */
            jstring javaCharset = nullptr;

            if (!charset.empty()) {

                javaCharset =
                        env->NewStringUTF(
                                charset.c_str());

                if (javaCharset == nullptr) {
                    return nullptr;
                }
            }


            /*
             * text
             *
             * ZXing-C++ text() 返回 UTF-8。
             */
            jstring javaText = nullptr;

            if (!text.empty()) {

                javaText =
                        env->NewStringUTF(
                                text.c_str());

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
                            hasECI ? JNI_TRUE : JNI_FALSE,
                            static_cast<jint>(eci),
                            javaCharset,
                            javaText);

            return result;
        }

        return nullptr;

    } catch (const std::exception& e) {

        if (!released) {
            env->ReleaseByteArrayElements(
                    rgbaArray,
                    raw,
                    JNI_ABORT);
        }

        ThrowJavaException(
                env,
                "java/lang/RuntimeException",
                e.what());

        return nullptr;

    } catch (...) {

        if (!released) {
            env->ReleaseByteArrayElements(
                    rgbaArray,
                    raw,
                    JNI_ABORT);
        }

        ThrowJavaException(
                env,
                "java/lang/RuntimeException",
                "Unknown ZXing-C++ exception");

        return nullptr;
    }
}


/**
 * 返回 ZXing-C++ 版本。
 */
JNIEXPORT jstring JNICALL
Java_com_hrz_zxingcpp_ZXingCpp_nativeVersion(
        JNIEnv* env,
        jclass)
{
    const std::string version =
            ZXing::Version();

    return env->NewStringUTF(
            version.c_str());
}

} // extern "C"
