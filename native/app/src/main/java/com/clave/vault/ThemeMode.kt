package com.clave.vault

enum class ThemeMode(val label: String) {
    SYSTEM("Sistema"), DARK("Oscuro"), LIGHT("Claro");

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        DARK -> true
        LIGHT -> false
    }

    companion object {
        fun fromStored(value: String?) = entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}
