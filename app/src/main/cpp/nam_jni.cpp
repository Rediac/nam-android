#include <jni.h>
#include <string>
#include <memory>
#include <atomic>
#include <cstring>
#include <vector>
#include <android/log.h>
#include <aaudio/AAudio.h>

// NeuralAmpModelerCore public API
#include "NAM/get_dsp.h"
#include "NAM/dsp.h"
#include "NAM/activations.h"

#define LOG_TAG "NAMPlayer"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// ── Global state ──────────────────────────────────────────────────────────────
static std::unique_ptr<nam::DSP> g_model;
static AAudioStream*  g_inputStream   = nullptr;
static AAudioStream*  g_outputStream  = nullptr;
static std::atomic<bool> g_running    {false};

// Audio ring buffer (simple single-producer single-consumer)
static constexpr int  BLOCK_SIZE    = 256;
static constexpr int  SAMPLE_RATE   = 48000;
static constexpr int  RING_FRAMES   = 4096;

static float g_ring[RING_FRAMES]    = {};
static std::atomic<int> g_writePos  {0};
static std::atomic<int> g_readPos   {0};

// ── Input callback: mic → ring buffer ────────────────────────────────────────
static aaudio_data_callback_result_t inputCallback(
        AAudioStream* /*stream*/, void* /*userData*/,
        void* audioData, int32_t numFrames)
{
    const float* src = static_cast<const float*>(audioData);
    int wp = g_writePos.load(std::memory_order_relaxed);
    for (int i = 0; i < numFrames; i++) {
        g_ring[wp % RING_FRAMES] = src[i];
        wp++;
    }
    g_writePos.store(wp, std::memory_order_release);
    return AAUDIO_CALLBACK_RESULT_CONTINUE;
}

// ── Output callback: ring buffer → NAM → speaker ─────────────────────────────
static aaudio_data_callback_result_t outputCallback(
        AAudioStream* /*stream*/, void* /*userData*/,
        void* audioData, int32_t numFrames)
{
    float* out = static_cast<float*>(audioData);

    if (!g_model || !g_running) {
        std::memset(out, 0, numFrames * sizeof(float));
        return AAUDIO_CALLBACK_RESULT_CONTINUE;
    }

    // Drain ring buffer into a local block
    std::vector<float> inBuf(numFrames, 0.0f);
    int rp  = g_readPos.load(std::memory_order_relaxed);
    int wp  = g_writePos.load(std::memory_order_acquire);
    int avail = wp - rp;
    int copy  = std::min(avail, numFrames);
    for (int i = 0; i < copy; i++) {
        inBuf[i] = g_ring[rp % RING_FRAMES];
        rp++;
    }
    g_readPos.store(rp, std::memory_order_release);

    try {
        // nam::DSP::process(input*, output*, nFrames)
        g_model->process(inBuf.data(), out, numFrames);
        g_model->finalize(numFrames);
    } catch (...) {
        std::memset(out, 0, numFrames * sizeof(float));
    }

    return AAUDIO_CALLBACK_RESULT_CONTINUE;
}

// ── Helper: open one AAudio stream ───────────────────────────────────────────
static AAudioStream* openStream(aaudio_direction_t direction,
                                AAudioStream_dataCallback cb)
{
    AAudioStreamBuilder* builder = nullptr;
    if (AAudio_createStreamBuilder(&builder) != AAUDIO_OK) return nullptr;

    AAudioStreamBuilder_setDirection(builder,       direction);
    AAudioStreamBuilder_setFormat(builder,          AAUDIO_FORMAT_PCM_FLOAT);
    AAudioStreamBuilder_setSampleRate(builder,      SAMPLE_RATE);
    AAudioStreamBuilder_setChannelCount(builder,    1);
    AAudioStreamBuilder_setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
    AAudioStreamBuilder_setSharingMode(builder,     AAUDIO_SHARING_MODE_EXCLUSIVE);
    AAudioStreamBuilder_setDataCallback(builder,    cb, nullptr);

    AAudioStream* stream = nullptr;
    aaudio_result_t r = AAudioStreamBuilder_openStream(builder, &stream);
    AAudioStreamBuilder_delete(builder);

    if (r != AAUDIO_OK) {
        LOGE("openStream dir=%d failed: %s", direction, AAudio_convertResultToText(r));
        return nullptr;
    }
    return stream;
}

// ── JNI ──────────────────────────────────────────────────────────────────────

extern "C" JNIEXPORT jstring JNICALL
Java_com_rediac_namplayer_NamEngine_nativeGetVersion(JNIEnv* env, jobject)
{
    return env->NewStringUTF("NAM Player 1.0 (Core v0.5.x / A2)");
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_rediac_namplayer_NamEngine_nativeLoadModel(JNIEnv* env, jobject, jstring jpath)
{
    const char* path = env->GetStringUTFChars(jpath, nullptr);
    LOGI("Loading NAM model: %s", path);

    try {
        // Enable fast tanh approximation (same as the official plugin)
        nam::activations::Activation::enable_fast_tanh();

        // get_dsp() is the official factory — works for A1 and A2
        g_model = nam::get_dsp(std::string(path));

        LOGI("Model loaded OK");
        env->ReleaseStringUTFChars(jpath, path);
        return JNI_TRUE;
    } catch (const std::exception& e) {
        LOGE("Failed to load model: %s", e.what());
        env->ReleaseStringUTFChars(jpath, path);
        g_model.reset();
        return JNI_FALSE;
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_rediac_namplayer_NamEngine_nativeStartAudio(JNIEnv*, jobject)
{
    if (g_outputStream) {
        LOGI("Audio already running");
        return JNI_TRUE;
    }

    // Reset ring buffer
    g_writePos.store(0);
    g_readPos.store(0);

    // Open input (mic)
    g_inputStream = openStream(AAUDIO_DIRECTION_INPUT, inputCallback);
    if (!g_inputStream) return JNI_FALSE;

    // Open output (speaker/headphones)
    g_outputStream = openStream(AAUDIO_DIRECTION_OUTPUT, outputCallback);
    if (!g_outputStream) {
        AAudioStream_close(g_inputStream);
        g_inputStream = nullptr;
        return JNI_FALSE;
    }

    // Start both
    if (AAudioStream_requestStart(g_inputStream)  != AAUDIO_OK ||
        AAudioStream_requestStart(g_outputStream) != AAUDIO_OK)
    {
        LOGE("Failed to start audio streams");
        AAudioStream_close(g_inputStream);
        AAudioStream_close(g_outputStream);
        g_inputStream = g_outputStream = nullptr;
        return JNI_FALSE;
    }

    g_running = true;
    LOGI("AAudio started @ %d Hz", SAMPLE_RATE);
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_rediac_namplayer_NamEngine_nativeStopAudio(JNIEnv*, jobject)
{
    g_running = false;
    if (g_inputStream) {
        AAudioStream_requestStop(g_inputStream);
        AAudioStream_close(g_inputStream);
        g_inputStream = nullptr;
    }
    if (g_outputStream) {
        AAudioStream_requestStop(g_outputStream);
        AAudioStream_close(g_outputStream);
        g_outputStream = nullptr;
    }
    LOGI("AAudio stopped");
}

extern "C" JNIEXPORT void JNICALL
Java_com_rediac_namplayer_NamEngine_nativeUnloadModel(JNIEnv*, jobject)
{
    g_model.reset();
    LOGI("Model unloaded");
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_rediac_namplayer_NamEngine_nativeIsModelLoaded(JNIEnv*, jobject)
{
    return g_model ? JNI_TRUE : JNI_FALSE;
}
