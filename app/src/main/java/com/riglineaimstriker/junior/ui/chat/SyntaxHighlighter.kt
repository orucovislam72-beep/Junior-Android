package com.riglineaimstriker.junior.ui.chat

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import com.riglineaimstriker.junior.util.CodeLang

/**
 * Лёгкая подсветка синтаксиса без зависимостей: один проход по тексту, раскраска
 * ключевых слов, строк, комментариев, чисел, типов, аннотаций и вызовов функций.
 * Поддержаны kotlin, java, python, javascript, typescript, c, cpp, sql, bash, json.
 */
object SyntaxHighlighter {

    data class Palette(
        val keyword: Color,
        val string: Color,
        val comment: Color,
        val number: Color,
        val type: Color,
        val annotation: Color,
        val function: Color,
    )

    // Палитра «Минеральная мастерская»: приглушённые природные тона без фиолетового.
    // Контраст на фоне кода (#29352E ночью / #E8ECE4 днём) — не ниже 4.9:1 (WCAG AA).
    val DarkPalette = Palette(
        keyword = Color(0xFFA6CBB4), string = Color(0xFFE2C08D), comment = Color(0xFFA3B0A7),
        number = Color(0xFFF0A58F), type = Color(0xFFA9C6DE), annotation = Color(0xFFD9CF8F),
        function = Color(0xFF9FD0D9),
    )

    val LightPalette = Palette(
        keyword = Color(0xFF2F5D46), string = Color(0xFF7A4A12), comment = Color(0xFF5B675F),
        number = Color(0xFF8A3B2E), type = Color(0xFF2F5470), annotation = Color(0xFF655A1A),
        function = Color(0xFF1F5566),
    )

    private class Spec(
        val keywords: Set<String>,
        val lineComments: List<String> = listOf("//"),
        val blockComment: Pair<String, String>? = "/*" to "*/",
        val quotes: String = "\"'",
        val tripleQuotes: Boolean = false,
        val annotations: Boolean = false,
        val caseInsensitive: Boolean = false,
        val dollarVars: Boolean = false,
        val typesByCase: Boolean = true,
        val jsonKeys: Boolean = false,
    )

    private fun words(s: String): Set<String> = s.split(' ').filter { it.isNotBlank() }.toSet()

    private val C_KW = words(
        "auto break case char const continue default do double else enum extern float for goto if " +
            "inline int long register restrict return short signed sizeof static struct switch typedef " +
            "union unsigned void volatile while bool true false NULL size_t uint8_t uint16_t uint32_t " +
            "uint64_t int8_t int16_t int32_t int64_t #include #define #ifdef #ifndef #endif #if #else #pragma",
    )
    private val CPP_KW = C_KW + words(
        "class namespace template typename public private protected virtual override final new delete " +
            "this nullptr using try catch throw operator friend explicit constexpr noexcept mutable " +
            "static_cast dynamic_cast reinterpret_cast const_cast std string vector auto co_await co_return",
    )

    private val specs: Map<String, Spec> = mapOf(
        "kotlin" to Spec(
            words(
                "package import class interface object fun val var if else when for while do return break " +
                    "continue in is as try catch finally throw null true false this super typealias " +
                    "companion data sealed enum abstract open override private protected public internal " +
                    "lateinit inline suspend operator infix const by init constructor vararg out reified " +
                    "annotation inner tailrec where it",
            ),
            tripleQuotes = true, annotations = true,
        ),
        "java" to Spec(
            words(
                "package import class interface enum extends implements new return if else for while do " +
                    "switch case default break continue try catch finally throw throws null true false this " +
                    "super static final abstract public private protected void int long short byte char " +
                    "boolean float double synchronized volatile transient native instanceof var record " +
                    "sealed permits yield assert",
            ),
            annotations = true,
        ),
        "python" to Spec(
            words(
                "def class return if elif else for while in not and or is None True False import from as " +
                    "try except finally raise with lambda yield pass break continue global nonlocal async " +
                    "await del assert self print len range",
            ),
            lineComments = listOf("#"), blockComment = null, tripleQuotes = true, annotations = true,
        ),
        "javascript" to Spec(
            words(
                "var let const function return if else for while do switch case default break continue " +
                    "new delete typeof instanceof in of try catch finally throw class extends super this " +
                    "null undefined true false import export from as async await yield static get set void",
            ),
            quotes = "\"'`",
        ),
        "typescript" to Spec(
            words(
                "var let const function return if else for while do switch case default break continue " +
                    "new delete typeof instanceof in of try catch finally throw class extends implements " +
                    "super this null undefined true false import export from as async await yield static " +
                    "get set void interface type enum namespace declare public private protected readonly " +
                    "abstract keyof any unknown never string number boolean",
            ),
            quotes = "\"'`", annotations = true,
        ),
        "c" to Spec(C_KW),
        "cpp" to Spec(CPP_KW),
        "sql" to Spec(
            words(
                "select from where and or not insert into values update set delete create table drop alter " +
                    "add column index primary key foreign references join left right inner outer full on " +
                    "group by order having limit offset as distinct union all null is in exists between like " +
                    "case when then else end count sum avg min max default unique check constraint view " +
                    "begin commit rollback transaction integer int text varchar boolean date timestamp asc desc",
            ),
            lineComments = listOf("--"), quotes = "'\"", caseInsensitive = true, typesByCase = false,
        ),
        "bash" to Spec(
            words(
                "if then else elif fi for while until do done case esac in function return exit local " +
                    "export echo cd ls rm cp mv mkdir cat grep sed awk sudo apt pkg git source set unset",
            ),
            lineComments = listOf("#"), blockComment = null, dollarVars = true, typesByCase = false,
        ),
        "json" to Spec(
            words("true false null"),
            lineComments = emptyList(), blockComment = null, quotes = "\"", typesByCase = false, jsonKeys = true,
        ),
    )

    fun isSupported(lang: String) = specs.containsKey(CodeLang.normalize(lang))

    fun highlight(code: String, lang: String, p: Palette): AnnotatedString {
        val spec = specs[CodeLang.normalize(lang)] ?: return AnnotatedString(code)
        val b = AnnotatedString.Builder(code)
        val n = code.length
        var i = 0
        fun style(color: Color, from: Int, to: Int, italic: Boolean = false) {
            if (to > from) {
                b.addStyle(
                    SpanStyle(color = color, fontStyle = if (italic) FontStyle.Italic else null),
                    from, to.coerceAtMost(n),
                )
            }
        }
        while (i < n) {
            val c = code[i]
            // Блочный комментарий
            val bc = spec.blockComment
            if (bc != null && code.startsWith(bc.first, i)) {
                val end = code.indexOf(bc.second, i + bc.first.length)
                val stop = if (end < 0) n else end + bc.second.length
                style(p.comment, i, stop, italic = true); i = stop; continue
            }
            // Строчный комментарий
            if (spec.lineComments.any { code.startsWith(it, i) }) {
                val end = code.indexOf('\n', i).let { if (it < 0) n else it }
                style(p.comment, i, end, italic = true); i = end; continue
            }
            // Тройные кавычки
            if (spec.tripleQuotes && (code.startsWith("\"\"\"", i) || code.startsWith("'''", i))) {
                val q = code.substring(i, i + 3)
                val end = code.indexOf(q, i + 3)
                val stop = if (end < 0) n else end + 3
                style(p.string, i, stop); i = stop; continue
            }
            // Строки
            if (spec.quotes.indexOf(c) >= 0) {
                var j = i + 1
                while (j < n && code[j] != c) {
                    if (code[j] == '\\') j++
                    else if (code[j] == '\n' && c != '`') break
                    j++
                }
                val stop = (j + 1).coerceAtMost(n)
                var isKey = false
                if (spec.jsonKeys) {
                    var k = stop
                    while (k < n && (code[k] == ' ' || code[k] == '\t')) k++
                    isKey = k < n && code[k] == ':'
                }
                style(if (isKey) p.type else p.string, i, stop); i = stop; continue
            }
            // Аннотации/декораторы
            if (spec.annotations && c == '@' && i + 1 < n && code[i + 1].isLetter()) {
                var j = i + 1
                while (j < n && (code[j].isLetterOrDigit() || code[j] == '_' || code[j] == '.')) j++
                style(p.annotation, i, j); i = j; continue
            }
            // Переменные bash: $VAR, ${VAR}, $1
            if (spec.dollarVars && c == '$' && i + 1 < n) {
                var j = i + 1
                if (code[j] == '{') {
                    val end = code.indexOf('}', j)
                    j = if (end < 0) n else end + 1
                } else {
                    while (j < n && (code[j].isLetterOrDigit() || code[j] == '_')) j++
                }
                style(p.annotation, i, j); i = j; continue
            }
            // Числа
            if (c.isDigit() && (i == 0 || !isIdent(code[i - 1]))) {
                var j = i + 1
                while (j < n && (code[j].isLetterOrDigit() || code[j] == '.' || code[j] == '_')) j++
                style(p.number, i, j); i = j; continue
            }
            // Идентификаторы и ключевые слова (в C директивы вида #include тоже)
            if (isIdentStart(c) || (c == '#' && spec.keywords.contains("#include"))) {
                var j = i + 1
                while (j < n && isIdent(code[j])) j++
                val word = code.substring(i, j)
                val kw = if (spec.caseInsensitive) spec.keywords.contains(word.lowercase())
                else spec.keywords.contains(word)
                when {
                    kw -> style(p.keyword, i, j)
                    nextNonSpace(code, j) == '(' -> style(p.function, i, j)
                    spec.typesByCase && word[0].isUpperCase() && word.length > 1 -> style(p.type, i, j)
                }
                i = j; continue
            }
            i++
        }
        return b.toAnnotatedString()
    }

    private fun isIdentStart(c: Char) = c.isLetter() || c == '_'
    private fun isIdent(c: Char) = c.isLetterOrDigit() || c == '_'

    private fun nextNonSpace(s: String, from: Int): Char? {
        var k = from
        while (k < s.length && s[k] == ' ') k++
        return if (k < s.length) s[k] else null
    }
}
