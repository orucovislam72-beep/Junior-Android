package com.riglineaimstriker.junior.skills

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * Навыки из Rigline: маленькие инструкции, которые подмешиваются к запросу.
 * Лимиты как в Rigline: до 5 активных, 1500 символов на навык, 3500 на все вместе.
 * Выключенный навык ничего не стоит.
 */
@Singleton
class SkillStore @Inject constructor(@ApplicationContext ctx: Context) {

    private val file = File(ctx.filesDir, "skills.json")
    private var custom: List<Skill> = emptyList()
    private var activeNames: List<String> = DEFAULT_ACTIVE

    private val _skills = MutableStateFlow<List<Skill>>(emptyList())
    val skills: StateFlow<List<Skill>> = _skills.asStateFlow()

    init {
        load()
        publish()
    }

    private fun load() {
        runCatching {
            val o = JSONObject(file.readText())
            o.optJSONArray("active")?.let { a ->
                activeNames = List(a.length()) { a.getString(it) }
            }
            o.optJSONArray("custom")?.let { a ->
                custom = List(a.length()) { i ->
                    val c = a.getJSONObject(i)
                    Skill(
                        name = c.getString("name"),
                        description = c.optString("description"),
                        body = c.getString("body"),
                        builtin = false,
                    )
                }
            }
        }
    }

    private fun allSkills(): List<Skill> = BUILTIN + custom

    private fun publish() {
        val all = allSkills()
        activeNames = activeNames.filter { n -> all.any { it.name == n } }.distinct().take(MAX_ACTIVE)
        _skills.value = all.map { it.copy(active = it.name in activeNames) }
    }

    private fun persist() {
        runCatching {
            val o = JSONObject()
            o.put("active", JSONArray(activeNames))
            val arr = JSONArray()
            custom.forEach { s ->
                arr.put(
                    JSONObject().apply {
                        put("name", s.name)
                        put("description", s.description)
                        put("body", s.body)
                    }
                )
            }
            o.put("custom", arr)
            file.writeText(o.toString())
        }
    }

    /** Включить или выключить навык. Возвращает текст ошибки или null. */
    @Synchronized
    fun toggle(name: String): String? {
        val on = name !in activeNames
        if (on && activeNames.size >= MAX_ACTIVE) {
            return "Можно включить не больше $MAX_ACTIVE навыков одновременно."
        }
        activeNames = if (on) activeNames + name else activeNames - name
        publish()
        persist()
        return null
    }

    /** Добавить свой навык (или заменить свой с тем же именем). Возвращает текст ошибки или null. */
    @Synchronized
    fun add(name: String, description: String, body: String): String? {
        val n = name.trim()
        val b = body.trim()
        if (n.isEmpty()) return "Введите название."
        if (n.length > MAX_NAME_CHARS) return "Название длиннее $MAX_NAME_CHARS символов."
        if (b.isEmpty()) return "Введите текст навыка."
        if (b.length > MAX_FILE_CHARS) return "Слишком длинный навык (максимум $MAX_FILE_CHARS символов)."
        if (BUILTIN.any { it.name == n }) return "Это имя занято встроенным навыком."
        val skill = Skill(n, description.trim(), b, builtin = false)
        custom = custom.filter { it.name != n } + skill
        publish()
        persist()
        return null
    }

    /** Добавить навык из текста .md-файла (необязательная шапка --- name/description ---). */
    fun importText(text: String, fallbackName: String): String? {
        val (meta, body) = parseFront(text)
        val name = meta["name"]?.takeIf { it.isNotBlank() } ?: fallbackName
        return add(name, meta["description"].orEmpty(), body)
    }

    @Synchronized
    fun remove(name: String) {
        custom = custom.filter { it.name != name }
        activeNames = activeNames - name
        publish()
        persist()
    }

    /** Блок инструкций для активных навыков, ограниченный по размеру. Пусто, если ничего не включено. */
    @Synchronized
    fun activePrompt(maxChars: Int): String {
        val all = allSkills()
        var budget = min(maxChars, MAX_TOTAL_CHARS)
        val parts = ArrayList<String>()
        for (n in activeNames.take(MAX_ACTIVE)) {
            val s = all.firstOrNull { it.name == n } ?: continue
            if (budget <= 0) break
            val limit = min(MAX_SKILL_CHARS, budget)
            var body = s.body
            if (body.length > limit) body = body.take(limit).trimEnd() + " …[обрезано]"
            budget -= body.length
            parts += "## ${s.name}\n$body"
        }
        if (parts.isEmpty()) return ""
        return "Включённые навыки (обязательные правила пользователя, соблюдай их в каждом ответе):\n\n" +
            parts.joinToString("\n\n")
    }

    companion object {
        const val MAX_ACTIVE = 5
        const val MAX_SKILL_CHARS = 1500
        const val MAX_TOTAL_CHARS = 3500
        const val MAX_FILE_CHARS = 20_000
        const val MAX_NAME_CHARS = 40

        private val DEFAULT_ACTIVE = listOf("Кратко")
        private val FRONT = Regex("^---\\s*\\n(.*?)\\n---\\s*\\n?(.*)$", RegexOption.DOT_MATCHES_ALL)

        /** Делит необязательную шапку и тело. Не бросает исключений. */
        fun parseFront(text: String): Pair<Map<String, String>, String> {
            val t = text.trimStart('\uFEFF')
            val m = FRONT.find(t) ?: return emptyMap<String, String>() to t.trim()
            val meta = HashMap<String, String>()
            m.groupValues[1].lines().forEach { line ->
                val i = line.indexOf(':')
                if (i > 0) {
                    meta[line.substring(0, i).trim().lowercase()] =
                        line.substring(i + 1).trim().trim('"', '\'')
                }
            }
            return meta to m.groupValues[2].trim()
        }

        val BUILTIN: List<Skill> = listOf(
            Skill(
                "Кратко", "Короткие ответы без воды",
                "КРАТКО: никаких вступлений и пересказа вопроса. Сначала код или ответ, " +
                    "затем не больше 2 строк пояснения. Списки — только если без них непонятно.",
                builtin = true,
            ),
            Skill(
                "Ревью кода", "Строгое ревью без переписывания",
                "РЕВЬЮ: не переписывай код целиком. Дай нумерованный список проблем, самые серьёзные " +
                    "первыми: баги, безопасность, граничные случаи, производительность. Для каждой: " +
                    "где (функция/строка) → что не так → как исправить (одна строка или короткий фрагмент). " +
                    "Если проблем нет — так и напиши.",
                builtin = true,
            ),
            Skill(
                "Минимум правок", "Только нужные изменения, без рефакторинга",
                "МИНИМУМ ПРАВОК: меняй только то, что нужно для задачи. Не переименовывай, " +
                    "не переформатируй, не «улучшай» попутно. Сохраняй стиль и имена исходника. " +
                    "Показывай только изменённые функции целиком и где они находятся.",
                builtin = true,
            ),
            Skill(
                "Сначала тест", "Сначала тест, потом код",
                "TDD: сначала тест, который падает без изменения, затем минимальная реализация, " +
                    "с которой он проходит. Используй стандартный фреймворк языка " +
                    "(pytest, JUnit, Jest и т.п.) и укажи команду запуска.",
                builtin = true,
            ),
            Skill(
                "Комментарии по-русски", "Комментарии и коммиты на русском",
                "КОММЕНТАРИИ ПО-РУССКИ: комментарии, докстринги и сообщения коммитов пиши по-русски. " +
                    "Имена переменных, функций и классов — по-английски.",
                builtin = true,
            ),
            Skill(
                "Объясняй просто", "Объяснения как для новичка",
                "ПРОСТО: объясняй как новичку. Короткие предложения, каждый термин расшифровывай " +
                    "при первом упоминании, дай маленький пример и аналогию из жизни.",
                builtin = true,
            ),
            Skill(
                "Разбор ошибки", "Чтение стектрейсов и сообщений об ошибках",
                "РАЗБОР ОШИБКИ: 1) что означает ошибка простыми словами; 2) вероятные причины, " +
                    "самая частая первой; 3) где искать (файл/строка из стектрейса); 4) исправление кодом. " +
                    "Если данных мало — попроси конкретный кусок кода или лога.",
                builtin = true,
            ),
            Skill(
                "SQL", "Точные и безопасные SQL-запросы",
                "SQL: пиши стандартный SQL; если важен диалект (PostgreSQL/MySQL/SQLite) — уточни или укажи, " +
                    "под какой написано. Только параметризованные запросы, никаких UPDATE/DELETE без WHERE. " +
                    "Одной строкой поясни, что делает запрос; для тяжёлых запросов предложи индекс.",
                builtin = true,
            ),
            Skill(
                "Регулярки", "Регулярные выражения с пояснением",
                "РЕГУЛЯРКИ: дай готовое выражение в блоке кода, разбери его по частям (1–3 строки), " +
                    "приведи пример строки, которая совпадает, и которая нет. Укажи нужные флаги " +
                    "и диалект (Java/Kotlin, Python, JS).",
                builtin = true,
            ),
            Skill(
                "Коммит", "Сообщения коммитов по Conventional Commits",
                "КОММИТ: сообщение в стиле Conventional Commits (feat, fix, refactor, docs, test, chore). " +
                    "Первая строка до 72 символов, в повелительном наклонении, без точки. " +
                    "Если нужно — пустая строка и короткое тело: что и зачем.",
                builtin = true,
            ),
        )
    }
}
