#include <jni.h>
#include <android/log.h>
#include <algorithm>
#include <cstring>
#include <atomic>
#include <chrono>
#include <string>
#include <thread>
#include <exception>
#include <vector>
#include "llama.h"

#define LOG_TAG "CCodeJNI"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static llama_model * g_model = nullptr;
static llama_context * g_ctx = nullptr;
static llama_sampler * g_sampler = nullptr;
static std::atomic<bool> g_cancelRequested{false};
static constexpr uint32_t kContextSize = 1536;
static constexpr uint32_t kBatchSize = 256;

static size_t completeUtf8Prefix(const std::string & text) {
    size_t i = 0;
    while (i < text.size()) {
        const unsigned char c = static_cast<unsigned char>(text[i]);
        size_t need = c < 0x80 ? 1 : (c >= 0xC2 && c <= 0xDF ? 2 : (c >= 0xE0 && c <= 0xEF ? 3 : (c >= 0xF0 && c <= 0xF4 ? 4 : 0)));
        if (need == 0 || i + need > text.size()) break;
        for (size_t j = 1; j < need; ++j) if ((static_cast<unsigned char>(text[i + j]) & 0xC0) != 0x80) return i;
        i += need;
    }
    return i;
}

static std::string jstringToString(JNIEnv * env, jstring value) {
    if (!value) return {};
    const char * chars = env->GetStringUTFChars(value, nullptr);
    std::string result = chars ? chars : "";
    if (chars) env->ReleaseStringUTFChars(value, chars);
    return result;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_ccode_code_LocalCodeEngine_nativeLoadModel(JNIEnv * env, jobject, jstring path) {
    if (g_ctx || g_model) {
        if (g_sampler) llama_sampler_free(g_sampler);
        if (g_ctx) llama_free(g_ctx);
        if (g_model) llama_model_free(g_model);
        g_ctx = nullptr;
        g_sampler = nullptr;
        g_model = nullptr;
    }

    const std::string modelPath = jstringToString(env, path);
    if (modelPath.empty()) return JNI_FALSE;

    llama_backend_init();
    auto modelParams = llama_model_default_params();
    modelParams.n_gpu_layers = 0;
    g_model = llama_model_load_from_file(modelPath.c_str(), modelParams);
    if (!g_model) {
        LOGE("Could not load model: %s", modelPath.c_str());
        return JNI_FALSE;
    }

    auto contextParams = llama_context_default_params();
    contextParams.n_ctx = kContextSize;
    contextParams.n_batch = kBatchSize;
    constexpr int cpuThreads = 6;
    contextParams.n_threads = cpuThreads;
    contextParams.n_threads_batch = cpuThreads;
    g_ctx = llama_init_from_model(g_model, contextParams);
    if (!g_ctx) {
        LOGE("Could not create llama context");
        llama_model_free(g_model);
        g_model = nullptr;
        return JNI_FALSE;
    }

    auto samplerParams = llama_sampler_chain_default_params();
    g_sampler = llama_sampler_chain_init(samplerParams);
    llama_sampler_chain_add(g_sampler, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_temp(0.2f));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_greedy());
    LOGE("Model loaded: ctx=%u batch=%u", kContextSize, kBatchSize);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_ccode_code_LocalCodeEngine_nativeIsLoaded(JNIEnv *, jobject) {
    return (g_model != nullptr && g_ctx != nullptr) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_ccode_code_LocalCodeEngine_nativeGenerateStreaming(JNIEnv * env, jobject, jstring prompt, jobject listener) {
    if (!g_model || !g_ctx || !g_sampler) return env->NewStringUTF("Модель ещё не загружена.");
    const std::string input = jstringToString(env, prompt);
    if (input.empty()) return env->NewStringUTF("Пустой запрос.");
    llama_memory_clear(llama_get_memory(g_ctx), true);
    llama_sampler_reset(g_sampler);
    const llama_vocab * vocab = llama_model_get_vocab(g_model);
    const uint32_t contextLimit = llama_n_ctx(g_ctx);
    const int32_t tokenCapacity = static_cast<int32_t>(std::min<size_t>(input.size() + 8, static_cast<size_t>(contextLimit)));
    std::vector<llama_token> tokens(static_cast<size_t>(std::max<int32_t>(tokenCapacity, 1)));
    int32_t tokenCount = llama_tokenize(vocab, input.c_str(), static_cast<int32_t>(input.size()), tokens.data(), tokenCapacity, false, true);
    if (tokenCount < 0) {
        LOGE("Prompt token count=%d exceeds safe capacity=%d", -tokenCount, tokenCapacity);
        return env->NewStringUTF("Запрос слишком длинный для текущего контекста.");
    }
    if (tokenCount <= 0) return env->NewStringUTF("Не удалось обработать запрос.");
    if (static_cast<uint32_t>(tokenCount) >= contextLimit - 32) {
        LOGE("Prompt rejected: %d tokens, context=%u", tokenCount, contextLimit);
        return env->NewStringUTF("Запрос слишком длинный для текущего контекста.");
    }

    llama_batch batch = llama_batch_init(kBatchSize, 0, 1);
    if (!batch.token || !batch.pos || !batch.seq_id || !batch.logits) {
        LOGE("Could not allocate llama batch: size=%u", kBatchSize);
        return env->NewStringUTF("Не удалось выделить память для запроса.");
    }
    std::string output;
    std::string pendingUtf8;
    const auto promptStart = std::chrono::steady_clock::now();
    for (int32_t offset = 0; offset < tokenCount; offset += static_cast<int32_t>(kBatchSize)) {
        const int32_t count = std::min<int32_t>(static_cast<int32_t>(kBatchSize), tokenCount - offset);
        for (int32_t i = 0; i < count; ++i) {
            batch.token[i] = tokens[static_cast<size_t>(offset + i)];
            batch.pos[i] = offset + i;
            batch.n_seq_id[i] = 1;
            batch.seq_id[i][0] = 0;
            batch.logits[i] = (offset + i == tokenCount - 1);
        }
        batch.n_tokens = count;
        if (llama_decode(g_ctx, batch) != 0) {
            LOGE("llama_decode failed for prompt batch offset=%d count=%d", offset, count);
            llama_batch_free(batch);
            return env->NewStringUTF("Ошибка обработки запроса. Подробности записаны в журнале.");
        }
    }
    const auto promptEnd = std::chrono::steady_clock::now();

    const llama_token eos = llama_vocab_eos(vocab);
    int32_t position = tokenCount;
    const std::string marker = "<|im_start|>user\n";
    const size_t userStart = input.find(marker);
    const size_t userLength = userStart == std::string::npos ? input.size() : input.size() - userStart - marker.size();
    const bool codeRequest = input.find("python") != std::string::npos || input.find("Python") != std::string::npos || input.find("Godot") != std::string::npos || input.find("гдскрипт") != std::string::npos || input.find("скрипт") != std::string::npos || input.find("игр") != std::string::npos || input.find("полный файл") != std::string::npos || input.find("код") != std::string::npos;
    const int requestedMaxTokens = codeRequest ? 512 : (userLength < 80 ? 48 : (userLength < 180 ? 160 : 256));
    const int maxNewTokens = std::max(1, std::min(requestedMaxTokens, static_cast<int>(contextLimit) - tokenCount - 1));
    jclass listenerClass = listener ? env->GetObjectClass(listener) : nullptr;
    jmethodID onToken = listenerClass ? env->GetMethodID(listenerClass, "onToken", "(Ljava/lang/String;)V") : nullptr;
    jmethodID onProgress = listenerClass ? env->GetMethodID(listenerClass, "onProgress", "(I)V") : nullptr;
    jmethodID onMetrics = listenerClass ? env->GetMethodID(listenerClass, "onMetrics", "(JJIF)V") : nullptr;
    g_cancelRequested.store(false);
    const auto generationStart = std::chrono::steady_clock::now();
    int generatedTokens = 0;
    for (int i = 0; i < maxNewTokens; ++i, ++position) {
        if (g_cancelRequested.load()) break;
        const llama_token next = llama_sampler_sample(g_sampler, g_ctx, -1);
        llama_sampler_accept(g_sampler, next);
        if (next == eos) break;
        ++generatedTokens;
        char piece[256];
        const int n = llama_token_to_piece(vocab, next, piece, sizeof(piece), 0, false);
        if (n > 0) {
            output.append(piece, static_cast<size_t>(n));
            pendingUtf8.append(piece, static_cast<size_t>(n));
            if (listener && onToken) {
                const size_t complete = completeUtf8Prefix(pendingUtf8);
                if (complete > 0) {
                    jstring token = env->NewStringUTF(pendingUtf8.substr(0, complete).c_str());
                    env->CallVoidMethod(listener, onToken, token);
                    env->DeleteLocalRef(token);
                    pendingUtf8.erase(0, complete);
                }
            }
        }
        if (listener && onProgress) env->CallVoidMethod(listener, onProgress, (i + 1) * 100 / maxNewTokens);
        batch.token[0] = next;
        batch.pos[0] = position;
        batch.n_seq_id[0] = 1;
        batch.seq_id[0][0] = 0;
        batch.logits[0] = true;
        batch.n_tokens = 1;
        if (llama_decode(g_ctx, batch) != 0) {
            LOGE("llama_decode failed during generation at token=%d position=%d", generatedTokens, position);
            break;
        }
    }
    if (listener && onToken && !pendingUtf8.empty()) {
        jstring token = env->NewStringUTF(pendingUtf8.c_str());
        env->CallVoidMethod(listener, onToken, token);
        env->DeleteLocalRef(token);
    }
    const auto generationEnd = std::chrono::steady_clock::now();
    const long long promptMs = std::chrono::duration_cast<std::chrono::milliseconds>(promptEnd - promptStart).count();
    const long long generationMs = std::chrono::duration_cast<std::chrono::milliseconds>(generationEnd - generationStart).count();
    const float tokensPerSecond = generationMs > 0 ? (generatedTokens * 1000.0f / generationMs) : 0.0f;
    if (listener && onMetrics) env->CallVoidMethod(listener, onMetrics, static_cast<jlong>(promptMs < 0 ? 0 : promptMs), static_cast<jlong>(generationMs), static_cast<jint>(generatedTokens), tokensPerSecond);
    llama_batch_free(batch);
    return env->NewStringUTF(output.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_ccode_code_LocalCodeEngine_nativeCancel(JNIEnv *, jobject) {
    g_cancelRequested.store(true);
}

extern "C" JNIEXPORT void JNICALL
Java_com_ccode_code_LocalCodeEngine_nativeRelease(JNIEnv *, jobject) {
    if (g_sampler) llama_sampler_free(g_sampler);
    if (g_ctx) llama_free(g_ctx);
    if (g_model) llama_model_free(g_model);
    g_ctx = nullptr;
    g_sampler = nullptr;
    g_model = nullptr;
    llama_backend_free();
}
