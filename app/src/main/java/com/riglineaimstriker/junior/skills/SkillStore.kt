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
        return "Активные навыки (инструкции пользователя, выполняй их):\n\n" + parts.joinToString("\n\n")
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
                "Отвечай кратко: без вступлений и пересказа вопроса. Сначала код или ответ, " +
                    "потом 1-3 строки пояснения. Если задача неясна, задай один короткий вопрос.",
                builtin = true,
            ),
            Skill(
                "Ревью кода", "Строгое ревью без переписывания",
                "РЕЖИМ РЕВЬЮ: не переписывай код целиком. Перечисли ошибки, проблемы безопасности " +
                    "и опасные граничные случаи, самые серьёзные первыми. У каждой укажи место " +
                    "в коде и идею исправления в одну строку. Если проблем нет, прямо скажи об этом.",
                builtin = true,
            ),
            Skill(
                "Минимум правок", "Только нужные изменения, без рефакторинга",
                "МИНИМАЛЬНЫЕ ПРАВКИ: меняй только то, что нужно для задачи. Без переименований, " +
                    "переформатирования и лишней уборки. Сохраняй стиль и имена из исходного кода. " +
                    "Показывай только изменённые фрагменты.",
                builtin = true,
            ),
            Skill(
                "Сначала тест", "Сначала тест, потом код",
                "TDD: для изменения поведения сначала напиши тест, который падает, затем реализацию, " +
                    "которая его проходит. Укажи команду запуска тестов.",
                builtin = true,
            ),
            Skill(
                "Комментарии по-русски", "Комментарии и коммиты на русском",
                "Пиши комментарии в коде, докстринги и сообщения коммитов по-русски. " +
                    "Имена переменных и функций оставляй английскими.",
                builtin = true,
            ),
            Skill(
                "Объясняй просто", "Объяснения как для новичка",
                "Объясняй как новичку: короткие предложения, термины расшифровывай, " +
                    "приводи небольшой пример.",
                builtin = true,
            ),
            Skill(
                "Разбор ошибки", "Чтение стектрейсов и сообщений об ошибках",
                "РАЗБОР ОШИБКИ: по тексту ошибки или стектрейсу назови вероятную причину " +
                    "(самую частую первой), укажи строку/место, где искать, и дай короткое " +
                    "исправление. Если данных мало — скажи, какой кусок кода или лога нужен.",
                builtin = true,
            ),
            Skill(
                "SQL", "Точные и безопасные SQL-запросы",
                "SQL: пиши запросы стандартным синтаксисом, уточняй диалект (PostgreSQL/MySQL/SQLite), " +
                    "если он важен. Избегай опасных операций без WHERE. Коротко поясняй, что делает запрос, " +
                    "и подсказывай индексы, если запрос тяжёлый.",
                builtin = true,
            ),
            Skill(
                "Регулярки", "Регулярные выражения с пояснением",
                "РЕГУЛЯРКИ: дай готовое выражение, разбери его по частям в 1-3 строках и приведи " +
                    "пример совпадения и несовпадения. Укажи флаги (например, многострочный режим), если нужны.",
                builtin = true,
            ),
            Skill(
                "Коммит", "Сообщения коммитов по Conventional Commits",
                "КОММИТ: предлагай сообщение коммита в стиле Conventional Commits " +
                    "(feat:, fix:, refactor:, docs:, test:, chore:). Первая строка до 72 символов, " +
                    "в повелительном наклонении. При необходимости добавь короткое тело с пояснением зачем.",
                builtin = true,
            ),
        )
    }
}
