package com.motogeek.telemetry.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

enum class AppLang(val code: String, val shortLabel: String, val displayName: String) {
    EN("en", "EN", "English"),
    SK("sk", "SK", "Slovenčina");

    fun pick(en: String, sk: String): String = if (this == SK) sk else en

    fun next(): AppLang = if (this == EN) SK else EN

    companion object {
        fun fromCode(code: String?): AppLang =
            entries.firstOrNull { it.code.equals(code?.trim(), ignoreCase = true) } ?: EN
    }
}

val LocalAppLang = compositionLocalOf { AppLang.EN }

@Composable
fun tr(en: String, sk: String): String = LocalAppLang.current.pick(en, sk)
