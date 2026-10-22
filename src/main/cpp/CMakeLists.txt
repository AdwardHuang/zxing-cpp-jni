cmake_minimum_required(VERSION 3.21)
project(zxing_jni LANGUAGES CXX)

include(FetchContent)

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(ZXING_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(ZXING_EXAMPLES OFF CACHE BOOL "" FORCE)
set(ZXING_EXAMPLES_QT OFF CACHE BOOL "" FORCE)
set(ZXING_UNIT_TESTS OFF CACHE BOOL "" FORCE)
set(ZXING_BLACKBOX_TESTS OFF CACHE BOOL "" FORCE)
set(ZXING_C_API OFF CACHE BOOL "" FORCE)
set(ZXING_PYTHON_MODULE OFF CACHE BOOL "" FORCE)
set(ZXING_WRITERS OFF CACHE STRING "" FORCE)
set(ZXING_READERS ON CACHE BOOL "" FORCE)
set(ZXING_ENABLE_1D OFF CACHE BOOL "" FORCE)
set(ZXING_ENABLE_AZTEC OFF CACHE BOOL "" FORCE)
set(ZXING_ENABLE_DATAMATRIX OFF CACHE BOOL "" FORCE)
set(ZXING_ENABLE_MAXICODE OFF CACHE BOOL "" FORCE)
set(ZXING_ENABLE_PDF417 OFF CACHE BOOL "" FORCE)
set(ZXING_ENABLE_QRCODE ON CACHE BOOL "" FORCE)

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

if (NOT DEFINED ENV{JAVA_HOME})
    message(FATAL_ERROR "JAVA_HOME must point to a JDK")
endif()

set(JAVA_HOME "$ENV{JAVA_HOME}")

if (WIN32)
    set(JNI_OS_INCLUDE "${JAVA_HOME}/include/win32")
else()
    set(JNI_OS_INCLUDE "${JAVA_HOME}/include/linux")
endif()

if (NOT EXISTS "${JAVA_HOME}/include/jni.h")
    message(FATAL_ERROR "jni.h not found under JAVA_HOME=${JAVA_HOME}")
endif()

add_library(zxing_jni SHARED zxing_jni.cpp)

target_include_directories(zxing_jni PRIVATE
    "${JAVA_HOME}/include"
    "${JNI_OS_INCLUDE}"
)

target_link_libraries(zxing_jni PRIVATE ZXing)

target_compile_features(zxing_jni PRIVATE cxx_std_20)

if (MSVC)
    target_compile_options(zxing_jni PRIVATE /utf-8)
endif()

set_target_properties(zxing_jni PROPERTIES
    OUTPUT_NAME "zxing_jni"
    CXX_VISIBILITY_PRESET hidden
    VISIBILITY_INLINES_HIDDEN YES
)

if (UNIX AND NOT APPLE)
    set_target_properties(zxing_jni PROPERTIES
        BUILD_WITH_INSTALL_RPATH TRUE
        INSTALL_RPATH "$ORIGIN"
    )
endif()

install(TARGETS zxing_jni
    RUNTIME DESTINATION .
    LIBRARY DESTINATION .
    ARCHIVE DESTINATION .
)
