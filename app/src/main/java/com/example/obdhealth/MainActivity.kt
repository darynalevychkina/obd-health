package com.example.obdhealth

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import com.example.obdhealth.ui.AppState
import com.example.obdhealth.ui.LocalLang
import com.example.obdhealth.ui.ObdApp
import com.example.obdhealth.ui.ObdTheme

class MainActivity : ComponentActivity() {
    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = AppState.get(this)
        app.start()
        // дозвіл на сповіщення про поїздку просимо при її запуску (Android 13+)
        app.requestNotificationPermission = {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            val systemDark = isSystemInDarkTheme()
            val dark = when (app.theme) {
                "light" -> false
                "dark" -> true
                else -> systemDark
            }
            // іконки в статус-барі й навігації мають контрастувати з фоном теми
            DisposableEffect(dark) {
                val transparent = android.graphics.Color.TRANSPARENT
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(transparent)
                    else SystemBarStyle.light(transparent, transparent),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(transparent)
                    else SystemBarStyle.light(transparent, transparent),
                )
                onDispose { }
            }
            CompositionLocalProvider(LocalLang provides app.lang) {
                ObdTheme(dark) {
                    ObdApp(app)
                }
            }
        }
    }

    override fun onDestroy() {
        AppState.get(this).requestNotificationPermission = null
        super.onDestroy()
    }
}
