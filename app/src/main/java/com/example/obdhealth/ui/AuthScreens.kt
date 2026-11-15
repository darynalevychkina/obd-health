package com.example.obdhealth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/** Вхід і реєстрація. Показується одразу, якщо користувач не авторизований. */
@Composable
fun AuthScreen(app: AppState) {
    var register by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showServer by remember { mutableStateOf(false) }
    var serverText by remember { mutableStateOf(app.serverUrl) }
    var localError by remember { mutableStateOf<String?>(null) }

    val badEmail = tr("Введи коректний email", "Enter a valid email")
    val shortPass = tr("Пароль має містити щонайменше 8 символів", "Password must be at least 8 characters")

    fun submit() {
        localError = null
        app.authError = null
        if (!email.contains("@") || !email.contains(".")) {
            localError = badEmail
            return
        }
        if (register && password.length < 8) {
            localError = shortPass
            return
        }
        if (password.isEmpty()) {
            localError = shortPass
            return
        }
        app.updateServerUrl(serverText)
        if (register) app.register(email, password) else app.login(email, password)
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // перемикач мови
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Chip("UK", app.lang == "uk") { app.updateLang("uk") }
            Spacer(Modifier.size(8.dp))
            Chip("EN", app.lang == "en") { app.updateLang("en") }
        }

        Spacer(Modifier.height(28.dp))
        Box(
            Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(Obd.accentSoft),
            contentAlignment = Alignment.Center,
        ) {
            CarIcon(Obd.accent, Modifier.size(44.dp))
        }
        Spacer(Modifier.height(16.dp))
        Txt("OBD Health", 28, FontWeight.ExtraBold)
        Spacer(Modifier.height(4.dp))
        Txt(
            if (register) tr("Створи акаунт", "Create an account")
            else tr("Увійди до свого акаунта", "Sign in to your account"),
            15,
            color = Obd.muted,
        )
        Spacer(Modifier.height(24.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Obd.surface2)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            listOf(
                tr("Вхід", "Sign in") to false,
                tr("Реєстрація", "Sign up") to true,
            ).forEach { (label, isReg) ->
                val active = register == isReg
                Box(
                    Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (active) Obd.segmentActive else Obd.surface2)
                        .clickable {
                            register = isReg
                            localError = null
                            app.authError = null
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Txt(
                        label, 14,
                        if (active) FontWeight.Bold else FontWeight.SemiBold,
                        if (active) Obd.ink else Obd.muted,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        ObdField(email, { email = it }, tr("Email", "Email"), keyboard = KeyboardType.Email)
        Spacer(Modifier.height(12.dp))
        ObdField(
            password, { password = it }, tr("Пароль", "Password"),
            keyboard = KeyboardType.Password, password = true,
        )
        if (register) {
            Txt(
                tr("Мінімум 8 символів", "At least 8 characters"),
                12, color = Obd.muted,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, start = 4.dp),
            )
        }

        val err = localError ?: app.authError
        if (err != null) {
            Spacer(Modifier.height(12.dp))
            ErrorBox(err)
        }

        Spacer(Modifier.height(18.dp))
        PrimaryButton(
            text = when {
                app.authBusy -> tr("Зачекай…", "Please wait…")
                register -> tr("Створити акаунт", "Create account")
                else -> tr("Увійти", "Sign in")
            },
            onClick = { if (!app.authBusy) submit() },
        )

        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { showServer = !showServer }) {
            Txt(
                tr("Сервер: ", "Server: ") + app.serverUrl,
                12, FontWeight.SemiBold, Obd.muted,
            )
        }
        if (showServer) {
            ObdField(serverText, { serverText = it }, tr("Адреса сервера", "Server address"), keyboard = KeyboardType.Uri)
            Txt(
                tr(
                    "Емулятор: http://10.0.2.2:8000. Реальний телефон: IP-адреса комп'ютера в Wi-Fi, наприклад http://192.168.0.10:8000",
                    "Emulator: http://10.0.2.2:8000. Real phone: your computer's Wi-Fi IP, e.g. http://192.168.0.10:8000",
                ),
                12, color = Obd.muted, lineHeight = 17,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}
