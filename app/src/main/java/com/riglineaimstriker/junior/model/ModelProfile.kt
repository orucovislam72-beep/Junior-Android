package com.riglineaimstriker.junior.model

data class ModelProfile(
    val id: String,
    val name: String,
    val description: String,
    val fileName: String,
    val url: String,
    val sizeGb: Double,
    val minRamGb: Double,
    val recommendedCtx: Int,
    val license: String,
    /** Имя файла внутри assets/models/, если модель вшита в APK. */
    val bundledAsset: String? = null,
)

object Profiles {
    // ВАЖНО: Qwen2.5-Coder-3B распространяется под Qwen Research License
    // (ограничивает коммерческое использование). Для публикации в Google Play
    // замените Mini на модель с открытой лицензией (например, Apache-2.0).
    val Mini = ModelProfile(
        id = "junior-mini",
        name = "Junior Mini",
        description = "Быстрый, подходит большинству телефонов.",
        fileName = "Qwen2.5-Coder-3B-Instruct-Q4_K_M.gguf",
        url = "https://huggingface.co/bartowski/Qwen2.5-Coder-3B-Instruct-GGUF/resolve/main/Qwen2.5-Coder-3B-Instruct-Q4_K_M.gguf",
        sizeGb = 1.93, minRamGb = 5.0, recommendedCtx = 4096, license = "Qwen Research",
        bundledAsset = "junior-mini.gguf",
    )
    val Pro = ModelProfile(
        id = "junior-pro",
        name = "Junior Pro",
        description = "Умнее и точнее, нужен мощный телефон.",
        fileName = "Qwen2.5-Coder-7B-Instruct-Q4_K_M.gguf",
        url = "https://huggingface.co/bartowski/Qwen2.5-Coder-7B-Instruct-GGUF/resolve/main/Qwen2.5-Coder-7B-Instruct-Q4_K_M.gguf",
        sizeGb = 4.68, minRamGb = 7.0, recommendedCtx = 4096, license = "Apache-2.0",
    )
    /** Модель из .gguf файла, выбранного пользователем. */
    val Custom = ModelProfile(
        id = "custom",
        name = "Своя модель",
        description = "Любая модель в формате GGUF из файла на телефоне.",
        fileName = "custom.gguf",
        url = "",
        sizeGb = 0.0, minRamGb = 0.0, recommendedCtx = 4096, license = "—",
    )
    val all = listOf(Mini, Pro)
    fun byId(id: String?): ModelProfile? = (all + Custom).firstOrNull { it.id == id }
}
