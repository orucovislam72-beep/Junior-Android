package com.riglineaimstriker.junior.skills

/** Навык (skill из Rigline): короткая инструкция, которую можно включать и выключать. */
data class Skill(
    val name: String,
    val description: String,
    val body: String,
    val builtin: Boolean,
    val active: Boolean = false,
)
