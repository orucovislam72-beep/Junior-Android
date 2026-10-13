package com.riglineaimstriker.junior.ui.components

import android.content.res.Configuration
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Общие элементы интерфейса «Минеральная мастерская».
 * Только оформление: никакой логики и состояния, всё приходит параметрами.
 */
object Space {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

/** Основная форма: 18 dp. Компактная (поля ввода, код): 16 dp. */
val JuniorShape = RoundedCornerShape(18.dp)
val JuniorCompactShape = RoundedCornerShape(16.dp)

/** Длительность функциональных переходов, мс (в пределах 150–250). */
const val MOTION_MS = 200

/**
 * true, если в системе отключены анимации (масштаб длительности аниматора = 0).
 * Значение отслеживается на лету через ContentObserver: переключение в настройках
 * телефона сразу останавливает крутящиеся индикаторы.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val ctx = LocalContext.current
    val resolver = ctx.contentResolver
    fun read(): Boolean = runCatching {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }.getOrDefault(false)
    var reduced by remember(resolver) { mutableStateOf(read()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduced = read()
            }
        }
        val uri = Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE)
        runCatching { resolver.registerContentObserver(uri, false, observer) }
        reduced = read()
        onDispose { runCatching { resolver.unregisterContentObserver(observer) } }
    }
    return reduced
}

/**
 * Тихий индикатор работы: маленький круг + подпись. При отключённых анимациях — статичная точка.
 * На экране должен быть не больше чем один такой.
 */
@Composable
fun ActivityLine(text: String, modifier: Modifier = Modifier) {
    val reduced = rememberReducedMotion()
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (reduced) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
        } else {
            CircularProgressIndicator(
                Modifier.size(14.dp),
                strokeWidth = 1.75.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.width(Space.s))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Верхняя панель экрана: «Назад», заголовок слева, действия справа. */
@Composable
fun ScreenTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    onTitleClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    JuniorIcon(JuniorIcons.Back, contentDescription = "Назад")
                }
            } else {
                Spacer(Modifier.width(Space.m))
            }
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .then(if (onTitleClick != null) Modifier.clickable(onClick = onTitleClick) else Modifier)
                    .padding(horizontal = Space.xs, vertical = Space.xs),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            actions()
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
    }
}

/** Заголовок раздела внутри экрана. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier.semantics { heading() },
    )
}

/**
 * Небольшая иллюстрация. Не больше 160 dp и трети высоты экрана;
 * на низких экранах (альбомная ориентация) не показывается.
 *
 * Цвета иллюстраций лежат в ресурсах (values / values-night) и по умолчанию следуют
 * системной теме. Если в настройках Junior выбрана другая тема, подменяем конфигурацию
 * (uiMode) только для этой картинки, чтобы она совпадала с темой приложения.
 */
@Composable
fun Illustration(@DrawableRes res: Int, modifier: Modifier = Modifier) {
    val h = LocalConfiguration.current.screenHeightDp
    if (h < 480) return
    val side = minOf(160, h / 3).dp
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val ctx = LocalContext.current
    val baseConfig = LocalConfiguration.current
    val themed = remember(ctx, baseConfig, dark) {
        val cfg = Configuration(baseConfig)
        val night = if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        cfg.uiMode = (cfg.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
        ctx.createConfigurationContext(cfg) to cfg
    }
    CompositionLocalProvider(LocalContext provides themed.first, LocalConfiguration provides themed.second) {
        Image(painterResource(res), contentDescription = null, modifier = modifier.size(side))
    }
}

/**
 * Показываем только известные понятные сообщения приложения (они перечислены ниже,
 * с учётом подставляемых имён файлов и чисел). Всё остальное — системный текст
 * исключения — заменяется безопасной фразой [fallback].
 */
fun humanError(raw: String?, fallback: String): String {
    val t = raw?.trim().orEmpty()
    if (t.isEmpty()) return fallback
    RENAMED_MESSAGES[t]?.let { return it }
    if (t in KNOWN_MESSAGES) return t
    if (KNOWN_PREFIXES.any { t.startsWith(it) } && t.none { it == '\n' } && t.length <= 200) return t
    return fallback
}

private val KNOWN_MESSAGES = setOf(
    // движок и модель
    "Не удалось открыть модель. Файл повреждён или не хватает памяти.",
    "Не найдена нативная библиотека для этого устройства.",
    "Не удалось загрузить модель.",
    "Файл модели не найден. Скачайте её заново.",
    "Модель не загружена",
    "Модель выгружена",
    "Диалог слишком длинный. Начните новый чат.",
    "Не удалось получить ответ. Повторите запрос.",
    // подготовка, загрузка и импорт модели
    "Модель не вшита в приложение",
    "Файл модели в приложении повреждён",
    "Не удалось открыть выбранный файл",
    "Это не файл модели GGUF. Выберите файл с расширением .gguf",
    "Не удалось сохранить модель",
    "Не удалось подготовить модель",
    "Не удалось скачать модель",
    "Неизвестная модель",
    "Недостаточно места на устройстве",
    "Файл модели повреждён. Попробуйте ещё раз",
    "Не удалось сохранить файл модели",
    "Нет соединения. Проверьте интернет и повторите",
    "Обрыв загрузки",
    "Не удалось загрузить модель из файла",
    // файлы и агент
    "Не удалось открыть файл.",
    "Это не текстовый файл. Приложите файл с кодом или текстом.",
    "Не удалось прочитать файл.",
    "Не указан путь к файлу.",
    "Путь вне проекта.",
    "Не удалось создать папку.",
    "Нет доступа к папке.",
    "Нет разрешения на папку.",
    "Не удалось открыть файл для записи.",
    "Нет доступа к сохранённой папке. Выберите её заново.",
    "Запись отклонена",
    "Ошибка",
    "Остановлено",
    "Неизвестная ошибка",
    // голосовой ввод (сообщения экрана чата)
    "На телефоне нет распознавания речи. Установите или включите Google (голосовой ввод).",
    "Голосовой ввод недоступен на этом устройстве.",
)

/** Технические сообщения инструментов агента → понятный русский текст. */
private val RENAMED_MESSAGES = mapOf(
    "Не указан path." to "Модель не указала путь к файлу или папке.",
    "Не указан query." to "Модель не указала, что искать.",
)

private const val RAW_CONTEXT_STATUS = "Читаю контекст…"

/**
 * Подпись строки состояния чата: служебное «Читаю контекст… N%» показываем как
 * «Читаю переписку… N%» (процент сохраняется). Остальные статусы — как есть.
 */
fun chatStatusText(raw: String?): String {
    val t = raw?.trim().orEmpty()
    if (t.isEmpty()) return "Думаю…"
    return if (t.startsWith(RAW_CONTEXT_STATUS)) "Читаю переписку…" + t.removePrefix(RAW_CONTEXT_STATUS) else t
}

/**
 * Подпись строки состояния агента: отрезаем служебный хвост « · …» (контекст, ток/с)
 * и показываем «Читаю контекст… N%» как «Разбираю задачу… N%».
 */
fun agentStatusText(raw: String): String {
    val t = raw.substringBefore(" · ").trim()
    return if (t.startsWith(RAW_CONTEXT_STATUS)) "Разбираю задачу…" + t.removePrefix(RAW_CONTEXT_STATUS) else t
}

private val KNOWN_PREFIXES = listOf(
    "Ошибка модели (код ",
    "Недостаточно свободного места: нужно около ",
    "Файл «", "Папка «", "Путь «", "«",
    "Не удалось создать папку «", "Не удалось создать файл «",
    "Файл больше ", "Файл большой: модель увидит",
    "Можно включить не больше ", "Название длиннее ", "Слишком длинный навык",
    "Введите название.", "Введите текст навыка.", "Это имя занято встроенным навыком.",
)

/** Русские форматы для подписей интерфейса (не зависят от языка телефона). */
val RU: Locale = Locale.forLanguageTag("ru-RU")
private const val NBSP = '\u00A0'

/** «1,2 ГБ» с неразрывным пробелом. */
fun formatGb(bytes: Double): String = String.format(RU, "%.1f", bytes / 1e9) + NBSP + "ГБ"

/** «512 Б» / «12,3 КБ». */
fun formatSize(bytes: Long): String =
    if (bytes < 1024) "$bytes${NBSP}Б" else String.format(RU, "%.1f", bytes / 1024.0) + NBSP + "КБ"

/** «0,4» — дробное число по-русски. */
fun formatDecimal(value: Float): String = String.format(RU, "%.1f", value)

/** Время по-русски: «только что», «5 мин назад», «сегодня, 14:05», «вчера, 09:30», «3 октября», «3 октября 2025». */
fun formatWhen(ms: Long, now: Long = System.currentTimeMillis()): String {
    val diff = now - ms
    if (diff in 0 until 60_000L) return "только что"
    if (diff in 60_000L until 3_600_000L) return "${diff / 60_000L}${NBSP}мин назад"
    val c = Calendar.getInstance().apply { timeInMillis = ms }
    val n = Calendar.getInstance().apply { timeInMillis = now }
    val time = SimpleDateFormat("HH:mm", RU).format(Date(ms))
    val sameYear = c.get(Calendar.YEAR) == n.get(Calendar.YEAR)
    val dayDiff = n.get(Calendar.DAY_OF_YEAR) - c.get(Calendar.DAY_OF_YEAR)
    return when {
        sameYear && dayDiff == 0 -> "сегодня, $time"
        sameYear && dayDiff == 1 -> "вчера, $time"
        sameYear -> SimpleDateFormat("d MMMM", RU).format(Date(ms))
        else -> SimpleDateFormat("d MMMM yyyy", RU).format(Date(ms))
    }
}

/** Пустое состояние/ошибка: иллюстрация, заголовок, пояснение и действие. */
@Composable
fun EmptyState(
    @DrawableRes illustration: Int?,
    title: String,
    text: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = Space.xl, vertical = Space.xl),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        if (illustration != null) Illustration(illustration)
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null) {
            Spacer(Modifier.size(Space.s))
            action()
        }
    }
}

/** Строка навигации: значок, название, пояснение, стрелка. Цель касания ≥ 56 dp. */
@Composable
fun NavRow(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(JuniorCompactShape)
            .clickable(onClick = onClick)
            .padding(horizontal = Space.m, vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        JuniorIcon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(Space.l))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        JuniorIcon(
            JuniorIcons.ChevronRight, contentDescription = null, size = 20.dp,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Сообщение в строке: ошибка или пояснение, с необязательным действием. */
@Composable
fun InlineNotice(
    text: String,
    isError: Boolean,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val bg = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
    Row(
        modifier
            .fillMaxWidth()
            .clip(JuniorCompactShape)
            .background(bg)
            .padding(start = Space.l, end = Space.xs, top = Space.xs, bottom = Space.xs)
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isError) {
            JuniorIcon(JuniorIcons.Warning, contentDescription = "Ошибка", size = 20.dp, tint = fg)
            Spacer(Modifier.width(Space.s))
        }
        Text(text, Modifier.weight(1f).padding(vertical = Space.s), style = MaterialTheme.typography.bodyMedium, color = fg)
        if (action != null) action()
    }
}
