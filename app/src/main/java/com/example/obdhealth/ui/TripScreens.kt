package com.example.obdhealth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.obdhealth.data.TripInfo

@Composable
fun exitReasonLabel(code: String?): String = when (code) {
    "USER_STOPPED" -> tr("Зупинено користувачем", "Stopped by user")
    "ENGINE_OFF" -> tr("Двигун вимкнено", "Engine turned off")
    "ADAPTER_DISCONNECTED" -> tr("Адаптер від'єднався", "Adapter disconnected")
    "ECU_TIMEOUT" -> tr("ЕБК не відповів", "ECU timeout")
    "APP_INTERRUPTED" -> tr("Застосунок перервано", "App interrupted")
    null -> tr("Не вказано", "Not specified")
    else -> code
}

@Composable
fun eventTitle(type: String, message: String?): String = when (type) {
    "TRIP_STARTED" -> tr("Поїздку розпочато", "Trip started")
    "TRIP_FINISHED" -> tr("Поїздку завершено", "Trip finished")
    "COOLANT_HIGH" -> tr("Перегрів охолоджувача", "Coolant overheating")
    "COOLANT_LOW" -> tr("Двигун не прогрівається до норми", "Engine not reaching operating temperature")
    "WARMUP_SLOW" -> tr("Повільний прогрів", "Slow warm-up")
    "LTFT_HIGH" -> tr("Велика корекція палива (LTFT)", "Large fuel trim (LTFT)")
    "STFT_UNSTABLE" -> tr("Нестабільна суміш", "Unstable mixture")
    "BATTERY_LOW" -> tr("Низька напруга мережі", "Low board voltage")
    "BATTERY_HIGH" -> tr("Завищена напруга мережі", "High board voltage")
    "IDLE_RPM" -> tr("Оберти холостого ходу поза нормою", "Idle speed out of range")
    "ANOMALY_ML" -> tr("Відхилення від звичних поїздок цього авто", "Differs from this car's usual trips")
    else -> message ?: type
}

@Composable
fun VehicleChips(app: AppState, onPick: () -> Unit = {}) {
    if (app.vehicles.size > 1) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            app.vehicles.forEach { v ->
                Chip("${v.make} ${v.model}", v.id == app.selectedVehicleId) {
                    app.selectVehicle(v.id)
                    onPick()
                }
            }
        }
    }
}

/** Список поїздок вибраного авто. */
@Composable
fun TripsScreen(app: AppState) {
    val vehicle = app.selectedVehicle
    LaunchedEffect(app.selectedVehicleId) { app.loadTrips() }
    var showHelp by remember { mutableStateOf(false) }
    var scenario by remember { mutableIntStateOf(0) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Txt(tr("Поїздки", "Trips"), 30, FontWeight.ExtraBold)
            HelpButton { showHelp = true }
        }
        if (showHelp) ConnectHelpDialog { showHelp = false }

        if (vehicle == null) {
            ObdCard {
                Txt(tr("Спочатку додай авто в гаражі", "Add a car in the garage first"), 16, FontWeight.Bold)
                PrimaryButton(tr("Додати авто", "Add a car"), { app.openAddCar() })
            }
            return@Column
        }

        VehicleChips(app)
        Txt("${vehicle.make} ${vehicle.model} · ${vehicle.year}", 14, FontWeight.SemiBold, Obd.muted)

        ObdCard(bg = Obd.warnSoft, borderColor = Obd.warnSoft) {
            Txt(
                tr(
                    "Bluetooth-адаптер ще не підключено, тому поїздка симулюється. Дані при цьому реально зберігаються на сервері.",
                    "The Bluetooth adapter is not connected yet, so the trip is simulated. The data is really saved on the server.",
                ),
                12, FontWeight.SemiBold, Obd.warnText, lineHeight = 17,
            )
        }

        if (app.sim != null) {
            PrimaryButton(
                tr("Повернутися до поїздки", "Back to the trip"),
                { app.screen = Screen.Live },
                bg = Obd.recording, fg = Obd.onRecording,
            )
        } else {
            Txt(tr("Сценарій симуляції", "Simulation scenario"), 13, FontWeight.Bold, Obd.muted)
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Chip(tr("Норма", "Normal"), scenario == 0) { scenario = 0 }
                Chip(tr("Перегрів", "Overheating"), scenario == 1) { scenario = 1 }
                Chip(tr("Погана суміш", "Bad mixture"), scenario == 2) { scenario = 2 }
                Chip(tr("Слабкий акумулятор", "Weak battery"), scenario == 3) { scenario = 3 }
            }
            PrimaryButton(
                tr("Почати поїздку (симуляція)", "Start trip (simulation)"),
                { app.startSim(scenario) },
                icon = Icons.Default.PlayArrow,
            )
        }

        val err = app.tripsError
        if (err != null) {
            ErrorBox(err)
            OutlineButton(tr("Спробувати ще раз", "Try again"), { app.loadTrips() })
        }
        if (app.tripsLoading && app.trips.isEmpty()) {
            SkeletonCard(2)
            SkeletonCard(2)
        } else if (app.trips.isEmpty() && err == null) {
            Txt(tr("Поїздок ще немає.", "No trips yet."), 14, color = Obd.muted)
        }
        app.trips.forEachIndexed { i, tr0 -> Appear(minOf(i, 8)) { TripRow(app, tr0) } }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun TripRow(app: AppState, t: TripInfo) {
    val lang = LocalLang.current
    ObdCard(onClick = { app.openTrip(t.id) }) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Txt(fmtDate(t.startedAt, lang), 15, FontWeight.Bold)
            Txt("${fmtKm(t.distanceKm)} " + tr("км", "km"), 15, FontWeight.Bold, Obd.accent, mono = true)
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Txt(fmtDuration(t.durationSec, lang), 13, color = Obd.muted)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val h = t.healthIndex
                if (h != null) Tag("$h", Obd.surface2, healthColor(h), 12)
                if (t.countsForBaseline) {
                    Tag(tr("у нормі авто", "baseline"), Obd.goodSoft, Obd.good)
                } else {
                    Tag(tr("закоротка", "too short"), Obd.surface2, Obd.muted)
                }
            }
        }
    }
}

/** Екран активної (симульованої) поїздки. */
@Composable
fun LiveScreen(app: AppState) {
    val sim = app.sim
    if (sim == null) {
        // поїздку вже збережено або скасовано
        LaunchedEffect(Unit) { app.screen = Screen.Trips }
        return
    }
    @Suppress("UNUSED_VARIABLE")
    val tick = sim.tick // читаємо, щоб екран оновлювався
    val s = sim.last
    val elapsed = sim.tick.toLong()

    Column(Modifier.fillMaxSize()) {
        BackHeader(tr("Поїздка (симуляція)", "Trip (simulation)"), onBack = { app.back() })
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PulseDot(Obd.recording, 12)
                Spacer(Modifier.width(8.dp))
                Txt(tr("Запис", "Recording"), 14, FontWeight.Bold, Obd.recording)
                Spacer(Modifier.width(12.dp))
                Txt(fmtMmSs(elapsed), 28, FontWeight.ExtraBold, mono = true)
            }

            if (!app.notificationsEnabled()) {
                ObdCard(bg = Obd.warnSoft, borderColor = Obd.warn) {
                    Txt(
                        tr("Сповіщення вимкнені, тож поїздки не буде видно в шторці.",
                            "Notifications are off, so the trip won't show in the notification shade."),
                        13, FontWeight.SemiBold, Obd.warnText, lineHeight = 18,
                    )
                    OutlineButton(tr("Увімкнути сповіщення", "Turn on notifications"), { app.openNotificationSettings() })
                }
            }

            ObdCard {
                val need = 300f
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Txt(tr("До зарахування в норму авто", "Until it counts for the baseline"), 12, FontWeight.SemiBold, Obd.muted)
                    Txt("${minOf(elapsed, 300)} / 300 " + tr("с", "s"), 12, FontWeight.Bold, Obd.muted, mono = true)
                }
                RoadProgress(
                    elapsed / need,
                    if (elapsed >= 300) Obd.good else Obd.accent,
                    carColor = app.selectedVehicle?.colorHex?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() },
                )
                Txt(
                    if (elapsed >= 300) tr("Поїздка буде зарахована.", "This trip will count.")
                    else tr("Поїздка коротша за 5 хв не формує норму.", "Trips shorter than 5 min don't form the baseline."),
                    12, color = Obd.muted,
                )
            }

            if (s != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Gauge(
                        s.speed.toFloat(), 200f, tr("Швидкість", "Speed"), tr("км/год", "km/h"),
                        "${s.speed}", modifier = Modifier.weight(1f),
                    )
                    Gauge(
                        s.rpm.toFloat(), 7000f, tr("Оберти", "RPM"), tr("об/хв", "rpm"),
                        "${s.rpm}", redFrom = 6000f, modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Tile(tr("Охолоджувач", "Coolant"), "${s.coolant}", "°C", Modifier.weight(1f))
                    Tile(tr("Навантаження", "Engine load"), "${s.load.toInt()}", "%", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Tile(tr("Бортова напруга", "Battery"), "${s.battery}", "V", Modifier.weight(1f))
                    Tile("LTFT", "${s.ltft}", "%", Modifier.weight(1f))
                }
            }

            val err = app.uploadError
            if (err != null) {
                ErrorBox(tr("Не вдалося зберегти поїздку: ", "Could not save the trip: ") + err)
                OutlineButton(tr("Спробувати ще раз", "Try again"), { app.finishSim() })
                OutlineButton(tr("Відкинути поїздку", "Discard the trip"), { app.discardSim() })
            } else {
                PrimaryButton(
                    if (app.uploadBusy) tr("Зберігаю на сервері…", "Saving to the server…")
                    else tr("Завершити поїздку", "Finish trip"),
                    onClick = { if (!app.uploadBusy) app.finishSim() },
                    bg = Obd.recording, fg = Obd.onRecording,
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Tile(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
            .background(Obd.surface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Txt(label, 12, FontWeight.SemiBold, Obd.muted)
        Row(verticalAlignment = Alignment.Bottom) {
            Txt(value, 26, FontWeight.SemiBold, mono = true)
            if (unit.isNotEmpty()) {
                Spacer(Modifier.width(4.dp))
                Txt(unit, 12, color = Obd.muted, modifier = Modifier.padding(bottom = 4.dp))
            }
        }
    }
}

/** Деталі збереженої поїздки (дані з сервера). */
@Composable
fun TripDetailScreen(app: AppState) {
    val lang = LocalLang.current
    val d = app.tripDetail
    Column(Modifier.fillMaxSize()) {
        BackHeader(
            tr("Поїздка", "Trip"),
            onBack = { app.back() },
            subtitle = d?.let { fmtDate(it.trip.startedAt, lang) },
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val err = app.tripDetailError
            if (err != null) ErrorBox(err)
            if (d == null) {
                if (err == null) {
                    SkeletonCard(2)
                    SkeletonCard(3)
                }
                return@Column
            }
            val t = d.trip

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Tile(tr("Відстань", "Distance"), fmtKm(t.distanceKm), tr("км", "km"), Modifier.weight(1f))
                Tile(tr("Тривалість", "Duration"), fmtDuration(t.durationSec, lang), "", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Tile(tr("Показників", "Readings"), "${t.readingsCount}", "", Modifier.weight(1f))
                Tile(
                    tr("Індекс здоров'я", "Health index"),
                    t.healthIndex?.toString() ?: "—", "", Modifier.weight(1f),
                )
            }

            ObdCard {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (t.countsForBaseline) Tag(tr("зараховано в норму авто", "counts for baseline"), Obd.goodSoft, Obd.good)
                    else Tag(tr("не зараховано (закоротка)", "not counted (too short)"), Obd.surface2, Obd.muted)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Txt(tr("Режим оцінки", "Assessment mode"), 13, color = Obd.muted)
                    Txt(
                        if (t.mode == "REFERENCE") tr("за довідковою нормою", "reference norm")
                        else tr("норма + власні поїздки", "norm + own trips"),
                        13, FontWeight.SemiBold,
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Txt(tr("Завершення", "Ended"), 13, color = Obd.muted)
                    Txt(exitReasonLabel(t.exitReason), 13, FontWeight.SemiBold)
                }
                if (t.healthIndex == null) {
                    Txt(
                        tr(
                            "Оцінки стану немає: для неї потрібно щонайменше 30 показників (від 30 с запису).",
                            "No condition score: at least 30 readings (30+ s of recording) are needed.",
                        ),
                        12, color = Obd.muted, lineHeight = 17,
                    )
                }
            }

            if (d.samples.size >= 2) {
                var metric by remember { mutableIntStateOf(0) }
                var sel by remember { mutableStateOf<Int?>(null) }
                val pts = d.samples.filter {
                    when (metric) {
                        0 -> it.coolant != null
                        1 -> it.speed != null
                        else -> it.rpm != null
                    }
                }
                ObdCard {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Chip(tr("Температура", "Temperature"), metric == 0) { metric = 0; sel = null }
                        Chip(tr("Швидкість", "Speed"), metric == 1) { metric = 1; sel = null }
                        Chip(tr("Оберти", "RPM"), metric == 2) { metric = 2; sel = null }
                    }
                    if (pts.size >= 2) {
                        val vals = pts.map {
                            when (metric) {
                                0 -> (it.coolant ?: 0).toFloat()
                                1 -> (it.speed ?: 0).toFloat()
                                else -> (it.rpm ?: 0).toFloat()
                            }
                        }
                        val pad = (vals.max() - vals.min()).coerceAtLeast(10f) * 0.15f
                        LineChart(
                            values = vals,
                            minY = vals.min() - pad,
                            maxY = vals.max() + pad,
                            lineColor = Obd.accent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp),
                            selected = sel,
                            onSelect = { sel = it },
                        )
                        // шкала часу під графіком
                        val t0 = pts.first().tSec
                        val t1 = pts.last().tSec
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            for (k in 0..4) {
                                Txt(fmtMmSs((t0 + (t1 - t0) * k / 4).toLong()), 11, color = Obd.muted, mono = true)
                            }
                        }
                        val p = sel?.let { pts.getOrNull(it) }
                        if (p == null) {
                            Txt(
                                tr("Торкнись графіка або проведи пальцем, щоб побачити, що було в цей момент.",
                                    "Tap or drag on the chart to see what was happening at that moment."),
                                12, color = Obd.muted, lineHeight = 17,
                            )
                        } else {
                            val step = (pts.last().tSec - pts.first().tSec) / (pts.size - 1)
                            val near = d.events.filter {
                                kotlin.math.abs((it.ts - t.startedAt) / 1000.0 - p.tSec) <= step
                            }
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                                    .background(Obd.surface2)
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Txt(tr("Момент ", "At ") + fmtMmSs(p.tSec.toLong()) + tr(" від початку", " into the trip"),
                                    13, FontWeight.ExtraBold)
                                p.coolant?.let { Txt(tr("Температура: ", "Temperature: ") + "$it °C", 13) }
                                p.speed?.let { Txt(tr("Швидкість: ", "Speed: ") + "$it " + tr("км/год", "km/h"), 13) }
                                p.rpm?.let { Txt(tr("Оберти: ", "RPM: ") + "$it", 13) }
                                p.load?.let { Txt(tr("Навантаження: ", "Engine load: ") + "${it.toInt()} %", 13) }
                                near.forEach { e ->
                                    Txt(tr("Подія: ", "Event: ") + eventTitle(e.type, e.message), 13, FontWeight.Bold, Obd.accent)
                                }
                            }
                        }
                    }
                }
            }

            if (d.events.isNotEmpty()) {
                ObdCard {
                    Txt(tr("Події", "Events"), 15, FontWeight.Bold)
                    d.events.forEach { e ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            val (bg, fg) = when (e.severity) {
                                "CRITICAL" -> Obd.dangerSoft to Obd.danger
                                "WARNING" -> Obd.warnSoft to Obd.warnText
                                else -> Obd.surface2 to Obd.muted
                            }
                            Tag(e.severity, bg, fg, 10)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Txt(eventTitle(e.type, e.message), 13, FontWeight.SemiBold)
                                if (!e.type.startsWith("TRIP_") && e.message != null) {
                                    Txt(e.message, 12, color = Obd.muted)
                                }
                                Txt(fmtClock(e.ts), 11, color = Obd.muted, mono = true)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
