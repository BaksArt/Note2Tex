package com.baksart.note2tex.domain.model

enum class AppLanguage(val tag: String) {
    SYSTEM(""),
    RU("ru"),
    EN("en");

    companion object {
        fun fromTag(tag: String?): AppLanguage = when (tag) {
            "ru" -> RU
            "en" -> EN
            else -> SYSTEM
        }
    }
}
