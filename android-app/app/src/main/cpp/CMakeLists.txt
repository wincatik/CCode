cmake_minimum_required(VERSION 3.22.1)
project(ccode)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(JNI_LIBS_DIR ${CMAKE_CURRENT_LIST_DIR}/../jniLibs/${ANDROID_ABI})
set(LLAMA_INCLUDE_DIR ${CMAKE_CURRENT_LIST_DIR}/include)

add_library(ggml-base SHARED IMPORTED)
set_target_properties(ggml-base PROPERTIES IMPORTED_LOCATION ${JNI_LIBS_DIR}/libggml-base.so)
add_library(ggml-cpu SHARED IMPORTED)
set_target_properties(ggml-cpu PROPERTIES IMPORTED_LOCATION ${JNI_LIBS_DIR}/libggml-cpu.so)
add_library(ggml SHARED IMPORTED)
set_target_properties(ggml PROPERTIES IMPORTED_LOCATION ${JNI_LIBS_DIR}/libggml.so)
add_library(llama SHARED IMPORTED)
set_target_properties(llama PROPERTIES IMPORTED_LOCATION ${JNI_LIBS_DIR}/libllama.so)

add_library(ccode SHARED ccode_jni.cpp)
target_include_directories(ccode PRIVATE ${LLAMA_INCLUDE_DIR})
target_link_libraries(ccode llama ggml ggml-cpu ggml-base log android)
