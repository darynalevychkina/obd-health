package com.example.obdhealth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import org.json.JSONObject

/** Прогноз: останній прогноз із сервера або порожній стан. */
@Composable
fun ForecastScreen(app: AppState) {
    val lang = LocalLang.current
    LaunchedEffect(app.selectedVehicleId, app.selectedVehicle?.tripsCount) {
        app.loadForecast()
        app.loadTrips()
    }
    val vehicle = app.selectedVehicle
    var showLocked by remember { mutableStateOf(false) }
    LaunchedEffect(showLocked) {
        if (showLocked) {
            kotlinx.coroutines.delay(3000)
            showLocked = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        Txt(tr("Прогноз", "Forecast"), 30, FontWeight.ExtraBold)

        if (vehicle == null) {
            ObdCard {
                Txt(tr("Спочатку додай авто в гаражі", "Add a car in the garage first"), 16, FontWeight.Bold)
                PrimaryButton(tr("Додати авто", "Add a car"), { app.openAddCar() })
            }
            return@Column
        }

        VehicleChips(app)
        Txt("${vehicle.make} ${vehicle.model} · ${vehicle.year}", 14, FontWeight.SemiBold, Obd.muted)

        val err = app.forecastError
        if (err != null) ErrorBox(err)

        val f = app.forecast
        if (f == null) {
            if (app.forecastLoading) {
                SkeletonCard(4)
            } else if (err == null) {
                ObdCard(bg = Obd.aiBg, borderColor = Obd.aiBorder) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = Obd.aiTitle, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Txt(tr("Прогнозу ще немає", "No forecast yet"), 14, FontWeight.ExtraBold, Obd.aiTitle)
                    }
                    Txt(
                        tr(
                            "Прогноз з'явиться після 3 зарахованих поїздок (від 5 хв кожна). Зараз зараховано: ${vehicle.baselineTrips} з 3.",
                            "A forecast will appear after 3 counted trips (5+ min each). Counted now: ${vehicle.baselineTrips} of 3.",
                        ),
                        14, lineHeight = 21,
                    )
                }
            }
        } else {
            // тренд (необов'язковий): {"title": "...", "values": [..], "threshold": n, "projection": n}
            val trend = try {
                f.trendRaw?.let { JSONObject(it) }
            } catch (_: Exception) {
                null
            }
            val values = trend?.optJSONArray("values")?.let { a ->
                (0 until a.length()).map { a.optDouble(it).toFloat() }
            } ?: emptyList()
            if (values.size >= 2) {
                ObdCard {
                    Txt(trend?.optString("title", "")?.ifEmpty { null } ?: tr("Тренд", "Trend"), 15, FontWeight.Bold)
                    val thr = if (trend != null && trend.has("threshold")) trend.optDouble("threshold").toFloat() else null
                    val proj = if (trend != null && trend.has("projection")) trend.optDouble("projection").toFloat() else null
                    val all = listOfNotNull(values.max(), values.min(), thr, proj)
                    val span = (all.max() - all.min()).coerceAtLeast(0.5f)
                    val hi = all.max() + span * 0.15f
                    val lo = all.min() - span * 0.15f
                    val last = values.last()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Txt(tr("Зараз", "Now"), 11, color = Obd.muted)
                            Txt(fmtNum(last), 18, FontWeight.Bold)
                        }
                        if (thr != null) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Txt(tr("Межа норми", "Limit"), 11, color = Obd.muted)
                            Txt(fmtNum(thr), 18, FontWeight.Bold, Obd.danger)
                        }
                        if (proj != null) Column(horizontalAlignment = Alignment.End) {
                            Txt(tr("Прогноз", "Projected"), 11, color = Obd.muted)
                            Txt(fmtNum(proj), 18, FontWeight.Bold, Obd.warnText)
                        }
                    }
                    LineChart(
                        values = values,
                        minY = lo,
                        maxY = hi,
                        lineColor = Obd.accent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp),
                        threshold = thr,
                        thresholdColor = Obd.danger,
                        projectionTo = proj,
                        projectionColor = Obd.warn,
                        showDots = true,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Txt("● " + tr("значення за поїздками", "value per trip"), 11, color = Obd.accent)
                        Txt("┄ " + tr("прогноз", "forecast"), 11, color = Obd.warnText)
                        Txt("┄ " + tr("межа", "limit"), 11, color = Obd.danger)
                    }
                    Txt(tr("Кожна точка — одна зарахована поїздка (зліва старіші).", "Each dot is one counted trip (older on the left)."), 11, color = Obd.muted)
                }
            }

            ObdCard(bg = Obd.aiBg, borderColor = Obd.aiBorder) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = Obd.aiTitle, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Txt(tr("Пояснення ШІ", "AI explanation"), 14, FontWeight.ExtraBold, Obd.aiTitle)
                    }
                    when (f.urgency) {
                        "HIGH" -> Tag(tr("Висока терміновість", "High urgency"), Obd.dangerSoft, Obd.danger, 12)
                        "MEDIUM" -> Tag(tr("Середня терміновість", "Medium urgency"), Obd.warnSoft, Obd.warnText, 12)
                        else -> Tag(tr("Низька терміновість", "Low urgency"), Obd.goodSoft, Obd.good, 12)
                    }
                }
                f.aiText.lines().map { it.trim() }.filter { it.isNotEmpty() }.forEach { ln ->
                    when {
                        ln.startsWith("- ") -> Bullet(ln.drop(2))
                        ln.endsWith(":") -> Txt(ln, 12, FontWeight.Bold, Obd.aiTitle)
                        else -> Txt(ln, 14, lineHeight = 21)
                    }
                }
                Txt(fmtDate(f.createdAt, lang), 11, color = Obd.muted)
            }
        }

        if (vehicle.baselineTrips >= 3) {
            val allAsc = app.trips.filter { it.countsForBaseline }.sortedBy { it.startedAt }
            val lockedIds = allAsc.take(2).map { it.id }.toSet()   // перші дві поїздки: замало даних для тренду
            val counted = allAsc.reversed().take(12)
            val lockedMsg = tr(
                "Не можна обрати перші 2 поїздки: для прогнозу потрібно щонайменше 3 поїздки. Обери 3-тю або пізнішу.",
                "The first 2 trips can't be selected: a forecast needs at least 3 trips. Pick the 3rd or a later one.",
            )
            if (showLocked) {
                Popup(alignment = Alignment.TopCenter, offset = IntOffset(0, 24)) {
                    Box(
                        Modifier
                            .padding(horizontal = 20.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Obd.warnSoft)
                            .border(1.dp, Obd.warn, RoundedCornerShape(14.dp))
                            .padding(14.dp),
                    ) {
                        Txt(lockedMsg, 13, FontWeight.SemiBold, Obd.warnText, lineHeight = 18)
                    }
                }
            }
            if (counted.isNotEmpty()) {
                val basedOn = try {
                    f?.trendRaw?.let { JSONObject(it) }?.optLong("based_on_trip_id", -1L) ?: -1L
                } catch (_: Exception) {
                    -1L
                }
                val activeId = if (basedOn > 0) basedOn else counted.first().id
                Txt(tr("Побудувати прогноз за поїздкою", "Build forecast from trip"), 13, FontWeight.Bold)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    counted.forEach { tr0 ->
                        Chip(fmtDate(tr0.startedAt, lang), tr0.id == activeId) {
                            if (tr0.id in lockedIds) showLocked = true
                            else if (!app.forecastLoading) app.refreshForecast(tr0.id)
                        }
                    }
                }
                Txt(
                    tr("Враховуються обрана поїздка та попередні до неї.", "The selected trip and the ones before it are used."),
                    11, color = Obd.muted,
                )
            }
            if (app.forecastLoading) Txt(tr("Оновлюю…", "Updating…"), 12, color = Obd.muted)
        }
        Txt(
            tr(
                "Прогноз орієнтовний і не замінює діагностику в сервісі. Тренд рахує сервер за вашими поїздками, а пояснення складає ШІ.",
                "The forecast is approximate and does not replace a workshop diagnosis. The server calculates the trend from your trips and AI writes the explanation.",
            ),
            12, color = Obd.muted, lineHeight = 17,
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun SettingsScreen(app: AppState) {
    var serverText by remember { mutableStateOf(app.serverUrl) }
    val themeOptions = listOf("light", "dark", "system")
    val langOptions = listOf("uk", "en")

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        Txt(tr("Налаштування", "Settings"), 30, FontWeight.ExtraBold)

        Section(tr("ВИГЛЯД", "APPEARANCE")) {
            Txt(tr("Тема", "Theme"), 14, FontWeight.Bold)
            Segmented(
                listOf(tr("Світла", "Light"), tr("Темна", "Dark"), tr("Системна", "System")),
                themeOptions.indexOf(app.theme).coerceAtLeast(0),
            ) { app.updateTheme(themeOptions[it]) }
            Txt(tr("Мова", "Language"), 14, FontWeight.Bold)
            Segmented(
                listOf("Українська", "English"),
                langOptions.indexOf(app.lang).coerceAtLeast(0),
            ) { app.updateLang(langOptions[it]) }
        }

        Section(tr("СЕРВЕР", "SERVER")) {
            ObdField(serverText, { serverText = it }, tr("Адреса сервера", "Server address"), keyboard = KeyboardType.Uri)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) {
                    OutlineButton(tr("Зберегти", "Save"), {
                        app.updateServerUrl(serverText)
                        serverText = app.serverUrl
                    })
                }
                Box(Modifier.weight(1f)) {
                    OutlineButton(tr("Перевірити", "Test"), {
                        app.updateServerUrl(serverText)
                        serverText = app.serverUrl
                        app.checkConnection()
                    })
                }
            }
            val status = app.connectionStatus
            if (status != null) Txt(status, 13, FontWeight.SemiBold, Obd.accent, lineHeight = 18)
            Txt(
                tr(
                    "Емулятор: http://10.0.2.2:8000. Реальний телефон: IP комп'ютера в тій самій Wi-Fi мережі, сервер запускати з --host 0.0.0.0.",
                    "Emulator: http://10.0.2.2:8000. Real phone: your computer's IP on the same Wi-Fi, start the server with --host 0.0.0.0.",
                ),
                12, color = Obd.muted, lineHeight = 17,
            )
        }

        Section(tr("АКАУНТ", "ACCOUNT")) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Txt(app.user?.email ?: "", 14, FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(onClick = { app.logout() }) {
                    Txt(tr("Вийти", "Sign out"), 14, FontWeight.Bold, Obd.danger)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column {
        Txt(title, 12, FontWeight.Bold, Obd.muted, modifier = Modifier.padding(bottom = 10.dp))
        ObdCard { content() }
    }
}

@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Obd.surface2)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val active = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (active) Obd.segmentActive else Obd.surface2)
                    .clickable { onSelect(i) },
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
}


@Composable
private fun Bullet(text: String) {
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(6.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(Obd.accent)
        )
        Spacer(Modifier.width(10.dp))
        Txt(text, 13)
    }
}

private fun fmtNum(v: Float): String = String.format(java.util.Locale.US, "%.2f", v)
