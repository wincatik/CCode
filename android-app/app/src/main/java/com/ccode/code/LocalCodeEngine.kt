package com.ccode.code

/** Minimal Kotlin wrapper around the local llama.cpp engine. */
object LocalCodeEngine {
    init {
        System.loadLibrary("ccode")
    }

    fun loadModel(path: String): Boolean = nativeLoadModel(path)
    fun isLoaded(): Boolean = nativeIsLoaded()
    interface StreamListener {
        fun onToken(token: String)
        fun onProgress(progress: Int)
        fun onMetrics(promptMs: Long, generationMs: Long, tokens: Int, tokensPerSecond: Float)
    }

    fun generateStreaming(prompt: String, listener: StreamListener): String = nativeGenerateStreaming(prompt, listener)
    fun cancel() = nativeCancel()
    fun release() = nativeRelease()

    private external fun nativeLoadModel(path: String): Boolean
    private external fun nativeIsLoaded(): Boolean
    private external fun nativeGenerateStreaming(prompt: String, listener: StreamListener): String
    private external fun nativeCancel()
    private external fun nativeRelease()
}
