package com.riglineaimstriker.junior.inference

fun interface TokenCallback {
    /** Возвращает false, чтобы остановить генерацию. */
    fun onToken(bytes: ByteArray): Boolean
}

object LlamaBridge {
    init { System.loadLibrary("junior_jni") }

    external fun load(path: String, nCtx: Int, nThreads: Int): Long
    external fun generate(
        handle: Long, prompt: ByteArray, maxTokens: Int, temperature: Float, cb: TokenCallback,
    ): Int
    external fun cancel(handle: Long)
    external fun free(handle: Long)
}

/** Склеивает байты токенов в строки, не разрезая многобайтные символы. */
class Utf8Assembler {
    private var pending = ByteArray(0)

    fun push(chunk: ByteArray): String {
        val all = pending + chunk
        val cut = completePrefix(all)
        pending = all.copyOfRange(cut, all.size)
        return String(all, 0, cut, Charsets.UTF_8)
    }

    private fun completePrefix(a: ByteArray): Int {
        var i = a.size - 1
        var back = 0
        while (i >= 0 && back < 3) {
            val c = a[i].toInt() and 0xFF
            if ((c and 0xC0) == 0x80) { i--; back++; continue }
            val need = when {
                c >= 0xF0 -> 4
                c >= 0xE0 -> 3
                c >= 0xC0 -> 2
                else -> 1
            }
            return if (a.size - i >= need) a.size else i
        }
        return a.size
    }
}
