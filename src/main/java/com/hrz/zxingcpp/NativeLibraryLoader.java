package com.hrz.zxingcpp;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/**
 * ZXing-C++ JNI 动态库加载器。
 *
 * 保证整个 Java 包只负责加载一次 native library。
 */
final class NativeLibraryLoader {

    private static boolean loaded = false;

    private NativeLibraryLoader() {
    }

    static synchronized void load() {
        if (loaded) {
            return;
        }

        loadNativeLibrary();

        loaded = true;
    }

    static void loadNativeLibrary() {

        if (loaded) {
            return;
        }


        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
        String resource;


        if (os.contains("win")) {
            if (isX86_64(arch)) {
                resource = "/native/windows-x86_64/zxing_jni.dll";
            } else {
                throw new UnsupportedOperationException("Unsupported Windows architecture: " + arch);
            }
        } else if (os.contains("linux")) {
            if (isX86_64(arch)) {
                resource = "/native/linux-x86_64/libzxing_jni.so";

            } else if (isAarch64(arch)) {
                resource = "/native/linux-aarch64/libzxing_jni.so";
            } else {
                throw new UnsupportedOperationException("Unsupported Linux architecture: " + arch);
            }

        } else {
            throw new UnsupportedOperationException("Unsupported operating system: " + os);
        }


        try (InputStream input = NativeLibraryLoader.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("Native library not found: " + resource);
            }
            String fileName = resource.substring(resource.lastIndexOf('/') + 1);
            String suffix = fileName.endsWith(".dll") ? ".dll" : ".so";
            Path nativeTempFile = Files.createTempFile("zxing_jni_", suffix);
            Files.copy(input, nativeTempFile, StandardCopyOption.REPLACE_EXISTING);
            nativeTempFile.toFile().deleteOnExit();
            System.load(nativeTempFile.toAbsolutePath().toString());
            loaded = true;
        }catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static boolean isX86_64(String arch) {
        return "amd64".equals(arch) || "x86_64".equals(arch) || "x64".equals(arch);
    }

    private static boolean isAarch64(String arch) {
        return "aarch64".equals(arch) || "arm64".equals(arch);
    }
}