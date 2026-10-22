cmake_minimum_required(VERSION 3.21)

project(zxing_jni LANGUAGES CXX)

include(FetchContent)

# ============================================================
# Java / JNI
# ============================================================

if (NOT DEFINED ENV{JAVA_HOME})
    message(FATAL_ERROR "JAVA_HOME is not set")
endif()

set(JAVA_HOME "$ENV{JAVA_HOME}")

message(STATUS "JAVA_HOME=${JAVA_HOME}")

if (WIN32)
    set(JNI_OS_INCLUDE "${JAVA_HOME}/include/win32")
else()
    set(JNI_OS_INCLUDE "${JAVA_HOME}/include/linux")
endif()

if (NOT EXISTS "${JAVA_HOME}/include/jni.h")
    message(FATAL_ERROR
        "jni.h not found: ${JAVA_HOME}/include/jni.h"
    )
endif()


# ============================================================
# ZXing-C++
# ============================================================

# 静态链接 ZXing-C++
set(BUILD_SHARED_LIBS OFF CACHE BOOL
    "Build ZXing-C++ as static library"
    FORCE
)

# 开启二维码/条码读取
set(ZXING_READERS ON CACHE BOOL
    "Build ZXing readers"
    FORCE
)

# 开启新的 Writer API
#
# NEW:
#   CreateBarcode()
#   CreateBarcodeFromText()
#   CreateBarcodeFromBytes()
#   WriteBarcodeToImage()
#
# 对 QrFT 来说最重要的是：
#
#   CreateBarcodeFromBytes()
#
# 可以直接接收 byte[]。
set(ZXING_WRITERS NEW CACHE STRING
    "Build ZXing writers"
    FORCE
)

# 使用 ZXing-C++ 自带的 Zint
set(ZXING_USE_BUNDLED_ZINT ON CACHE BOOL
    "Use bundled Zint for barcode creation"
    FORCE
)

# 不编译 ZXing-C++ 示例
set(ZXING_EXAMPLES OFF CACHE BOOL
    "Build ZXing examples"
    FORCE
)

set(ZXING_EXAMPLES_QT OFF CACHE BOOL
    "Build ZXing Qt examples"
    FORCE
)

# 不编译单元测试
set(ZXING_UNIT_TESTS OFF CACHE BOOL
    "Build ZXing unit tests"
    FORCE
)

# 不编译 blackbox tests
set(ZXING_BLACKBOX_TESTS OFF CACHE BOOL
    "Build ZXing blackbox tests"
    FORCE
)

# 不编译 C API
set(ZXING_C_API OFF CACHE BOOL
    "Build ZXing C API"
    FORCE
)

# 不编译 Python 模块
set(ZXING_PYTHON_MODULE OFF CACHE BOOL
    "Build ZXing Python module"
    FORCE
)


FetchContent_Declare(
    zxing_cpp
    GIT_REPOSITORY https://github.com/zxing-cpp/zxing-cpp.git
    GIT_TAG v3.1.1
    GIT_SHALLOW TRUE
)

FetchContent_MakeAvailable(zxing_cpp)


if (NOT TARGET ZXing)
    message(FATAL_ERROR "ZXing target was not created")
endif()

message(STATUS "ZXing target found")


# ============================================================
# JNI library
# ============================================================

add_library(zxing_jni SHARED
    zxing_jni.cpp
)


# ============================================================
# Include
# ============================================================

target_include_directories(zxing_jni PRIVATE

    # JNI
    "${JAVA_HOME}/include"
    "${JNI_OS_INCLUDE}"

    # ZXing-C++
    #
    # v3.1.1:
    #   core/src/ZXingCpp.h
    #   core/src/CreateBarcode.h
    #   core/src/ImageView.h
    #   ...
    #
    # 因此这里必须包含 core/src。
    "${zxing_cpp_SOURCE_DIR}/core/src"
)


# ============================================================
# Link
# ============================================================

target_link_libraries(zxing_jni PRIVATE
    ZXing::ZXing
)


# ============================================================
# C++ standard
# ============================================================

target_compile_features(zxing_jni PRIVATE
    cxx_std_20
)


# ============================================================
# Compiler options
# ============================================================

if (MSVC)

    target_compile_options(zxing_jni PRIVATE
        /utf-8
    )

else()

    target_compile_options(zxing_jni PRIVATE
        -fvisibility=hidden
    )

endif()


# ============================================================
# Output
# ============================================================

set_target_properties(zxing_jni PROPERTIES
    OUTPUT_NAME "zxing_jni"
)


# ============================================================
# Install
# ============================================================

install(
    TARGETS zxing_jni
    RUNTIME DESTINATION .
    LIBRARY DESTINATION .
    ARCHIVE DESTINATION .
)
