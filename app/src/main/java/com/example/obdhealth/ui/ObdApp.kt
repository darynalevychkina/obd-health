package com.example.obdhealth.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Корінь застосунку: заставка, потім вхід, потім основні екрани. */
@Composable
fun ObdApp(app: AppState) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Obd.bg)
    ) {
        when {
            !app.splashDone -> SplashScreen(app.bootDone)
            app.user == null -> AuthScreen(app)
            else -> MainShell(app)
        }
    }
}

@Composable
private fun MainShell(app: AppState) {
    val screen = app.screen
    val isTab = screen == Screen.Garage || screen == Screen.Trips ||
        screen == Screen.Forecast || screen == Screen.Settings

    BackHandler(enabled = screen != Screen.Garage) { app.back() }

    Scaffold(
        containerColor = Obd.bg,
        bottomBar = {
            if (isTab) ObdBottomBar(current = screen, onSelect = { app.screen = it })
        },
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            when (screen) {
                Screen.Garage -> GarageScreen(app)
                Screen.Trips -> TripsScreen(app)
                Screen.Forecast -> ForecastScreen(app)
                Screen.Settings -> SettingsScreen(app)
                Screen.AddCar -> AddCarScreen(app)
                Screen.Live -> LiveScreen(app)
                Screen.TripDetail -> TripDetailScreen(app)
            }
        }
    }
}
