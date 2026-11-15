package com.example.obdhealth.data

import android.content.Context
import java.util.Locale

/** Локальні налаштування на телефоні (SharedPreferences). */
class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("obd_health", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = sp.getString("server", DEFAULT_SERVER) ?: DEFAULT_SERVER
        set(v) = sp.edit().putString("server", v).apply()

    var token: String?
        get() = sp.getString("token", null)
        set(v) = sp.edit().putString("token", v).apply()

    /** "system", "light" або "dark" */
    var theme: String
        get() = sp.getString("theme", "system") ?: "system"
        set(v) = sp.edit().putString("theme", v).apply()

    /** "uk" або "en" */
    var lang: String
        get() = sp.getString("lang", null)
            ?: if (Locale.getDefault().language == "en") "en" else "uk"
        set(v) = sp.edit().putString("lang", v).apply()

    companion object {
        /** Адреса комп'ютера з емулятора Android. З реального телефона потрібна IP-адреса ПК. */
        const val DEFAULT_SERVER = "http://10.0.2.2:8000"
    }
}
