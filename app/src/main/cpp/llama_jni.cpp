// JNI-мост к llama.cpp. Строки передаются как UTF-8 байты (jbyteArray),
// потому что NewStringUTF/GetStringUTFChars используют «modified UTF-8»
// и ломают эмодзи.
#include <jni.h>
#include <android/log.h>
#include <algorithm>
#include <atomic>
#include <mutex>
#include <string>
#include <vector>
#include "llama.h"

#define TAG "JuniorJNI"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace {
struct Handle {
    llama_model *model = nullptr;
    llama_context *ctx = nullptr;
    std::atomic<bool> cancel{false};
};
std::once_flag g_backend_once;
constexpr int kBatch = 512;
}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_riglineaimstriker_junior_inference_LlamaBridge_load(
        JNIEnv *env, jobject, jstring jpath, jint nCtx, jint nThreads) {
    std::call_once(g_backend_once, [] { llama_backend_init(); });
    const char *path = env->GetStringUTFChars(jpath, nullptr);
    llama_model_params mp = llama_model_default_params();
    mp.n_gpu_layers = 0;
    llama_model *model = llama_model_load_from_file(path, mp);
    env->ReleaseStringUTFChars(jpath, path);
    if (!model) { LOGE("model load failed"); return 0; }

    llama_context_params cp = llama_context_default_params();
    cp.n_ctx = static_cast<uint32_t>(nCtx);
    cp.n_batch = kBatch;
    cp.n_ubatch = kBatch;
    cp.n_threads = nThreads;
    cp.n_threads_batch = nThreads;
    llama_context *ctx = llama_init_from_model(model, cp);
    if (!ctx) { LOGE("context init failed"); llama_model_free(model); return 0; }

    auto *h = new Handle();
    h->model = model;
    h->ctx = ctx;
    return reinterpret_cast<jlong>(h);
}

// Коды: 0 ок, -1 плохой handle, -2 промпт не влезает в контекст,
// -3 ошибка decode, -4 ошибка токенизации, -5 исключение в callback.
extern "C" JNIEXPORT jint JNICALL
Java_com_riglineaimstriker_junior_inference_LlamaBridge_generate(
        JNIEnv *env, jobject, jlong handle, jbyteArray jprompt,
        jint maxTokens, jfloat temperature, jobject cb) {
    auto *h = reinterpret_cast<Handle *>(handle);
    if (!h) return -1;
    h->cancel = false;

    jsize plen = env->GetArrayLength(jprompt);
    std::string prompt(static_cast<size_t>(plen), '\0');
    env->GetByteArrayRegion(jprompt, 0, plen, reinterpret_cast<jbyte *>(prompt.data()));

    jclass cls = env->GetObjectClass(cb);
    jmethodID onToken = env->GetMethodID(cls, "onToken", "([B)Z");
    if (!onToken) return -1;

    const llama_vocab *vocab = llama_model_get_vocab(h->model);
    int n = -llama_tokenize(vocab, prompt.c_str(), (int) prompt.size(), nullptr, 0, true, true);
    if (n <= 0) return -4;
    std::vector<llama_token> toks(static_cast<size_t>(n));
    if (llama_tokenize(vocab, prompt.c_str(), (int) prompt.size(), toks.data(), n, true, true) < 0)
        return -4;

    const int nctx = (int) llama_n_ctx(h->ctx);
    if (n + 1 >= nctx) return -2;
    const int maxNew = std::min<int>(maxTokens, nctx - n - 1);

    llama_memory_clear(llama_get_memory(h->ctx), true);

    llama_sampler *smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(smpl, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_p(0.9f, 1));
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    int rc = 0;
    for (int i = 0; i < n && rc == 0; i += kBatch) {
        if (h->cancel) { llama_sampler_free(smpl); return 0; }
        llama_batch b = llama_batch_get_one(toks.data() + i, std::min(kBatch, n - i));
        if (llama_decode(h->ctx, b) != 0) rc = -3;
    }

    llama_token tok;
    for (int k = 0; k < maxNew && rc == 0 && !h->cancel; k++) {
        tok = llama_sampler_sample(smpl, h->ctx, -1);
        if (llama_vocab_is_eog(vocab, tok)) break;

        char buf[256];
        int len = llama_token_to_piece(vocab, tok, buf, sizeof(buf), 0, false);
        if (len > 0) {
            jbyteArray arr = env->NewByteArray(len);
            env->SetByteArrayRegion(arr, 0, len, reinterpret_cast<jbyte *>(buf));
            jboolean go = env->CallBooleanMethod(cb, onToken, arr);
            env->DeleteLocalRef(arr);
            if (env->ExceptionCheck()) { env->ExceptionClear(); rc = -5; break; }
            if (!go) break;
        }
        llama_batch b = llama_batch_get_one(&tok, 1);
        if (llama_decode(h->ctx, b) != 0) rc = -3;
    }

    llama_sampler_free(smpl);
    return rc;
}

extern "C" JNIEXPORT void JNICALL
Java_com_riglineaimstriker_junior_inference_LlamaBridge_cancel(JNIEnv *, jobject, jlong handle) {
    if (auto *h = reinterpret_cast<Handle *>(handle)) h->cancel = true;
}

extern "C" JNIEXPORT void JNICALL
Java_com_riglineaimstriker_junior_inference_LlamaBridge_free(JNIEnv *, jobject, jlong handle) {
    auto *h = reinterpret_cast<Handle *>(handle);
    if (!h) return;
    if (h->ctx) llama_free(h->ctx);
    if (h->model) llama_model_free(h->model);
    delete h;
}
