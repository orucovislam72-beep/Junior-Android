package com.riglineaimstriker.junior.agent

/** Вызов инструмента, найденный в ответе модели. */
data class ToolCall(
    val name: String,
    val path: String,
    val query: String = "",
    val content: String = "",
    /** Текст ответа модели до конца тега включительно (то, что уйдёт обратно в историю). */
    val raw: String,
)

/**
 * Разбор простого формата инструментов:
 *   <tool name="read_file" path="a.kt"/>
 *   <tool name="write_file" path="a.kt">...содержимое...</tool>
 * Формат намеренно похож на XML, но разбирается терпимо: маленькая модель часто
 * ошибается в мелочах (одинарные кавычки, лишние ``` внутри write_file и т.п.).
 */
object ToolParser {
    val TOOLS = setOf("list_files", "read_file", "write_file", "create_dir", "search")

    private val OPEN = Regex("<tool\\s+([^>]*?)\\s*(/?)>", RegexOption.DOT_MATCHES_ALL)
    private val ATTR = Regex("(\\w+)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')")
    private const val CLOSE = "</tool>"

    /** Есть ли в тексте уже законченный вызов (можно останавливать генерацию). */
    fun isComplete(text: String): Boolean = parse(text, final = false) != null

    /**
     * @param final генерация закончилась: допускаем незакрытый тег у инструментов без содержимого.
     */
    fun parse(text: String, final: Boolean): ToolCall? {
        val m = OPEN.find(text) ?: return null
        val attrs = ATTR.findAll(m.groupValues[1]).associate { a ->
            a.groupValues[1].lowercase() to a.groupValues[2].ifEmpty { a.groupValues[3] }
        }
        val name = attrs["name"]?.trim()?.lowercase() ?: return null
        val selfClosing = m.groupValues[2] == "/"
        val path = attrs["path"]?.trim().orEmpty().ifEmpty { if (name == "list_files" || name == "search") "." else "" }
        val query = attrs["query"].orEmpty()
        if (name != "write_file") {
            // Инструменты без содержимого: хватает открывающего тега.
            val end = if (!selfClosing && text.startsWith(CLOSE, m.range.last + 1)) {
                m.range.last + 1 + CLOSE.length
            } else m.range.last + 1
            return ToolCall(name, path, query, raw = text.substring(0, end))
        }
        if (selfClosing) {
            return ToolCall(name, path, query, content = "", raw = text.substring(0, m.range.last + 1))
        }
        val bodyStart = m.range.last + 1
        val close = text.indexOf(CLOSE, bodyStart)
        if (close < 0 && !final) return null
        val bodyEnd = if (close < 0) text.length else close
        val content = cleanContent(text.substring(bodyStart, bodyEnd))
        val rawEnd = if (close < 0) text.length else close + CLOSE.length
        return ToolCall(name, path, query, content = content, raw = text.substring(0, rawEnd))
    }

    /** Убирает переносы по краям и обёртку ```lang ... ```, если модель её добавила. */
    fun cleanContent(body: String): String {
        var s = body.removePrefix("\r").removePrefix("\n")
        val t = s.trim()
        if (t.startsWith("```")) {
            val firstNl = t.indexOf('\n')
            val lastFence = t.lastIndexOf("```")
            if (firstNl in 0 until lastFence) s = t.substring(firstNl + 1, lastFence)
        }
        s = s.trimEnd(' ', '\t', '\r', '\n')
        return if (s.isEmpty()) s else s + "\n"
    }

    /** Текст ответа без тегов (для финального сообщения). */
    fun stripTags(text: String): String =
        text.replace(Regex("</?tool[^>]*>"), "").trim()
}

/** Строка диффа: ' ' без изменений, '-' удалена, '+' добавлена, '…' пропуск. */
data class DiffLine(val kind: Char, val text: String)

object LineDiff {
    /** Построчный дифф (LCS) с контекстом [context] строк. Для огромных файлов — упрощённо. */
    fun diff(old: String?, new: String, context: Int = 2, maxLines: Int = 400): List<DiffLine> {
        val b = new.removeSuffix("\n").split('\n')
        if (old == null) return b.take(maxLines).map { DiffLine('+', it) } +
            if (b.size > maxLines) listOf(DiffLine('…', "ещё ${b.size - maxLines} строк")) else emptyList()
        val a = old.removeSuffix("\n").split('\n')
        if (a.size * b.size > 400_000) {
            return listOf(DiffLine('…', "Файл большой: было ${a.size} строк, станет ${b.size}.")) +
                b.take(60).map { DiffLine('+', it) }
        }
        val n = a.size
        val m = b.size
        val lcs = Array(n + 1) { IntArray(m + 1) }
        for (i in n - 1 downTo 0) for (j in m - 1 downTo 0) {
            lcs[i][j] = if (a[i] == b[j]) lcs[i + 1][j + 1] + 1 else maxOf(lcs[i + 1][j], lcs[i][j + 1])
        }
        val full = ArrayList<DiffLine>()
        var i = 0
        var j = 0
        while (i < n && j < m) {
            when {
                a[i] == b[j] -> { full += DiffLine(' ', a[i]); i++; j++ }
                lcs[i + 1][j] >= lcs[i][j + 1] -> { full += DiffLine('-', a[i]); i++ }
                else -> { full += DiffLine('+', b[j]); j++ }
            }
        }
        while (i < n) full += DiffLine('-', a[i++])
        while (j < m) full += DiffLine('+', b[j++])
        // Оставляем только изменения и немного контекста вокруг.
        val keep = BooleanArray(full.size)
        full.forEachIndexed { k, d ->
            if (d.kind != ' ') for (x in (k - context).coerceAtLeast(0)..(k + context).coerceAtMost(full.size - 1)) keep[x] = true
        }
        if (keep.none { it }) return listOf(DiffLine('…', "Содержимое не изменилось."))
        val out = ArrayList<DiffLine>()
        var skipped = 0
        full.forEachIndexed { k, d ->
            if (keep[k]) {
                if (skipped > 0) { out += DiffLine('…', "$skipped строк без изменений"); skipped = 0 }
                out += d
            } else skipped++
        }
        if (skipped > 0) out += DiffLine('…', "$skipped строк без изменений")
        return if (out.size > maxLines) out.take(maxLines) + DiffLine('…', "дифф обрезан") else out
    }
}

/** Выполнение инструментов над рабочей папкой. Возвращает текст результата для модели. */
class ToolRunner(private val ws: Workspace) {

    fun run(call: ToolCall): String = when (call.name) {
        "list_files" -> {
            val items = ws.list(call.path, depth = 3)
            if (items.isEmpty()) "Папка «${WorkspacePaths.normalize(call.path)}» пуста."
            else items.joinToString("\n") + if (items.size >= 200) "\n…(список обрезан)" else ""
        }
        "read_file" -> {
            require(call.path.isNotBlank()) { "Не указан path." }
            val text = ws.readOrNull(call.path)
                ?: throw IllegalArgumentException("Файл «${WorkspacePaths.normalize(call.path)}» не найден.")
            if (text.length > MAX_READ) {
                text.take(MAX_READ) + "\n…[файл обрезан: показано $MAX_READ из ${text.length} символов]"
            } else text.ifEmpty { "(файл пустой)" }
        }
        "write_file" -> {
            require(call.path.isNotBlank()) { "Не указан path." }
            ws.write(call.path, call.content)
            "Файл ${WorkspacePaths.normalize(call.path)} записан, строк: ${lineCount(call.content)}."
        }
        "create_dir" -> {
            require(call.path.isNotBlank()) { "Не указан path." }
            ws.mkdirs(call.path)
            "Папка ${WorkspacePaths.normalize(call.path)} создана."
        }
        "search" -> {
            require(call.query.isNotBlank()) { "Не указан query." }
            search(call.path, call.query)
        }
        else -> throw IllegalArgumentException(
            "Неизвестный инструмент «${call.name}». Доступны: ${ToolParser.TOOLS.joinToString()}."
        )
    }

    private fun search(path: String, query: String): String {
        val hits = ArrayList<String>()
        for (f in ws.files(path)) {
            if (hits.size >= 30) break
            val text = runCatching { ws.readOrNull(f) }.getOrNull() ?: continue
            if (text.indexOf('\u0000') >= 0) continue
            text.lineSequence().forEachIndexed { idx, line ->
                if (hits.size < 30 && line.contains(query, ignoreCase = true)) {
                    hits += "$f:${idx + 1}: ${line.trim().take(160)}"
                }
            }
        }
        return if (hits.isEmpty()) "Ничего не найдено по «$query»." else hits.joinToString("\n")
    }

    companion object {
        const val MAX_READ = 6000

        fun lineCount(text: String): Int = if (text.isEmpty()) 0 else text.trimEnd('\n').split('\n').size
    }
}
