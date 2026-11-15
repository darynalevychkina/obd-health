package com.example.obdhealth.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/** Поточна мова інтерфейсу: "uk" або "en". */
val LocalLang = staticCompositionLocalOf { "uk" }

/** Повертає український або англійський текст залежно від вибраної мови. */
@Composable
fun tr(uk: String, en: String): String = if (LocalLang.current == "en") en else uk
