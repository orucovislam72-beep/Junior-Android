package com.riglineaimstriker.junior.agent

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File

/** Папка проекта, с которой работает агент. Все пути — относительные от корня. */
interface Workspace {
    /** Название для интерфейса. */
    val title: String

    /** Список файлов и папок (рекурсивно до [depth] уровней). Папки — с «/» на конце. */
    fun list(path: String, depth: Int = 2, limit: Int = 200): List<String>

    /** Текст файла или null, если файла нет. */
    fun readOrNull(path: String): String?

    fun write(path: String, content: String)

    fun mkdirs(path: String)

    /** Все текстовые файлы под [path] (для поиска). */
    fun files(path: String, limit: Int = 400): List<String>

    fun isDir(path: String): Boolean
}

/** Нормализация пути от модели: только относительные пути, без «..». */
object WorkspacePaths {
    val SKIP_DIRS = setOf(".git", ".gradle", "build", "node_modules", ".idea", "__pycache__", ".cxx", "venv", ".venv")

    fun segments(path: String): List<String> {
        val parts = path.trim().trim('"', '\'', '`').replace('\\', '/')
            .split('/').filter { it.isNotEmpty() && it != "." }
        require(parts.none { it == ".." }) { "Путь «$path» выходит за пределы проекта." }
        return parts
    }

    fun normalize(path: String): String = segments(path).joinToString("/").ifEmpty { "." }

    /** Как [normalize], но не бросает исключение на плохом пути (для подписей в интерфейсе). */
    fun normalizeOrRaw(path: String): String = runCatching { normalize(path) }.getOrDefault(path)
}

/** Папка внутри приложения (filesDir/workspace) — работает без разрешений. */
class FileWorkspace(private val root: File, override val title: String) : Workspace {
    init { root.mkdirs() }

    private fun resolve(path: String): File {
        val f = WorkspacePaths.segments(path).fold(root) { acc, s -> File(acc, s) }
        val rootPath = root.canonicalPath
        val fp = f.canonicalPath
        require(fp == rootPath || fp.startsWith(rootPath + File.separator)) { "Путь вне проекта." }
        return f
    }

    override fun list(path: String, depth: Int, limit: Int): List<String> {
        val base = resolve(path)
        require(base.isDirectory) { "Папка «${WorkspacePaths.normalize(path)}» не найдена." }
        val out = ArrayList<String>()
        fun walk(dir: File, prefix: String, level: Int) {
            val children = dir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: return
            for (c in children) {
                if (out.size >= limit) return
                if (c.isDirectory) {
                    out += "$prefix${c.name}/"
                    if (level < depth && c.name !in WorkspacePaths.SKIP_DIRS) walk(c, "$prefix${c.name}/", level + 1)
                } else out += "$prefix${c.name}"
            }
        }
        walk(base, "", 1)
        return out
    }

    override fun readOrNull(path: String): String? {
        val f = resolve(path)
        return if (f.isFile) f.readText() else null
    }

    override fun write(path: String, content: String) {
        val f = resolve(path)
        require(!f.isDirectory) { "«${WorkspacePaths.normalize(path)}» — это папка." }
        f.parentFile?.mkdirs()
        f.writeText(content)
    }

    override fun mkdirs(path: String) {
        val f = resolve(path)
        require(f.isDirectory || f.mkdirs()) { "Не удалось создать папку." }
    }

    override fun files(path: String, limit: Int): List<String> {
        val base = resolve(path)
        val rootPath = root.canonicalPath
        if (base.isFile) return listOf(base.canonicalPath.removePrefix(rootPath).trimStart(File.separatorChar))
        return base.walkTopDown()
            .onEnter { it == base || it.name !in WorkspacePaths.SKIP_DIRS }
            .filter { it.isFile && it.length() < 256 * 1024 }
            .take(limit)
            .map { it.canonicalPath.removePrefix(rootPath).trimStart(File.separatorChar).replace(File.separatorChar, '/') }
            .toList()
    }

    override fun isDir(path: String): Boolean = resolve(path).isDirectory
}

/** Папка, выбранная пользователем через системный диалог (Storage Access Framework). */
class SafWorkspace(private val ctx: Context, treeUri: Uri) : Workspace {
    private val root: DocumentFile = DocumentFile.fromTreeUri(ctx, treeUri)
        ?: throw IllegalArgumentException("Нет доступа к папке.")

    override val title: String = root.name ?: "Папка проекта"

    private fun find(path: String): DocumentFile? {
        var cur = root
        for (s in WorkspacePaths.segments(path)) {
            cur = cur.findFile(s) ?: return null
        }
        return cur
    }

    override fun list(path: String, depth: Int, limit: Int): List<String> {
        val base = find(path)?.takeIf { it.isDirectory }
            ?: throw IllegalArgumentException("Папка «${WorkspacePaths.normalize(path)}» не найдена.")
        val out = ArrayList<String>()
        fun walk(dir: DocumentFile, prefix: String, level: Int) {
            val children = dir.listFiles().sortedWith(compareBy({ !it.isDirectory }, { (it.name ?: "").lowercase() }))
            for (c in children) {
                if (out.size >= limit) return
                val name = c.name ?: continue
                if (c.isDirectory) {
                    out += "$prefix$name/"
                    if (level < depth && name !in WorkspacePaths.SKIP_DIRS) walk(c, "$prefix$name/", level + 1)
                } else out += "$prefix$name"
            }
        }
        walk(base, "", 1)
        return out
    }

    override fun readOrNull(path: String): String? {
        val f = find(path)?.takeIf { it.isFile } ?: return null
        return ctx.contentResolver.openInputStream(f.uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
    }

    private fun dirFor(segments: List<String>): DocumentFile {
        var cur = root
        for (s in segments) {
            val existing = cur.findFile(s)
            cur = when {
                existing == null -> cur.createDirectory(s)
                    ?: throw IllegalStateException("Не удалось создать папку «$s».")
                existing.isDirectory -> existing
                else -> throw IllegalArgumentException("«$s» — это файл, а не папка.")
            }
        }
        return cur
    }

    override fun write(path: String, content: String) {
        val segs = WorkspacePaths.segments(path)
        require(segs.isNotEmpty()) { "Не указан путь к файлу." }
        val dir = dirFor(segs.dropLast(1))
        val name = segs.last()
        val existing = dir.findFile(name)
        require(existing?.isDirectory != true) { "«$name» — это папка." }
        // application/octet-stream: провайдер не будет дописывать своё расширение (.txt).
        val file = existing ?: dir.createFile("application/octet-stream", name)
            ?: throw IllegalStateException("Не удалось создать файл «$name».")
        val bytes = content.toByteArray(Charsets.UTF_8)
        val out = runCatching { ctx.contentResolver.openOutputStream(file.uri, "wt") }.getOrNull()
            ?: ctx.contentResolver.openOutputStream(file.uri, "w")
            ?: throw IllegalStateException("Не удалось открыть файл для записи.")
        out.use { it.write(bytes) }
    }

    override fun mkdirs(path: String) {
        dirFor(WorkspacePaths.segments(path))
    }

    override fun files(path: String, limit: Int): List<String> {
        val base = find(path) ?: throw IllegalArgumentException("«${WorkspacePaths.normalize(path)}» не найден.")
        val prefix0 = WorkspacePaths.segments(path).joinToString("/")
        if (base.isFile) return listOf(prefix0)
        val out = ArrayList<String>()
        fun walk(dir: DocumentFile, prefix: String) {
            for (c in dir.listFiles()) {
                if (out.size >= limit) return
                val name = c.name ?: continue
                if (c.isDirectory) {
                    if (name !in WorkspacePaths.SKIP_DIRS) walk(c, "$prefix$name/")
                } else if (c.length() < 256 * 1024) out += "$prefix$name"
            }
        }
        walk(base, if (prefix0.isEmpty()) "" else "$prefix0/")
        return out
    }

    override fun isDir(path: String): Boolean = find(path)?.isDirectory == true
}
