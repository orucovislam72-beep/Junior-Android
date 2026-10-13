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

interface InferenceEngine {
    val state: StateFlow<EngineState>
    suspend fun load(modelPath: String, nCtx: Int, threads: Int)
    fun generate(prompt: String, maxTokens: Int, temperature: Float = 0.3f): Flow<String>
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

    override suspend fun load(modelPath: String, nCtx: Int, threads: Int) {
        loadLock.withLock {
            withContext(Dispatchers.Default) {
                unloadInternal()
                _state.value = EngineState.Loading
                try {
                    val h = LlamaBridge.load(modelPath, nCtx, threads)
                    check(h != 0L) { "Не удалось открыть модель. Файл повреждён или не хватает памяти." }
                    handle = h
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
        val h = synchronized(lock) { val x = handle; handle = 0L; x }
        if (h != 0L) {
            LlamaBridge.cancel(h)
            genLock.withLock { LlamaBridge.free(h) }
        }
    }

    override fun generate(prompt: String, maxTokens: Int, temperature: Float): Flow<String> =
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
                            TokenCallback { bytes ->
                                val s = utf8.push(bytes)
                                s.isEmpty() || trySend(s).isSuccess
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

    override fun cancel() {
        synchronized(lock) { if (handle != 0L) LlamaBridge.cancel(handle) }
    }

    override fun reportFailure(message: String) {
        _state.value = EngineState.Failed(message)
    }
}
