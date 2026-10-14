package com.riglineaimstriker.junior.inference

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

sealed interface EngineState {
    data object Unloaded : EngineState
    data object Loading : EngineState
    data object Ready : EngineState
    data class Failed(val message: String) : EngineState
}

class InferenceException(val code: Int) : Exception(
    when (code) {
        CONTEXT_OVERFLOW -> "Диалог слишком длинный. Начните новый чат."
        else -> "Ошибка модели (код $code). Повторите запрос."
    }
) {
    companion object { const val CONTEXT_OVERFLOW = -2 }
}

/** События генерации: прогресс чтения промпта и новые куски текста (по одному на токен). */
sealed interface GenEvent {
    data class Prompt(val nPrompt: Int, val nReused: Int, val nCtx: Int, val nDone: Int) : GenEvent
    data class Token(val text: String) : GenEvent
}

interface InferenceEngine {
    val state: StateFlow<EngineState>
    suspend fun load(modelPath: String, nCtx: Int, threads: Int)
    fun generate(prompt: String, maxTokens: Int, temperature: Float = 0.3f): Flow<GenEvent>
    /** Точное число токенов в тексте или -1, если модель не загружена. */
    fun countTokens(text: String): Int
    /** Сбросить KV-кэш (следующий запрос будет посчитан с нуля). */
    suspend fun resetCache()
    fun cancel()
    fun reportFailure(message: String)
}

@Singleton
class LlamaCppEngine @Inject constructor() : InferenceEngine {
    private val _state = MutableStateFlow<EngineState>(EngineState.Unloaded)
    override val state: StateFlow<EngineState> = _state.asStateFlow()

    private val lock = Any()
    @Volatile private var handle = 0L
    private val genLock = Mutex()
    private val loadLock = Mutex()
    @Volatile private var loadedSig: String? = null

    override suspend fun load(modelPath: String, nCtx: Int, threads: Int) {
        loadLock.withLock {
            val sig = "$modelPath|${java.io.File(modelPath).lastModified()}|$nCtx"
            if (_state.value is EngineState.Ready && handle != 0L && loadedSig == sig) return@withLock
            withContext(Dispatchers.Default) {
                unloadInternal()
                _state.value = EngineState.Loading
                try {
                    val h = LlamaBridge.load(modelPath, nCtx, threads)
                    check(h != 0L) { "Не удалось открыть модель. Файл повреждён или не хватает памяти." }
                    handle = h
                    loadedSig = sig
                    _state.value = EngineState.Ready
                } catch (e: CancellationException) {
                    _state.value = EngineState.Unloaded
                    throw e
                } catch (e: UnsatisfiedLinkError) {
                    _state.value = EngineState.Failed("Не найдена нативная библиотека для этого устройства.")
                } catch (e: Throwable) {
                    _state.value = EngineState.Failed(e.message ?: "Не удалось загрузить модель.")
                }
            }
        }
    }

    private suspend fun unloadInternal() {
        loadedSig = null
        val h = synchronized(lock) { val x = handle; handle = 0L; x }
        if (h != 0L) {
            LlamaBridge.cancel(h)
            genLock.withLock { LlamaBridge.free(h) }
        }
    }

    override fun generate(prompt: String, maxTokens: Int, temperature: Float): Flow<GenEvent> =
        callbackFlow {
            val h = handle
            if (h == 0L) {
                close(IllegalStateException("Модель не загружена"))
                return@callbackFlow
            }
            val worker = launch(Dispatchers.Default) {
                genLock.withLock {
                    if (handle != h) {
                        close(IllegalStateException("Модель выгружена"))
                        return@withLock
                    }
                    val utf8 = Utf8Assembler()
                    val rc = try {
                        LlamaBridge.generate(
                            h, prompt.toByteArray(Charsets.UTF_8), maxTokens, temperature,
                            object : TokenCallback {
                                override fun onToken(bytes: ByteArray): Boolean =
                                    trySend(GenEvent.Token(utf8.push(bytes))).isSuccess

                                override fun onPrompt(nPrompt: Int, nReused: Int, nCtx: Int, nDone: Int) {
                                    trySend(GenEvent.Prompt(nPrompt, nReused, nCtx, nDone))
                                }
                            },
                        )
                    } catch (t: Throwable) {
                        close(t)
                        return@withLock
                    }
                    if (rc < 0) close(InferenceException(rc)) else close()
                }
            }
            awaitClose { if (worker.isActive) cancel() }
        }.buffer(Channel.UNLIMITED)

    override fun countTokens(text: String): Int {
        val h = handle
        if (h == 0L) return -1
        return try {
            LlamaBridge.countTokens(h, text.toByteArray(Charsets.UTF_8))
        } catch (t: Throwable) {
            -1
        }
    }

    override suspend fun resetCache() {
        val h = handle
        if (h == 0L) return
        LlamaBridge.cancel(h)
        genLock.withLock { if (handle == h) LlamaBridge.reset(h) }
    }

    override fun cancel() {
        synchronized(lock) { if (handle != 0L) LlamaBridge.cancel(handle) }
    }

    override fun reportFailure(message: String) {
        _state.value = EngineState.Failed(message)
    }
}
