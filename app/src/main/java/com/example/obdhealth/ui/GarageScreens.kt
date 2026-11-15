package com.example.obdhealth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import com.example.obdhealth.data.VehicleInfo
import java.util.Calendar
import kotlin.math.roundToInt

@Composable
fun fuelLabel(code: String): String = when (code) {
    "PETROL" -> tr("Бензин", "Petrol")
    "DIESEL" -> tr("Дизель", "Diesel")
    "HYBRID" -> tr("Гібрид", "Hybrid")
    "ELECTRIC" -> tr("Електро", "Electric")
    "LPG" -> tr("Газ (LPG)", "LPG")
    else -> code
}

/** Гараж: список авто користувача з сервера. */
@Composable
fun GarageScreen(app: AppState) {
    var showHelp by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<VehicleInfo?>(null) }
    pendingDelete?.let { v ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = Obd.surface,
            title = { Txt(tr("Видалити авто?", "Delete this car?"), 18, FontWeight.ExtraBold) },
            text = {
                Txt(
                    tr(
                        "Авто «${v.make} ${v.model}» разом з усіма його поїздками буде видалено назавжди.",
                        "“${v.make} ${v.model}” and all its trips will be permanently deleted.",
                    ),
                    14, lineHeight = 20,
                )
            },
            confirmButton = {
                TextButton(onClick = { pendingDelete = null; app.deleteVehicle(v.id) }) {
                    Txt(tr("Видалити", "Delete"), 14, FontWeight.Bold, Obd.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Txt(tr("Скасувати", "Cancel"), 14, FontWeight.Bold, Obd.muted)
                }
            },
        )
    }
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
            Txt(tr("Гараж", "Garage"), 30, FontWeight.ExtraBold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HelpButton { showHelp = true }
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Obd.accent)
                    .clickable { app.openAddCar() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = tr("Додати авто", "Add car"),
                    tint = Obd.onAccent,
                )
            }
            }
        }
        if (showHelp) ConnectHelpDialog { showHelp = false }

        val err = app.vehiclesError
        when {
            err != null -> {
                ErrorBox(err)
                OutlineButton(tr("Спробувати ще раз", "Try again"), { app.loadVehicles() })
            }
            app.vehiclesLoading && app.vehicles.isEmpty() -> {
                SkeletonCard(3)
                SkeletonCard(3)
            }
            app.vehicles.isEmpty() -> {
                ObdCard {
                    Box(
                        Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Obd.accentSoft),
                        contentAlignment = Alignment.Center,
                    ) { CarIcon(Obd.accent, Modifier.size(34.dp)) }
                    Txt(tr("У гаражі поки порожньо", "Your garage is empty"), 18, FontWeight.ExtraBold)
                    Txt(
                        tr(
                            "Додай своє авто, щоб записувати поїздки та стежити за його станом.",
                            "Add your car to record trips and track its condition.",
                        ),
                        14, color = Obd.muted, lineHeight = 20,
                    )
                    PrimaryButton(tr("Додати авто", "Add a car"), { app.openAddCar() })
                }
            }
            else -> {
                app.vehicles.forEachIndexed { i, v ->
                    Appear(minOf(i, 6)) {
                        SwipeToReveal(tr("Видалити", "Delete"), onAction = { pendingDelete = v }) {
                            VehicleCard(app, v, v.id == app.selectedVehicleId)
                        }
                    }
                }
                Txt(
                    tr("Свайпни картку вліво, щоб видалити авто.", "Swipe a card left to delete the car."),
                    12, color = Obd.muted,
                )
                Txt(
                    tr(
                        "Пробіг розрахунковий: початкове значення плюс відстань поїздок, порахована за швидкістю. Це наближена оцінка.",
                        "Mileage is an estimate: the starting value plus trip distance calculated from speed. It is approximate.",
                    ),
                    12, color = Obd.muted, lineHeight = 17,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun VehicleCard(app: AppState, v: VehicleInfo, selected: Boolean) {
    val lang = LocalLang.current
    ObdCard(
        onClick = {
            app.selectVehicle(v.id)
            app.screen = Screen.Trips
        },
        borderColor = if (selected) Obd.accent else Obd.border,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Obd.accentSoft),
                contentAlignment = Alignment.Center,
            ) { CarIcon(Obd.accent, Modifier.size(30.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Txt("${v.make} ${v.model}", 17, FontWeight.ExtraBold)
                val engine = v.engineVolL?.let { " · ${it} л".replace(" л", if (lang == "en") " L" else " л") } ?: ""
                Txt("${v.year} · ${fuelLabel(v.fuel)}$engine", 13, color = Obd.muted)
            }
            val h = v.lastHealth
            if (h != null) {
                HealthRing(h, healthColor(h), Obd.surface2, Modifier.size(64.dp))
            }
        }
        Line()
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Txt(
                    "≈ ${fmtInt(v.odometerEstKm)} " + tr("км", "km"),
                    16, FontWeight.Bold, mono = true,
                )
                Txt(tr("розрахунковий пробіг", "estimated mileage"), 11, color = Obd.muted)
            }
            Column(horizontalAlignment = Alignment.End) {
                Txt("${v.tripsCount}", 16, FontWeight.Bold, mono = true)
                Txt(tr("поїздок", "trips"), 11, color = Obd.muted)
            }
        }
        val need = 3
        val learned = v.baselineTrips.coerceAtMost(need)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Txt(tr("Навчання норми авто", "Learning the car's baseline"), 12, FontWeight.SemiBold, Obd.muted)
                Txt("$learned / $need", 12, FontWeight.Bold, Obd.muted, mono = true)
            }
            ProgressBar(learned / need.toFloat(), if (learned >= need) Obd.good else Obd.accent)
        }
        if (v.lastTripAt != null) {
            Txt(tr("Остання поїздка: ", "Last trip: ") + fmtDate(v.lastTripAt, lang), 12, color = Obd.muted)
        }
        if (v.lastTripAt == null) {
            Txt(
                tr("Перші 3 поїздки (від 5 хв) формують норму авто.", "The first 3 trips (5+ min each) form the car's baseline."),
                12, color = Obd.muted, lineHeight = 17,
            )
        }
    }
}

/** Блок-пояснення: навіщо застосунку знати пробіг. */
@Composable
private fun OdoHint() {
    ObdCard(bg = Obd.aiBg, borderColor = Obd.aiBorder) {
        Txt(tr("Навіщо потрібен пробіг?", "Why do we need the mileage?"), 14, FontWeight.ExtraBold, Obd.aiTitle)
        Txt(
            tr(
                "Знос вузлів залежить від пробігу, а не лише від віку авто. З ним ми точніше порівнюємо показники з нормою для такої машини, краще прогнозуємо несправності й нагадуємо про ТО. Далі пробіг рахується автоматично за поїздками, це наближена оцінка.",
                "Wear depends on mileage, not only on the car's age. With it we compare readings with the norm for such a car more accurately, forecast faults better and remind you about service. After that mileage is calculated from trips automatically; it is an approximation.",
            ),
            13, lineHeight = 19,
        )
    }
}

/** Додавання авто: за VIN-кодом або вручну (без перевірки). */
@Composable
fun AddCarScreen(app: AppState) {
    val cur = remember { Calendar.getInstance().get(Calendar.YEAR) }
    var mode by remember { mutableStateOf(0) } // 0 = за номером, 1 = за VIN, 2 = вручну
    var plate by remember { mutableStateOf("") }
    var make by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var year by remember { mutableStateOf(cur.toString()) }
    var fuel by remember { mutableStateOf("PETROL") }
    var engine by remember { mutableStateOf("") }
    var vin by remember { mutableStateOf("") }
    var odo by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        app.clearVin()
        app.loadMakes()
    }
    // коли марка збігається з довідником, підтягуємо її моделі
    LaunchedEffect(make, app.makes) {
        if (app.makes.any { it.equals(make.trim(), ignoreCase = true) }) app.loadModels(make.trim())
    }
    // знайдений авто живе лише у своїй картці; тільки паливо підставляємо в чипи
    val found = app.vinResult
    LaunchedEffect(found) { found?.fuel?.let { fuel = it } }

    val msgPlate = tr("Введи державний номер", "Enter the licence plate")
    val msgBadMake = tr("Такої марки немає в довіднику. Вибери марку зі списку підказок.", "This make is not in the catalog. Pick a make from the suggestions.")
    val msgBadModel = tr("Такої моделі немає в цієї марки. Вибери модель зі списку підказок.", "This make has no such model. Pick a model from the suggestions.")
    val msgMake = tr("Вкажи марку і модель", "Enter make and model")
    val msgYear = tr("Рік має бути від 1980 до 2100", "Year must be between 1980 and 2100")
    val msgEngine = tr("Об'єм двигуна: число від 0 до 9.9", "Engine volume: a number from 0 to 9.9")
    val msgVin = tr("VIN має містити 17 символів (латиниця й цифри, без I, O, Q)", "VIN must be 17 characters (letters and digits, no I, O, Q)")
    val msgOdo = tr("Пробіг: ціле число від 0", "Mileage: a whole number, 0 or more")

    fun save() {
        localError = null
        val y = year.trim().toIntOrNull()
        val o = if (odo.isBlank()) 0 else odo.trim().toIntOrNull()
        val e = if (engine.isBlank()) null else engine.trim().replace(',', '.').toDoubleOrNull()
        val v = vin.trim().uppercase()
        val canonMake = app.makes.firstOrNull { it.equals(make.trim(), ignoreCase = true) }
        val canonModel = app.models.firstOrNull { it.equals(model.trim(), ignoreCase = true) }
        when {
            make.isBlank() || model.isBlank() -> localError = msgMake
            app.makes.isNotEmpty() && canonMake == null -> localError = msgBadMake
            app.models.isNotEmpty() && canonModel == null -> localError = msgBadModel
            y == null || y !in 1980..2100 -> localError = msgYear
            engine.isNotBlank() && (e == null || e < 0 || e > 9.9) -> localError = msgEngine
            v.isNotEmpty() && !Regex("^[A-HJ-NPR-Z0-9]{17}$").matches(v) -> localError = msgVin
            o == null || o < 0 -> localError = msgOdo
            else -> app.addVehicle(canonMake ?: make.trim(), canonModel ?: model.trim(), y, fuel, e, v.ifEmpty { null }, o)
        }
    }

    fun saveFound() {
        localError = null
        val f = app.vinResult ?: return
        val o = if (odo.isBlank()) 0 else odo.trim().toIntOrNull()
        if (o == null || o < 0) {
            localError = msgOdo
            return
        }
        val fv = f.vin.ifBlank { if (mode == 1) vin.trim().uppercase() else "" }
        app.addVehicle(f.make ?: "", f.model ?: "", f.year ?: cur, fuel, f.engineVolL, fv.ifEmpty { null }, o, f.color)
    }

    fun resetForm() {
        plate = ""; vin = ""; make = ""; model = ""; year = cur.toString()
        fuel = "PETROL"; engine = ""; odo = ""; localError = null
        app.clearVin()
    }

    Column(Modifier.fillMaxSize()) {
        BackHeader(tr("Нове авто", "New car"), onBack = { app.back() })
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Segmented(
                listOf(tr("За номером", "By plate"), tr("За VIN", "By VIN"), tr("Вручну", "Manually")),
                mode,
            ) { resetForm(); mode = it }

            if (mode != 2) {
                // ---------- пошук за номером або VIN ----------
                if (mode == 0) {
                    ObdField(plate, { plate = it.uppercase(); app.clearVin() }, tr("Державний номер", "Licence plate"), capitalizeAll = true)
                    Txt(
                        tr(
                            "Наприклад AA1234BB. Дані про авто підтягнуться з реєстру МВС України.",
                            "For example AA1234BB. Car data is pulled from the Ukrainian registry.",
                        ),
                        12, color = Obd.muted, lineHeight = 17,
                    )
                } else {
                    ObdField(vin, { vin = it.uppercase(); app.clearVin() }, "VIN", capitalizeAll = true)
                    Txt(
                        tr(
                            "VIN: 17 символів, він є в техпаспорті та на табличці під лобовим склом. Дані про авто підтягнуться з міжнародної бази.",
                            "VIN: 17 characters, found in the registration document and on a plate under the windshield. Car data is pulled from an international database.",
                        ),
                        12, color = Obd.muted, lineHeight = 17,
                    )
                }
                PrimaryButton(
                    if (app.vinBusy) tr("Шукаю…", "Searching…") else tr("Знайти авто", "Find the car"),
                    onClick = {
                        localError = null
                        if (mode == 0) {
                            if (plate.trim().length < 5) localError = msgPlate
                            else if (!app.vinBusy) app.lookupPlate(plate)
                        } else {
                            val v = vin.trim().uppercase()
                            if (!Regex("^[A-HJ-NPR-Z0-9]{17}$").matches(v)) localError = msgVin
                            else if (!app.vinBusy) app.decodeVin(v)
                        }
                    },
                )
                app.vinError?.let { ErrorBox(it) }

                if (found != null) {
                    val complete = found.make != null && found.model != null && found.year != null
                    if (!complete) {
                        val missing = tr("не знайдено", "not found")
                        ObdCard(bg = Obd.warnSoft, borderColor = Obd.warnSoft) {
                            Txt(
                                tr("Знайдено лише частину даних", "Only part of the data was found"),
                                14, FontWeight.ExtraBold, Obd.warnText,
                            )
                            val rows = listOf(
                                tr("Марка", "Make") to found.make,
                                tr("Модель", "Model") to found.model,
                                tr("Рік", "Year") to found.year?.toString(),
                                tr("Паливо", "Fuel") to found.fuel?.let { fuelLabel(it) },
                                tr("Об'єм двигуна", "Engine volume") to found.engineVolL?.let { "$it " + tr("л", "L") },
                                "VIN" to found.vin.ifBlank { null },
                            )
                            rows.forEach { (label, value) ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Txt(label, 13, color = Obd.warnText)
                                    Txt(
                                        value ?: missing, 13, FontWeight.Bold,
                                        if (value != null) Obd.ink else Obd.danger,
                                    )
                                }
                            }
                            Txt(
                                tr("Решту потрібно ввести вручну, знайдене підставиться само.",
                                    "Enter the rest manually; what was found will be filled in."),
                                12, color = Obd.warnText, lineHeight = 17,
                            )
                        }
                        OutlineButton(tr("Заповнити вручну", "Fill in manually"), {
                            make = found.make ?: ""
                            model = found.model ?: ""
                            found.year?.let { year = it.toString() }
                            found.engineVolL?.let { engine = it.toString() }
                            if (mode == 1) vin = vin.trim().uppercase() else vin = found.vin
                            app.clearVin()
                            mode = 2
                        })
                    } else {
                        ObdCard(bg = Obd.goodSoft, borderColor = Obd.goodSoft) {
                            Txt(tr("Знайдено", "Found"), 12, FontWeight.Bold, Obd.good)
                            Txt("${found.make} ${found.model}", 20, FontWeight.ExtraBold, Obd.ink)
                            val eng = found.engineVolL?.let { " · $it " + tr("л", "L") } ?: ""
                            Txt("${found.year}$eng", 14, color = Obd.infoText)
                        }
                        Txt(tr("Паливо", "Fuel"), 14, FontWeight.Bold)
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf("PETROL", "DIESEL", "HYBRID", "ELECTRIC", "LPG").forEach {
                                Chip(fuelLabel(it), fuel == it) { fuel = it }
                            }
                        }
                        ObdField(odo, { odo = it }, tr("Пробіг на панелі, км", "Odometer reading, km"), keyboard = KeyboardType.Number)
                        OdoHint()
                        (localError ?: app.addError)?.let { ErrorBox(it) }
                        PrimaryButton(
                            if (app.addBusy) tr("Зберігаю…", "Saving…") else tr("Додати це авто", "Add this car"),
                            onClick = { if (!app.addBusy) saveFound() },
                        )
                    }
                }
                if (found == null) localError?.let { ErrorBox(it) }
            } else {
                // ---------- ручне введення ----------
                SuggestField(make, { make = it }, tr("Марка", "Make"), app.makes)
                SuggestField(model, { model = it }, tr("Модель", "Model"), app.models)
                if (app.catalogError != null) {
                    Txt(
                        tr("Підказки недоступні, можна вводити вручну.", "Suggestions are unavailable, you can type manually."),
                        12, color = Obd.muted,
                    )
                }

                Txt(tr("Рік випуску", "Year"), 14, FontWeight.Bold)
                val yInt = (year.toIntOrNull() ?: cur).coerceIn(1980, cur + 1)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ObdField(year, { year = it.filter { c -> c.isDigit() }.take(4) }, tr("Рік", "Year"),
                        modifier = Modifier.width(104.dp), keyboard = KeyboardType.Number)
                    Spacer(Modifier.width(12.dp))
                    Slider(
                        value = yInt.toFloat(),
                        onValueChange = { year = it.roundToInt().toString() },
                        valueRange = 1980f..(cur + 1).toFloat(),
                        steps = cur + 1 - 1980 - 1,
                        colors = SliderDefaults.colors(
                            thumbColor = Obd.accent,
                            activeTrackColor = Obd.accent,
                            inactiveTrackColor = Obd.surface2,
                            activeTickColor = Obd.accent,
                            inactiveTickColor = Obd.surface2,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                }

                Txt(tr("Паливо", "Fuel"), 14, FontWeight.Bold)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf("PETROL", "DIESEL", "HYBRID", "ELECTRIC", "LPG").forEach {
                        Chip(fuelLabel(it), fuel == it) { fuel = it }
                    }
                }

                ObdField(engine, { engine = it }, tr("Об'єм двигуна, л (необов'язково)", "Engine volume, L (optional)"), keyboard = KeyboardType.Decimal)
                ObdField(vin, { vin = it.uppercase() }, tr("VIN (необов'язково)", "VIN (optional)"), capitalizeAll = true)
                ObdField(odo, { odo = it }, tr("Пробіг на панелі, км", "Odometer reading, km"), keyboard = KeyboardType.Number)
                OdoHint()

                (localError ?: app.addError)?.let { ErrorBox(it) }
                PrimaryButton(
                    if (app.addBusy) tr("Зберігаю…", "Saving…") else tr("Зберегти", "Save"),
                    onClick = { if (!app.addBusy) save() },
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
