#include <jni.h>

#include <cstdint>
#include <string>
#include <vector>

#include "ZXingCpp.h"

namespace {

std::string JStringToUtf8(JNIEnv* env, jstring value)
{
    if (value == nullptr) {
        return {};
    }

    const char* chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) {
        return {};
    }

    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

void ThrowJavaException(JNIEnv* env, const char* className, const std::string& message)
{
    jclass cls = env->FindClass(className);
    if (cls != nullptr) {
        env->ThrowNew(cls, message.c_str());
    }
}

} // namespace

extern "C" {

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
        ThrowJavaException(env, "java/lang/NullPointerException", "rgba");
        return nullptr;
    }

    if (width <= 0 || height <= 0 || rowStride < width * 4) {
        ThrowJavaException(env, "java/lang/IllegalArgumentException", "Invalid image dimensions or rowStride");
        return nullptr;
    }

    const jsize length = env->GetArrayLength(rgbaArray);
    const jlong required = static_cast<jlong>(rowStride) * height;
    if (length < required) {
        ThrowJavaException(env, "java/lang/IllegalArgumentException", "RGBA array is smaller than rowStride * height");
        return nullptr;
    }

    jboolean isCopy = JNI_FALSE;
    jbyte* raw = env->GetByteArrayElements(rgbaArray, &isCopy);
    if (raw == nullptr) {
        return nullptr;
    }

    try {
        const auto* pixels = reinterpret_cast<const uint8_t*>(raw);
        ZXing::ImageView image(pixels, width, height, ZXing::ImageFormat::RGBA, rowStride);

        auto options = ZXing::ReaderOptions()
                .formats(ZXing::BarcodeFormat::QRCode)
                .tryRotate(tryRotate == JNI_TRUE)
                .tryInvert(tryInvert == JNI_TRUE)
                .tryDownscale(true)
                .maxNumberOfSymbols(1)
                .returnErrors(false);

        auto barcodes = ZXing::ReadBarcodes(image, options);

        env->ReleaseByteArrayElements(rgbaArray, raw, JNI_ABORT);

        for (const auto& barcode : barcodes) {
            if (!barcode.isValid()) {
                continue;
            }

            const auto& bytes = barcode.bytes();
            jbyteArray result = env->NewByteArray(static_cast<jsize>(bytes.size()));
            if (result == nullptr) {
                return nullptr;
            }

            if (!bytes.empty()) {
                env->SetByteArrayRegion(
                        result,
                        0,
                        static_cast<jsize>(bytes.size()),
                        reinterpret_cast<const jbyte*>(bytes.data()));
            }
            return result;
        }

        return nullptr;
    } catch (const std::exception& e) {
        env->ReleaseByteArrayElements(rgbaArray, raw, JNI_ABORT);
        ThrowJavaException(env, "java/lang/RuntimeException", e.what());
        return nullptr;
    } catch (...) {
        env->ReleaseByteArrayElements(rgbaArray, raw, JNI_ABORT);
        ThrowJavaException(env, "java/lang/RuntimeException", "Unknown ZXing-C++ exception");
        return nullptr;
    }
}

JNIEXPORT jstring JNICALL
Java_com_hrz_zxingcpp_ZXingCpp_nativeVersion(JNIEnv* env, jclass)
{
    const std::string version = ZXing::Version();
    return env->NewStringUTF(version.c_str());
}

} // extern "C"
