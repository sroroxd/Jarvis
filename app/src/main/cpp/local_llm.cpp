#include <jni.h>
#include "llama.h"
#include <algorithm>
#include <atomic>
#include <cstdint>
#include <cstring>
#include <limits>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <string>
#include <vector>

namespace {
struct Engine {
    std::atomic<bool> cancelled{false};
    bool token_limit_reached = false; // Read only by the serialized Java worker.
    llama_model *model = nullptr;
    llama_context *context = nullptr;
    ~Engine() {
        if (context) llama_free(context);
        if (model) llama_model_free(model);
    }
};

Engine *engine(jlong value) {
    return reinterpret_cast<Engine *>(static_cast<intptr_t>(value));
}

bool abortDecode(void *opaque) {
    return static_cast<Engine *>(opaque)->cancelled.load(std::memory_order_relaxed);
}

// llama may invoke this on its own loader threads: atomic state only, never JNI.
bool loadProgress(float, void *opaque) {
    return !abortDecode(opaque);
}

// Do not log prompts, generated text, paths, or model metadata through llama's logger.
void discardLog(ggml_log_level, const char *, void *) {}

void fail(JNIEnv *env, const char *message) {
    if (env->ExceptionCheck()) return; // In particular, preserve listener exceptions.
    jclass type = env->FindClass("java/lang/IllegalStateException");
    if (type) env->ThrowNew(type, message); // Error strings below are ASCII/modified-UTF8 safe.
}

std::string bytes(JNIEnv *env, jbyteArray value) {
    if (!value) throw std::runtime_error("Text oder Dateipfad fehlt.");
    const jsize length = env->GetArrayLength(value);
    std::string result(static_cast<size_t>(length), '\0');
    if (length) env->GetByteArrayRegion(value, 0, length, reinterpret_cast<jbyte *>(result.data()));
    if (env->ExceptionCheck()) throw std::runtime_error("Text konnte nicht gelesen werden.");
    return result;
}

// Decode real UTF-8 to UTF-16, including supplementary characters (emoji).
// In streaming mode retain an incomplete trailing sequence for the next token.
jstring utf16(JNIEnv *env, const std::string &input, size_t &consumed, bool final) {
    std::vector<jchar> out;
    size_t i = 0;
    while (i < input.size()) {
        const uint8_t lead = static_cast<uint8_t>(input[i]);
        uint32_t cp;
        size_t n;
        if (lead < 0x80) { cp = lead; n = 1; }
        else if (lead >= 0xc2 && lead <= 0xdf) { cp = lead & 0x1f; n = 2; }
        else if (lead >= 0xe0 && lead <= 0xef) { cp = lead & 0x0f; n = 3; }
        else if (lead >= 0xf0 && lead <= 0xf4) { cp = lead & 0x07; n = 4; }
        else { out.push_back(0xfffd); ++i; continue; }
        if (i + n > input.size()) {
            if (!final) break;
            out.push_back(0xfffd);
            i = input.size();
            break;
        }
        bool valid = true;
        for (size_t k = 1; k < n; ++k) {
            const uint8_t next = static_cast<uint8_t>(input[i + k]);
            if ((next & 0xc0) != 0x80) { valid = false; break; }
            cp = (cp << 6) | (next & 0x3f);
        }
        if (!valid || (n == 2 && cp < 0x80) || (n == 3 && cp < 0x800) ||
            (n == 4 && cp < 0x10000) || cp > 0x10ffff || (cp >= 0xd800 && cp <= 0xdfff)) {
            out.push_back(0xfffd); ++i; continue;
        }
        if (cp <= 0xffff) out.push_back(static_cast<jchar>(cp));
        else {
            cp -= 0x10000;
            out.push_back(static_cast<jchar>(0xd800 + (cp >> 10)));
            out.push_back(static_cast<jchar>(0xdc00 + (cp & 0x3ff)));
        }
        i += n;
    }
    consumed = i;
    // JNI accepts a null pointer only ambiguously across implementations; use a sentinel.
    const jchar empty = 0;
    return env->NewString(out.empty() ? &empty : out.data(), static_cast<jsize>(out.size()));
}

bool emit(JNIEnv *env, jobject listener, jmethodID method, std::string &pending, bool final) {
    if (!listener || pending.empty()) return true;
    size_t consumed = 0;
    jstring text = utf16(env, pending, consumed, final);
    if (!text) return false;
    if (consumed != 0) env->CallVoidMethod(listener, method, text);
    env->DeleteLocalRef(text);
    pending.erase(0, consumed);
    return !env->ExceptionCheck();
}
} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_de_art_jarvis_LocalLlmEngine_nativeCreate(JNIEnv *env, jclass) {
    try {
        static std::once_flag initialized;
        std::call_once(initialized, [] { llama_log_set(discardLog, nullptr); llama_backend_init(); });
        return static_cast<jlong>(reinterpret_cast<intptr_t>(new Engine()));
    } catch (const std::exception &e) { fail(env, e.what()); }
    catch (...) { fail(env, "Lokaler Modellzustand konnte nicht angelegt werden."); }
    return 0;
}

extern "C" JNIEXPORT void JNICALL
Java_de_art_jarvis_LocalLlmEngine_nativeLoadInto(JNIEnv *env, jclass, jlong handle,
                                               jbyteArray path, jint threads, jint contextTokens) {
    try {
        Engine &state = *engine(handle); // Published before loading; freed by Java after this call.
        if (abortDecode(&state)) throw std::runtime_error("Modellladung abgebrochen.");
        const std::string file = bytes(env, path);
        if (file.find('\0') != std::string::npos) throw std::runtime_error("Ungueltiger GGUF-Dateipfad.");
        auto mp = llama_model_default_params();
        mp.n_gpu_layers = 0;
        mp.progress_callback = loadProgress;
        mp.progress_callback_user_data = &state;
        state.model = llama_model_load_from_file(file.c_str(), mp);
        if (abortDecode(&state)) throw std::runtime_error("Modellladung abgebrochen.");
        if (!state.model) throw std::runtime_error("GGUF-Modell konnte nicht geladen werden. Datei und freien Speicher pruefen.");
        // The Java prompt builder currently implements Qwen2.x ChatML only.
        // Reject incompatible imports before allocating a context/accepting the model.
        char architecture[32] = {};
        const int architecture_length = llama_model_meta_val_str(
            state.model, "general.architecture", architecture, sizeof(architecture));
        const char *chat_template = llama_model_chat_template(state.model, nullptr);
        if (architecture_length != 5 || std::string(architecture, 5) != "qwen2" ||
            !chat_template || !std::strstr(chat_template, "<|im_start|>") ||
            !std::strstr(chat_template, "<|im_end|>")) {
            throw std::runtime_error("Der lokale Modus unterstützt derzeit Qwen2.x-Instruct-Modelle mit ChatML.");
        }
        auto cp = llama_context_default_params();
        cp.n_ctx = static_cast<uint32_t>(contextTokens);
        cp.n_batch = std::min(contextTokens, 256);
        cp.n_ubatch = std::min(contextTokens, 128);
        cp.n_threads = threads;
        cp.n_threads_batch = threads;
        cp.offload_kqv = false;
        cp.op_offload = false;
        cp.abort_callback = abortDecode;
        cp.abort_callback_data = &state;
        if (abortDecode(&state)) throw std::runtime_error("Modellladung abgebrochen.");
        state.context = llama_init_from_model(state.model, cp);
        if (abortDecode(&state)) throw std::runtime_error("Modellladung abgebrochen.");
        if (!state.context) throw std::runtime_error("Modellkontext konnte nicht angelegt werden. Weniger Kontext oder kleineres Modell verwenden.");
    } catch (const std::exception &e) { fail(env, e.what()); }
    catch (...) { fail(env, "Unbekannter Fehler beim Laden der lokalen KI."); }
}

extern "C" JNIEXPORT jstring JNICALL
Java_de_art_jarvis_LocalLlmEngine_nativeGenerate(JNIEnv *env, jclass, jlong handle,
                                                jbyteArray prompt, jint maxTokens, jobject listener) {
    try {
        Engine &state = *engine(handle); // Lifetime protected by Java operations/handleLock.
        state.token_limit_reached = false;
        jmethodID method = nullptr;
        if (listener) {
            jclass type = env->GetObjectClass(listener);
            if (!type) return nullptr;
            method = env->GetMethodID(type, "onToken", "(Ljava/lang/String;)V");
            env->DeleteLocalRef(type);
            if (!method) return nullptr;
        }
        const std::string text = bytes(env, prompt);
        const llama_vocab *vocab = llama_model_get_vocab(state.model);
        int count = llama_tokenize(vocab, text.data(), static_cast<int32_t>(text.size()), nullptr, 0, true, true);
        if (count == std::numeric_limits<int32_t>::min()) throw std::runtime_error("Die Anfrage ist zu lang.");
        if (count < 0) count = -count;
        if (count == 0 || static_cast<uint32_t>(count) >= llama_n_ctx(state.context))
            throw std::runtime_error("Die Anfrage passt nicht in den Modellkontext. Gespraech kuerzen oder neu beginnen.");
        std::vector<llama_token> tokens(static_cast<size_t>(count));
        count = llama_tokenize(vocab, text.data(), static_cast<int32_t>(text.size()), tokens.data(), count, true, true);
        if (count < 1) throw std::runtime_error("Die Anfrage konnte nicht tokenisiert werden.");
        llama_memory_clear(llama_get_memory(state.context), true);
        std::unique_ptr<llama_sampler, decltype(&llama_sampler_free)> sampler(llama_sampler_init_greedy(), llama_sampler_free);
        if (!sampler) throw std::runtime_error("Antwortgenerator konnte nicht angelegt werden.");
        std::string output, pending;
        for (int offset = 0; offset < count && !abortDecode(&state);) {
            const int chunk = std::min(256, count - offset);
            const int status = llama_decode(state.context, llama_batch_get_one(tokens.data() + offset, chunk));
            if (status != 0) {
                if (abortDecode(&state)) break;
                throw std::runtime_error("Die lokale KI konnte die Anfrage nicht verarbeiten.");
            }
            offset += chunk;
        }
        const int budget = std::min<int>(maxTokens, static_cast<int>(llama_n_ctx(state.context)) - count);
        int generated = 0;
        bool ended_by_eog = false;
        for (int step = 0; step < budget && !abortDecode(&state); ++step) {
            llama_token token = llama_sampler_sample(sampler.get(), state.context, -1);
            if (llama_vocab_is_eog(vocab, token)) { ended_by_eog = true; break; }
            std::vector<char> piece(128);
            int length = llama_token_to_piece(vocab, token, piece.data(), static_cast<int>(piece.size()), 0, false);
            if (length < 0) {
                piece.resize(static_cast<size_t>(-length));
                length = llama_token_to_piece(vocab, token, piece.data(), static_cast<int>(piece.size()), 0, false);
            }
            if (length < 0) throw std::runtime_error("Ein Antworttoken konnte nicht gelesen werden.");
            output.append(piece.data(), static_cast<size_t>(length));
            pending.append(piece.data(), static_cast<size_t>(length));
            ++generated;
            if (!emit(env, listener, method, pending, false)) return nullptr;
            if (abortDecode(&state) || step + 1 == budget) break;
            const int status = llama_decode(state.context, llama_batch_get_one(&token, 1));
            if (status != 0) {
                if (abortDecode(&state)) break;
                throw std::runtime_error("Die lokale Antwort wurde wegen eines Rechenfehlers beendet.");
            }
        }
        state.token_limit_reached = !ended_by_eog && !abortDecode(&state) && generated >= budget;
        if (!emit(env, listener, method, pending, true)) return nullptr;
        size_t consumed;
        return utf16(env, output, consumed, true);
    } catch (const std::exception &e) { fail(env, e.what()); }
    catch (...) { fail(env, "Unbekannter Fehler bei der lokalen Antwort."); }
    return nullptr;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_de_art_jarvis_LocalLlmEngine_nativeTokenLimitReached(JNIEnv *, jclass, jlong handle) {
    return engine(handle)->token_limit_reached ? JNI_TRUE : JNI_FALSE;
}
extern "C" JNIEXPORT void JNICALL
Java_de_art_jarvis_LocalLlmEngine_nativeCancel(JNIEnv *, jclass, jlong handle) {
    engine(handle)->cancelled.store(true, std::memory_order_relaxed);
}
extern "C" JNIEXPORT void JNICALL
Java_de_art_jarvis_LocalLlmEngine_nativeClose(JNIEnv *, jclass, jlong handle) {
    delete engine(handle);
}
