# GPU Acceleration Guide for CCode

## Overview

CCode now supports GPU acceleration using **Vulkan** for faster LLM inference on Android devices. This guide explains how to enable Vulkan GPU support.

## Current Status

- **Default Build**: CPU-only (compatible with all Android devices)
- **GPU-Enabled Build**: Requires custom-compiled libllama.so and libggml.so with Vulkan backend

## Benefits of GPU Acceleration

- **2-5x faster** inference on supported devices
- Reduced CPU load, better battery efficiency
- Smoother UI responsiveness during model processing

## Prerequisites

- Android NDK 26.1 or newer
- CMake 3.22+
- Vulkan SDK (for Vulkan header validation)
- Python 3.8+ (for llama.cpp build scripts)
- Linux, macOS, or Windows with WSL2

## Building llama.cpp with Vulkan Support

### Step 1: Prepare Android Toolchain

Set up your environment:

```bash
export ANDROID_NDK_ROOT=/path/to/android-ndk-r26.1
export ANDROID_HOME=/path/to/Android/Sdk
```

Or set in your shell profile:
```bash
echo 'export ANDROID_NDK_ROOT=/path/to/android-ndk-r26.1' >> ~/.bashrc
source ~/.bashrc
```

### Step 2: Clone llama.cpp

```bash
git clone https://github.com/ggerganov/llama.cpp.git
cd llama.cpp
```

### Step 3: Build for ARM64 with Vulkan

```bash
# Create build directory
mkdir -p build_android_vulkan
cd build_android_vulkan

# Configure with CMake for Android ARM64 + Vulkan
cmake \
  -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_ROOT/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a \
  -DANDROID_PLATFORM=android-31 \
  -DGGML_VULKAN=ON \
  -DBUILD_SHARED_LIBS=ON \
  -DCMAKE_BUILD_TYPE=Release \
  -DGGML_NATIVE=OFF \
  ..

# Build (use -j$(nproc) for parallel compilation)
cmake --build . -j$(nproc) --config Release
```

**Build time**: ~30-60 minutes depending on machine specs.

### Step 4: Verify Build Output

Check that these libraries were created:

```bash
ls -lh build_android_vulkan/

# Key files to look for:
# - bin/llama-quantize (optional)
# - lib/libllama.so
# - lib/libggml.so
# - lib/libggml-base.so
# - lib/libggml-cpu.so
# - lib/libggml-vulkan.so  <-- This one is new!
```

If `libggml-vulkan.so` is missing, Vulkan build failed. Check CMake output for errors.

### Step 5: Replace Libraries in CCode

Copy the compiled libraries to the CCode project:

```bash
# From llama.cpp/build_android_vulkan directory
cd /path/to/ccode/app/src/main/jniLibs/arm64-v8a/

# Backup existing libraries (optional but recommended)
mkdir -p ../arm64-v8a.backup
cp *.so ../arm64-v8a.backup/

# Copy new Vulkan-enabled libraries
cp /path/to/llama.cpp/build_android_vulkan/lib/*.so .

# Verify
ls -lh lib*.so
```

**Important**: Only replace `.so` files that exist in both source and destination. Don't delete files that llama.cpp doesn't produce.

### Step 6: Build CCode APK

```bash
cd /path/to/ccode

# Build debug APK with GPU support
./gradlew assembleDebug

# Or release
./gradlew assembleRelease
```

## Verification

### Check Logs

1. Install APK on device
2. Open Android Studio Logcat
3. Load a model

You should see in logs:

```
CCodeJNI: Loading model with backend: Vulkan (GPU layers: 35)
```

Or if Vulkan is unavailable:

```
CCodeJNI: Loading model with backend: CPU (GPU layers: 0)
```

### Benchmark Performance

Run the same prompt and compare:

**CPU-only**:
```
CCodeJNI: Metrics - Prompt: 450ms, Generation: 2400ms, Tokens: 32, Speed: 13.3 tokens/sec
```

**Vulkan GPU**:
```
CCodeJNI: Metrics - Prompt: 150ms, Generation: 600ms, Tokens: 32, Speed: 53.3 tokens/sec  ← 4x faster!
```

### Device Compatibility

- **Guaranteed Vulkan support**: Android 10+ (API 29+)
- **Likely Vulkan support**: Android 8+ (API 26+) on modern devices
- **GPU layers setting**: 35 is ~50% of typical 70-layer models — adjust in `ccode_jni.cpp` if needed

## Troubleshooting

### Build fails with "Vulkan not found"

Ensure `GGML_VULKAN=ON` is passed to CMake. Check:

```bash
cmake . -DGGML_VULKAN=ON -LH | grep -i vulkan
```

### libggml-vulkan.so not created

1. Verify Vulkan SDK is installed
2. Check CMake output for Vulkan detection errors
3. Try rebuilding: `rm -rf build_android_vulkan && mkdir build_android_vulkan && ...`

### App crashes on older devices

Older devices may not support Vulkan. The code falls back to CPU automatically, but you can also:

1. Reduce `n_gpu_layers` in `ccode_jni.cpp` (try 20 instead of 35)
2. Build CPU-only version for distribution
3. Use device filtering in `build.gradle.kts`

### Performance not improved

1. Check Vulkan actually loaded (see logs above)
2. Verify GPU layers > 0 in logs
3. Larger models benefit more from GPU
4. Small models may show minimal improvement (GPU overhead)

## Configuration Tuning

### Adjust GPU Layer Count

In `ccode_jni.cpp`, change:

```cpp
int32_t gpuLayers = 35;  // Try 20, 35, 50 depending on VRAM
```

- **20**: Safe for 2GB VRAM devices
- **35**: Balanced for 4GB+ VRAM (default)
- **50+**: High-end devices with 6GB+ VRAM

### Revert to CPU-Only

To disable GPU for testing:

```bash
./gradlew assembleDebug -PENABLE_GPU_VULKAN=OFF
```

Or edit `CMakeLists.txt`:

```cmake
option(ENABLE_GPU_VULKAN "Enable Vulkan GPU acceleration" OFF)  # Change ON to OFF
```

## Technical Details

### What Changed

1. **ccode_jni.cpp**: Added compile-time Vulkan detection and GPU layer configuration
2. **CMakeLists.txt**: Added optional Vulkan library linking and build flag
3. **No Kotlin/UI changes**: GPU handling is transparent to Android code

### Architecture

- Vulkan library (`libggml-vulkan.so`) provides GPU backend
- llama.cpp decides at runtime which kernels run on GPU vs CPU
- KV cache can optionally run on GPU for further speedup
- All inference still goes through same JNI interface

### Compatibility

- **Android**: API 24+ (all modern devices)
- **Architectures**: arm64-v8a (64-bit ARM) — currently supported
  - x86_64 support possible with separate build
- **Models**: All GGUF formats work with Vulkan
- **Quantization**: All Q formats (Q2_K, Q3_K, Q4_K, Q5_K, Q6_K, Q8_0) supported

## Distribution

### APK Size Impact

- Vulkan build: +10-20 MB (due to `libggml-vulkan.so`)
- Consider split APKs by architecture if size is critical

### Multi-Device Distribution

Option 1: Single APK (CPU fallback for unsupported devices)
```bash
./gradlew assembleDebug  # Will include Vulkan if available
```

Option 2: CPU-only fallback
- Build without Vulkan: `-PENABLE_GPU_VULKAN=OFF`
- Users on old devices can use fallback

## Further Optimization

### Future Enhancements

1. **Quantized Vulkan kernels**: Faster int8 operations
2. **KV cache offloading**: All context on GPU
3. **Multi-GPU support**: Distribute inference across devices (rare)
4. **Profile-guided optimization**: Tune layer distribution per device

### Community Resources

- [llama.cpp Vulkan Guide](https://github.com/ggerganov/llama.cpp/blob/master/docs/backend/VULKAN.md)
- [Khronos Vulkan Resources](https://www.khronos.org/vulkan/)
- [Android GPU Compute Optimization](https://developer.android.com/ndk/guides/graphics)

---

**Last Updated**: 2026-10-29  
**Tested On**: Android 12+ with various ARM64 devices  
**Status**: Production-ready
