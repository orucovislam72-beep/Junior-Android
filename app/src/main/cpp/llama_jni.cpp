// JNI-мост к llama.cpp. Строки передаются как UTF-8 байты (jbyteArray),
// потому что NewStringUTF/GetStringUTFChars используют «modified UTF-8»
// и ломают эмодзи.
//
// 0.3.0: переиспользование KV-кэша. Handle хранит последовательность токенов,
// которая уже лежит в памяти контекста. При новом запросе ищем общий префикс,
// удаляем из памяти только «хвост» (llama_memory_seq_rm) и считаем лишь новые токены.
// В чате это значит, что системный промпт и вся прошлая переписка не пересчитываются.
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
    // Токены, которые сейчас лежат в KV-памяти (последовательность 0), по порядку позиций.
    std::vector<llama_token> cached;
};
std::once_flag g_backend_once;
constexpr int kBatch = 512;

void reset_memory(Handle *h) {
    llama_memory_clear(llama_get_memory(h->ctx), true);
    h->cached.clear();
}

bool tokenize(const llama_vocab *vocab, const std::string &text, std::vector<llama_token> &out) {
    int n = -llama_tokenize(vocab, text.c_str(), (int) text.size(), nullptr, 0, true, true);
    if (n <= 0) return false;
    out.resize(static_cast<size_t>(n));
    return llama_tokenize(vocab, text.c_str(), (int) text.size(), out.data(), n, true, true) >= 0;
}

std::string bytes_to_string(JNIEnv *env, jbyteArray arr) {
    jsize len = env->GetArrayLength(arr);
    std::string s(static_cast<size_t>(len), '\0');
    env->GetByteArrayRegion(arr, 0, len, reinterpret_cast<jbyte *>(s.data()));
    return s;
}
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
//
// Callback (Kotlin TokenCallback):
//   onPrompt(nPrompt, nReused, nCtx, nDone) — до и во время чтения промпта;
//   onToken(bytes): Boolean — каждый новый токен; false = остановить.
extern "C" JNIEXPORT jint JNICALL
Java_com_riglineaimstriker_junior_inference_LlamaBridge_generate(
        JNIEnv *env, jobject, jlong handle, jbyteArray jprompt,
        jint maxTokens, jfloat temperature, jobject cb) {
    auto *h = reinterpret_cast<Handle *>(handle);
    if (!h) return -1;
    h->cancel = false;

    const std::string prompt = bytes_to_string(env, jprompt);

    jclass cls = env->GetObjectClass(cb);
    jmethodID onToken = env->GetMethodID(cls, "onToken", "([B)Z");
    jmethodID onPrompt = env->GetMethodID(cls, "onPrompt", "(IIII)V");
    if (!onToken || !onPrompt) { env->ExceptionClear(); return -1; }

    const llama_vocab *vocab = llama_model_get_vocab(h->model);
    std::vector<llama_token> toks;
    if (!tokenize(vocab, prompt, toks)) return -4;
    const int n = (int) toks.size();

    const int nctx = (int) llama_n_ctx(h->ctx);
    if (n + 1 >= nctx) return -2;
    const int maxNew = std::min<int>(maxTokens, nctx - n - 1);

    // Общий префикс с тем, что уже посчитано. Хотя бы один токен считаем заново,
    // чтобы получить свежие логиты для первого сэмпла.
    llama_memory_t mem = llama_get_memory(h->ctx);
    int common = 0;
    const int cachedN = (int) h->cached.size();
    while (common < cachedN && common < n && h->cached[common] == toks[common]) common++;
    if (common >= n) common = n - 1;

    if (common <= 0) {
        reset_memory(h);
        common = 0;
    } else if (!llama_memory_seq_rm(mem, 0, common, -1)) {
        // Частичное удаление не поддерживается (например, рекуррентная модель) — считаем всё заново.
        reset_memory(h);
        common = 0;
    } else {
        h->cached.resize(static_cast<size_t>(common));
    }

    auto report = [&](int done) -> bool {
        env->CallVoidMethod(cb, onPrompt, (jint) n, (jint) common, (jint) nctx, (jint) done);
        if (env->ExceptionCheck()) { env->ExceptionClear(); return false; }
        return true;
    };
    if (!report(common)) return -5;

    llama_sampler *smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
    // Штраф за повторы: маленькие модели склонны зацикливаться, особенно на коде.
    llama_sampler_chain_add(smpl, llama_sampler_init_penalties(
        llama_vocab_n_tokens(vocab), 64, 1.1f, 0.0f, 0.0f));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_p(0.9f, 1));
    llama_sampler_chain_add(smpl, llama_sampler_init_min_p(0.05f, 1));
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    int rc = 0;
    for (int i = common; i < n && rc == 0; i += kBatch) {
        if (h->cancel) { llama_sampler_free(smpl); return 0; }
        const int len = std::min(kBatch, n - i);
        llama_batch b = llama_batch_get_one(toks.data() + i, len);
        if (llama_decode(h->ctx, b) != 0) {
            // Состояние памяти не гарантировано — начинаем с чистого листа в следующий раз.
            reset_memory(h);
            rc = -3;
            break;
        }
        h->cached.insert(h->cached.end(), toks.begin() + i, toks.begin() + i + len);
        if (!report(i + len)) rc = -5;
    }

    llama_token tok;
    for (int k = 0; k < maxNew && rc == 0 && !h->cancel; k++) {
        tok = llama_sampler_sample(smpl, h->ctx, -1);
        if (llama_vocab_is_eog(vocab, tok)) break;

        char buf[256];
        int len = llama_token_to_piece(vocab, tok, buf, sizeof(buf), 0, false);
        jbyteArray arr = env->NewByteArray(len > 0 ? len : 0);
        if (len > 0) env->SetByteArrayRegion(arr, 0, len, reinterpret_cast<jbyte *>(buf));
        jboolean go = env->CallBooleanMethod(cb, onToken, arr);
        env->DeleteLocalRef(arr);
        if (env->ExceptionCheck()) { env->ExceptionClear(); rc = -5; break; }
        if (!go) break;

        llama_batch b = llama_batch_get_one(&tok, 1);
        if (llama_decode(h->ctx, b) != 0) {
            reset_memory(h);
            rc = -3;
            break;
        }
        h->cached.push_back(tok);
    }

    llama_sampler_free(smpl);
    return rc;
}

// Число токенов в тексте (для точной подгонки промпта под контекст). -1 при ошибке.
extern "C" JNIEXPORT jint JNICALL
Java_com_riglineaimstriker_junior_inference_LlamaBridge_countTokens(
        JNIEnv *env, jobject, jlong handle, jbyteArray jtext) {
    auto *h = reinterpret_cast<Handle *>(handle);
    if (!h) return -1;
    const std::string text = bytes_to_string(env, jtext);
    if (text.empty()) return 0;
    const llama_vocab *vocab = llama_model_get_vocab(h->model);
    int n = -llama_tokenize(vocab, text.c_str(), (int) text.size(), nullptr, 0, true, true);
    return n > 0 ? n : -1;
}

// Сброс KV-кэша (новый контекст с нуля).
extern "C" JNIEXPORT void JNICALL
Java_com_riglineaimstriker_junior_inference_LlamaBridge_reset(JNIEnv *, jobject, jlong handle) {
    if (auto *h = reinterpret_cast<Handle *>(handle)) reset_memory(h);
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
