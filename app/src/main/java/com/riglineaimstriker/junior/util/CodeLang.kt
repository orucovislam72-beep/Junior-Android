package com.riglineaimstriker.junior.util

/** Определение языка кода по имени файла и нормализация меток из ```fence. */
object CodeLang {
    private val byExt = mapOf(
        "kt" to "kotlin", "kts" to "kotlin",
        "java" to "java",
        "py" to "python", "pyw" to "python",
        "js" to "javascript", "mjs" to "javascript", "cjs" to "javascript", "jsx" to "javascript",
        "ts" to "typescript", "tsx" to "typescript",
        "c" to "c", "h" to "c",
        "cpp" to "cpp", "cc" to "cpp", "cxx" to "cpp", "hpp" to "cpp", "hh" to "cpp",
        "sql" to "sql",
        "sh" to "bash", "bash" to "bash", "zsh" to "bash",
        "json" to "json",
        "xml" to "xml", "html" to "html", "htm" to "html", "css" to "css",
        "md" to "markdown", "yml" to "yaml", "yaml" to "yaml", "toml" to "toml",
        "go" to "go", "rs" to "rust", "swift" to "swift", "dart" to "dart",
        "rb" to "ruby", "php" to "php", "cs" to "csharp", "gradle" to "groovy",
        "txt" to "text", "properties" to "properties", "ini" to "ini", "cfg" to "ini",
    )

    fun fromFileName(name: String): String {
        val n = name.lowercase()
        if (n == "dockerfile") return "dockerfile"
        if (n == "makefile") return "makefile"
        return byExt[n.substringAfterLast('.', "")] ?: "text"
    }

    /** Приводит метку из ```kt / ```py / ```c++ к одному имени. */
    fun normalize(label: String): String = when (val l = label.trim().lowercase()) {
        "kt", "kts", "kotlin" -> "kotlin"
        "py", "python", "python3" -> "python"
        "js", "javascript", "jsx", "node" -> "javascript"
        "ts", "typescript", "tsx" -> "typescript"
        "c++", "cpp", "cc", "cxx", "hpp" -> "cpp"
        "h", "c" -> "c"
        "sh", "bash", "shell", "zsh", "console", "terminal" -> "bash"
        "json", "jsonc" -> "json"
        "sql", "postgresql", "postgres", "mysql", "sqlite" -> "sql"
        "java" -> "java"
        else -> l
    }
}
