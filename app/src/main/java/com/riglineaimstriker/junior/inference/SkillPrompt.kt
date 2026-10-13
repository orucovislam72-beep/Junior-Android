package com.riglineaimstriker.junior.inference

/**
 * Встраивает инструкции навыков в системный промпт.
 * Раньше они подмешивались в последнюю реплику пользователя, но тогда промпт менялся
 * в середине на каждом шаге и KV-кэш не мог переиспользовать прошлую переписку.
 */
object SkillPrompt {
    fun system(base: String, rules: String): String = Prompts.withSkills(base, rules)
}
