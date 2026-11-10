cmake_minimum_required(VERSION 3.22.1)

# Must precede project()/NDK toolchain initialization. No shared libc++ dependency.
set(ANDROID_STL c++_static CACHE STRING "" FORCE)
project(jarvis_llm LANGUAGES C CXX)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
foreach(flag LLAMA_BUILD_COMMON LLAMA_BUILD_TESTS LLAMA_BUILD_TOOLS
        LLAMA_BUILD_EXAMPLES LLAMA_BUILD_SERVER LLAMA_BUILD_APP LLAMA_BUILD_UI
        LLAMA_BUILD_MTMD LLAMA_OPENSSL LLAMA_CONNECT GGML_BUILD_TESTS
        GGML_BUILD_EXAMPLES GGML_NATIVE GGML_BACKEND_DL GGML_CPU_ALL_VARIANTS
        GGML_OPENMP GGML_OPENMP_FETCH GGML_LLAMAFILE GGML_CPU_KLEIDIAI
        GGML_BLAS GGML_ACCELERATE GGML_CUDA GGML_METAL GGML_VULKAN GGML_OPENCL
        GGML_HEXAGON GGML_RPC GGML_SYCL GGML_WEBGPU GGML_HIP GGML_MUSA
        GGML_OPENVINO GGML_ET GGML_ZDNN GGML_ZENDNN GGML_VIRTGPU)
    set(${flag} OFF CACHE BOOL "" FORCE)
endforeach()
set(GGML_CPU ON CACHE BOOL "" FORCE)
# Android's x86_64 ABI does not promise AVX/FMA; keep emulator builds portable.
foreach(flag GGML_AVX GGML_AVX2 GGML_FMA GGML_F16C GGML_BMI2 GGML_SSE42)
    set(${flag} OFF CACHE BOOL "" FORCE)
endforeach()

include(FetchContent)
# Upstream commit 2026-11-10; archive SHA-256 verified before pinning.
FetchContent_Declare(llama_cpp
    URL https://codeload.github.com/ggml-org/llama.cpp/tar.gz/3b4d6ab3f79651935fba1dd93732f29022a7f5f5
    URL_HASH SHA256=f6aa510d53755ccdfe2b916a65b0dde622a4869f333f28e31b3b904d22b90bb1
)
FetchContent_MakeAvailable(llama_cpp)

add_library(jarvis_llm SHARED local_llm.cpp)
target_link_libraries(jarvis_llm PRIVATE llama)
target_compile_options(jarvis_llm PRIVATE -Wall -Wextra -fvisibility=hidden)
# Keep static C++/llama symbols private to avoid interposition with ONNX/sherpa.
target_link_options(jarvis_llm PRIVATE -Wl,--exclude-libs,ALL -Wl,-z,max-page-size=16384)
