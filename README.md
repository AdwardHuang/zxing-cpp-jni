# zxing-cpp-jni

一个面向 Java 11 的 ZXing-C++ JNI 封装示例项目。

项目目标是给 Java/Swing 项目提供一个轻量的二维码解码后端，并支持把 ZXing-C++ 的原始 barcode bytes 直接返回给 Java。对于 QrFT 这类使用 ISO-8859-1 一字节映射承载二进制 Fountain/OpenRQ packet 的场景，可以避免先转 Base64。

## 当前版本

- ZXing-C++：3.1.1
- Java：11+
- C++：C++20
- CMake：3.21+
- Maven：3.8+
- GitHub Actions：Windows x86_64、Linux x86_64

ZXing-C++ 官方项目当前使用 CMake，核心库默认使用 C++20；项目也提供 C API 和多种 wrapper。这里没有使用官方 C API，而是直接从 C++ API 调用 `ImageView`、`ReaderOptions`、`ReadBarcodes` 和 `Barcode::bytes()`，减少一层转换。

## 目录

```text
zxing-cpp-jni/
├── pom.xml
├── README.md
├── LICENSE
├── src/main/java/com/hrz/zxingcpp/ZXingCpp.java
├── src/main/cpp/CMakeLists.txt
├── src/main/cpp/zxing_jni.cpp
└── .github/workflows/build.yml
```

## 本地构建

需要 JDK 11、CMake 和 C++ 编译器。

Windows PowerShell：

```powershell
$env:JAVA_HOME="C:\Program Files\Java\jdk-11"
cmake -S src/main/cpp -B build/native -DCMAKE_BUILD_TYPE=Release -DCMAKE_INSTALL_PREFIX=build/install
cmake --build build/native --config Release --parallel
cmake --install build/native --config Release
```

Linux：

```bash
export JAVA_HOME=/path/to/jdk-11
cmake -S src/main/cpp -B build/native -DCMAKE_BUILD_TYPE=Release -DCMAKE_INSTALL_PREFIX=build/install
cmake --build build/native --parallel
cmake --install build/native
```

第一次配置时 CMake 会通过 FetchContent 从 GitHub 拉取 ZXing-C++ 3.1.1，因此本地构建需要网络。

## Java API

### BufferedImage → 原始 bytes

```java
byte[] data = ZXingCpp.decodeBytes(image);

if (data != null) {
    System.out.println("decoded bytes = " + data.length);
}
```

### BufferedImage → ISO-8859-1

```java
String content = ZXingCpp.decodeIso88591(image);

if (content != null) {
    byte[] packet = content.getBytes(StandardCharsets.ISO_8859_1);
}
```

对于 QrFT，推荐直接使用：

```java
byte[] packet = ZXingCpp.decodeBytes(image);
```

然后交给 OpenRQ，只有在现有协议仍要求 `String` 时才使用 ISO-8859-1 转换。

### 查询 native ZXing-C++ 版本

```java
System.out.println(ZXingCpp.version());
```

## JAR 内 native 库

Release JAR 中的资源结构：

```text
native/
├── windows-x86_64/zxing_jni.dll
└── linux-x86_64/libzxing_jni.so
```

Java 启动时会把对应 native 库提取到临时文件并调用 `System.load()`。

## GitHub Actions

推送到 `main`、Pull Request 或手动执行 workflow 都会编译 Windows/Linux native 库。

创建 tag：

```bash
git tag v0.1.0
git push origin v0.1.0
```

当 tag 符合 `v*` 时，Actions 会自动创建 GitHub Release 并上传 JAR/ZIP。

## 为什么返回 Barcode::bytes()

ZXing-C++ 的 `Barcode` 对象同时提供 `text()` 和 `bytes()`。二维码内容如果本质上是任意二进制数据，应该优先使用 `bytes()`，不要让 native 层先按 UTF-8 解释。

这对于 QrFT 的 OpenRQ packet 尤其重要：`EncodingPacket.asArray()` 是二进制 packet，Java 端若要保持现有 ISO-8859-1 协议，可以显式进行：

```java
String s = new String(packetBytes, StandardCharsets.ISO_8859_1);
byte[] packetBytes2 = s.getBytes(StandardCharsets.ISO_8859_1);
```

但 JNI 直接返回 `byte[]` 更干净。

## 注意

这是一个最小 JNI 封装，不试图覆盖 ZXing-C++ 的全部 API。当前 native reader 只启用 QR Code，并设置 `maxNumberOfSymbols(1)`，适合 QrFT 一帧一二维码的场景。
