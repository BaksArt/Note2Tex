package com.baksart.note2tex.domain.model

enum class AppTheme(val key: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromKey(k: String?): AppTheme = when (k) {
            LIGHT.key -> LIGHT
            DARK.key  -> DARK
            else      -> SYSTEM
        }
    }
}