cmake_minimum_required(VERSION 3.21)

project(zxing_jni LANGUAGES CXX)

include(FetchContent)

# ---------------------------------------------------------
# Java / JNI
# ---------------------------------------------------------

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

# ---------------------------------------------------------
# ZXing-C++
# ---------------------------------------------------------

set(BUILD_SHARED_LIBS OFF CACHE BOOL
    "Build ZXing-C++ as static library"
    FORCE
)

set(ZXING_READERS ON CACHE BOOL
    "Build ZXing readers"
    FORCE
)

set(ZXING_WRITERS OFF CACHE STRING
    "Build ZXing writers"
    FORCE
)

set(ZXING_EXAMPLES OFF CACHE BOOL
    "Build examples"
    FORCE
)

set(ZXING_EXAMPLES_QT OFF CACHE BOOL
    "Build Qt examples"
    FORCE
)

set(ZXING_UNIT_TESTS OFF CACHE BOOL
    "Build unit tests"
    FORCE
)

set(ZXING_BLACKBOX_TESTS OFF CACHE BOOL
    "Build blackbox tests"
    FORCE
)

set(ZXING_C_API OFF CACHE BOOL
    "Build C API"
    FORCE
)

set(ZXING_PYTHON_MODULE OFF CACHE BOOL
    "Build Python module"
    FORCE
)

FetchContent_Declare(
    zxing_cpp
    GIT_REPOSITORY https://github.com/zxing-cpp/zxing-cpp.git
    GIT_TAG v3.1.1
    GIT_SHALLOW TRUE
)

FetchContent_MakeAvailable(zxing_cpp)

# ---------------------------------------------------------
# Check ZXing target
# ---------------------------------------------------------

if (NOT TARGET ZXing)
    message(FATAL_ERROR
        "ZXing target was not created"
    )
endif()

message(STATUS "ZXing target found")

# ---------------------------------------------------------
# JNI library
# ---------------------------------------------------------

add_library(zxing_jni SHARED
    zxing_jni.cpp
)

target_include_directories(zxing_jni PRIVATE
    "${JAVA_HOME}/include"
    "${JNI_OS_INCLUDE}"
)

target_link_libraries(zxing_jni PRIVATE
    ZXing::ZXing
)

target_compile_features(zxing_jni PRIVATE
    cxx_std_20
)

# ---------------------------------------------------------
# Compiler options
# ---------------------------------------------------------

if (MSVC)

    target_compile_options(zxing_jni PRIVATE
        /utf-8
    )

else()

    target_compile_options(zxing_jni PRIVATE
        -fvisibility=hidden
    )

endif()

# ---------------------------------------------------------
# Output name
# ---------------------------------------------------------

set_target_properties(zxing_jni PROPERTIES
    OUTPUT_NAME "zxing_jni"
)

# ---------------------------------------------------------
# Install
# ---------------------------------------------------------

install(
    TARGETS zxing_jni
    RUNTIME DESTINATION .
    LIBRARY DESTINATION .
    ARCHIVE DESTINATION .
)
