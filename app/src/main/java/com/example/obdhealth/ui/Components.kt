package com.example.obdhealth.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Текст з нашими типовими параметрами. */
@Composable
fun Txt(
    text: String,
    size: Int = 14,
    weight: FontWeight = FontWeight.Normal,
    color: Color = Obd.ink,
    mono: Boolean = false,
    modifier: Modifier = Modifier,
    lineHeight: Int? = null,
) {
    Text(
        text = text,
        color = color,
        fontSize = size.sp,
        fontWeight = weight,
        fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
        lineHeight = if (lineHeight != null) lineHeight.sp else TextUnit.Unspecified,
        modifier = modifier,
    )
}

/** Картка з рамкою. Якщо передати onClick, на неї можна натискати. */
@Composable
fun ObdCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    bg: Color = Obd.surface,
    borderColor: Color = Obd.border,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    var m = modifier
        .fillMaxWidth()
        .clip(shape)
        .background(bg)
        .border(1.dp, borderColor, shape)
    if (onClick != null) {
        m = m.clickable(onClick = onClick)
    }
    Column(
        modifier = m.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/** Тонка лінія-роздільник. */
@Composable
fun Line() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Obd.border)
    )
}

/** Маленька позначка (Увага, Інфо тощо). */
@Composable
fun Tag(text: String, bg: Color, fg: Color, size: Int = 11) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Txt(text, size, FontWeight.Bold, fg)
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    bg: Color = Obd.accent,
    fg: Color = Obd.onAccent,
    icon: ImageVector? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Txt(text, 16, FontWeight.ExtraBold, fg)
    }
}

@Composable
fun OutlineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(shape)
            .background(Obd.surface)
            .border(1.5.dp, Obd.accent, shape)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Txt(text, 16, FontWeight.Bold, Obd.accent)
    }
}

/** Верхня панель зі стрілкою «назад». */
@Composable
fun BackHeader(title: String, onBack: () -> Unit, subtitle: String? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Назад", "Back"), tint = Obd.ink)
        }
        Column {
            Txt(title, 18, FontWeight.ExtraBold)
            if (subtitle != null) {
                Txt(subtitle, 12, color = Obd.muted)
            }
        }
    }
}

@Composable
fun healthColor(value: Int): Color = when {
    value >= 80 -> Obd.good
    value >= 50 -> Obd.warn
    else -> Obd.danger
}

@Composable
fun healthLabel(value: Int): String = when {
    value >= 80 -> tr("Добре", "Good")
    value >= 50 -> tr("Увага", "Attention")
    else -> tr("Погано", "Poor")
}

/** Проста іконка авто, намальована контуром. */
@Composable
fun CarIcon(tint: Color, modifier: Modifier = Modifier.size(26.dp)) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = w * 0.07f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val roof = Path().apply {
            moveTo(w * 0.17f, h * 0.62f)
            lineTo(w * 0.26f, h * 0.38f)
            lineTo(w * 0.74f, h * 0.38f)
            lineTo(w * 0.83f, h * 0.62f)
        }
        drawPath(roof, tint, style = stroke)
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.12f, h * 0.62f),
            size = Size(w * 0.76f, h * 0.16f),
            cornerRadius = CornerRadius(w * 0.05f),
            style = stroke,
        )
        drawCircle(tint, radius = w * 0.045f, center = Offset(w * 0.3f, h * 0.7f))
        drawCircle(tint, radius = w * 0.045f, center = Offset(w * 0.7f, h * 0.7f))
    }
}

/** Кільце індексу здоров'я з числом усередині. */
@Composable
fun HealthRing(value: Int, color: Color, track: Color, modifier: Modifier = Modifier.size(76.dp)) {
    val sweep by animateFloatAsState(value / 100f, tween(1000), label = "ring")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 8.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = track,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke),
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * sweep,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        // трицифрове «100» має вміщатися всередині кільця
        val fs = if (value >= 100) 19 else 26
        Txt(value.toString(), fs, FontWeight.SemiBold, color, mono = true)
    }
}

/**
 * Простий лінійний графік.
 * highlight: діапазон індексів, які малюємо кольором аномалії (з фоновою смугою).
 * threshold: горизонтальна пунктирна лінія норми.
 * projectionTo: значення, до якого йде пунктирний прогноз від останньої точки.
 */
@Composable
fun LineChart(
    values: List<Float>,
    minY: Float,
    maxY: Float,
    lineColor: Color,
    modifier: Modifier = Modifier,
    highlight: IntRange? = null,
    highlightColor: Color = Obd.danger,
    bandColor: Color = Obd.dangerSoft,
    threshold: Float? = null,
    thresholdColor: Color = Obd.muted,
    projectionTo: Float? = null,
    projectionColor: Color = Obd.warn,
    showDots: Boolean = false,
    selected: Int? = null,
    onSelect: ((Int) -> Unit)? = null,
    selectColor: Color = Obd.ink,
) {
    val n0 = values.size
    val hasProjection = projectionTo != null
    fun indexAt(x: Float, width: Float): Int {
        val plotW = if (hasProjection) width * 0.72f else width
        return ((x / plotW) * (n0 - 1)).roundToInt().coerceIn(0, n0 - 1)
    }
    var m = modifier
    if (onSelect != null && n0 >= 2) {
        m = m
            .pointerInput(n0, hasProjection) {
                detectTapGestures { pos -> onSelect(indexAt(pos.x, size.width.toFloat())) }
            }
            .pointerInput(n0, hasProjection) {
                detectDragGestures(
                    onDragStart = { pos -> onSelect(indexAt(pos.x, size.width.toFloat())) },
                    onDrag = { change, _ ->
                        change.consume()
                        onSelect(indexAt(change.position.x, size.width.toFloat()))
                    },
                )
            }
    }
    Canvas(m) {
        val w = size.width
        val h = size.height
        val pad = 10.dp.toPx()
        val n = values.size
        val plotW = if (projectionTo != null) w * 0.72f else w
        val lineWidth = 3.dp.toPx()
        val dash = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))

        fun xOf(i: Int): Float = plotW * i / (n - 1)
        fun yOf(v: Float): Float = h - pad - (v - minY) / (maxY - minY) * (h - 2 * pad)

        if (highlight != null) {
            val x1 = xOf(highlight.first)
            val x2 = xOf(highlight.last)
            drawRect(color = bandColor, topLeft = Offset(x1, 0f), size = Size(x2 - x1, h))
        }
        if (threshold != null) {
            drawLine(
                color = thresholdColor,
                start = Offset(0f, yOf(threshold)),
                end = Offset(w, yOf(threshold)),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = dash,
            )
        }

        val path = Path()
        for (i in 0 until n) {
            if (i == 0) path.moveTo(xOf(i), yOf(values[i])) else path.lineTo(xOf(i), yOf(values[i]))
        }
        drawPath(path, lineColor, style = Stroke(width = lineWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))

        if (highlight != null) {
            val hp = Path()
            var started = false
            for (i in highlight) {
                if (i in 0 until n) {
                    if (!started) {
                        hp.moveTo(xOf(i), yOf(values[i]))
                        started = true
                    } else {
                        hp.lineTo(xOf(i), yOf(values[i]))
                    }
                }
            }
            drawPath(hp, highlightColor, style = Stroke(width = lineWidth + 1f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }

        if (showDots) {
            for (i in 0 until n) {
                drawCircle(lineColor, radius = 4.dp.toPx(), center = Offset(xOf(i), yOf(values[i])))
            }
        }

        if (selected != null && selected in 0 until n) {
            val sx = xOf(selected)
            val sy = yOf(values[selected])
            drawLine(
                color = selectColor.copy(alpha = 0.45f),
                start = Offset(sx, 0f),
                end = Offset(sx, h),
                strokeWidth = 1.5.dp.toPx(),
            )
            drawCircle(selectColor, radius = 7.dp.toPx(), center = Offset(sx, sy))
            drawCircle(lineColor, radius = 4.5.dp.toPx(), center = Offset(sx, sy))
        }

        if (projectionTo != null) {
            drawLine(
                color = projectionColor,
                start = Offset(xOf(n - 1), yOf(values[n - 1])),
                end = Offset(w, yOf(projectionTo)),
                strokeWidth = lineWidth,
                cap = StrokeCap.Round,
                pathEffect = dash,
            )
        }
    }
}

/** Нижня панель навігації: лише іконки (підписи тільки для озвучення). */
@Composable
fun ObdBottomBar(current: Screen, onSelect: (Screen) -> Unit) {
    val items = listOf(
        Triple(Screen.Garage, tr("Гараж", "Garage"), Icons.Default.Home),
        Triple(Screen.Trips, tr("Поїздки", "Trips"), Icons.Default.DateRange),
        Triple(Screen.Forecast, tr("Прогноз", "Forecast"), Icons.Default.Info),
        Triple(Screen.Settings, tr("Налаштування", "Settings"), Icons.Default.Settings),
    )
    NavigationBar(containerColor = Obd.surface, contentColor = Obd.ink) {
        items.forEach { (screen, label, icon) ->
            NavigationBarItem(
                selected = current == screen,
                onClick = { onSelect(screen) },
                icon = { Icon(icon, contentDescription = label) },
                label = null,
                alwaysShowLabel = false,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Obd.accent,
                    indicatorColor = Obd.accentSoft,
                    unselectedIconColor = Obd.muted,
                ),
            )
        }
    }
}

/** Переключач-«таблетка» (вибір однієї з кількох опцій). */
@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        Modifier
            .clip(shape)
            .background(if (selected) Obd.accentSoft else Obd.surface)
            .border(1.dp, if (selected) Obd.accent else Obd.border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Txt(text, 14, FontWeight.Bold, if (selected) Obd.accent else Obd.ink)
    }
}

/** Смужка прогресу 0..1. */
@Composable
fun ProgressBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(600), label = "progress")
    Box(
        modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Obd.surface2)
    ) {
        Box(
            Modifier
                .fillMaxWidth(f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color)
        )
    }
}

/** Поле вводу в стилі застосунку. */
@Composable
fun ObdField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
    capitalizeAll: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboard,
            capitalization = if (capitalizeAll) KeyboardCapitalization.Characters else KeyboardCapitalization.None,
        ),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Obd.ink,
            unfocusedTextColor = Obd.ink,
            focusedBorderColor = Obd.accent,
            unfocusedBorderColor = Obd.border,
            focusedLabelColor = Obd.accent,
            unfocusedLabelColor = Obd.muted,
            cursorColor = Obd.accent,
            focusedContainerColor = Obd.surface,
            unfocusedContainerColor = Obd.surface,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Блок із повідомленням про помилку. */
@Composable
fun ErrorBox(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Obd.dangerSoft)
            .padding(12.dp)
    ) {
        Txt(text, 13, FontWeight.SemiBold, Obd.danger, lineHeight = 18)
    }
}


/** Плавна поява елемента зі зсувом (для списків). */
@Composable
fun Appear(index: Int, content: @Composable () -> Unit) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * 70L)
        a.animateTo(1f, tween(380))
    }
    Box(
        Modifier.graphicsLayer {
            alpha = a.value
            translationY = (1f - a.value) * 36f
        }
    ) { content() }
}

/** Пульсуюча крапка запису. */
@Composable
fun PulseDot(color: Color, size: Int = 10) {
    val t = rememberInfiniteTransition(label = "pulse")
    val a by t.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulseAlpha",
    )
    Box(
        Modifier
            .size(size.dp)
            .alpha(a)
            .clip(CircleShape)
            .background(color)
    )
}

/** Кругла кнопка зі знаком питання. */
@Composable
fun HelpButton(onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Obd.surface)
            .border(1.dp, Obd.border, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Txt("?", 20, FontWeight.ExtraBold, Obd.accent)
    }
}

/** Інструкція: як підключити Bluetooth-адаптер ELM327. */
@Composable
fun ConnectHelpDialog(onDismiss: () -> Unit) {
    val steps = listOf(
        tr("Встав адаптер ELM327 у діагностичний роз'єм OBD2. Зазвичай він під кермом, біля педалей.",
            "Plug the ELM327 adapter into the OBD2 port. It is usually under the steering wheel, near the pedals."),
        tr("Увімкни запалювання. Для запису поїздки двигун краще завести.",
            "Turn the ignition on. For recording a trip it is best to start the engine."),
        tr("Увімкни Bluetooth на телефоні, знайди пристрій OBDII, V-LINK або ELM327 і з'єднай його. PIN-код зазвичай 1234 або 0000.",
            "Turn on Bluetooth on your phone, find the device OBDII, V-LINK or ELM327 and pair it. The PIN is usually 1234 or 0000."),
        tr("Відкрий застосунок, вибери авто в гаражі й натисни «Почати поїздку».",
            "Open the app, pick your car in the garage and tap “Start trip”."),
        tr("Після поїздки виймай адаптер: дешеві моделі не засинають і за кілька днів можуть розрядити акумулятор.",
            "Unplug the adapter after the trip: cheap models never sleep and can drain the battery in a few days."),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Obd.surface,
        title = { Txt(tr("Як підключити адаптер", "How to connect the adapter"), 18, FontWeight.ExtraBold) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                steps.forEachIndexed { i, s ->
                    Row(verticalAlignment = Alignment.Top) {
                        Box(
                            Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Obd.accentSoft),
                            contentAlignment = Alignment.Center,
                        ) { Txt("${i + 1}", 12, FontWeight.ExtraBold, Obd.accent) }
                        Spacer(Modifier.width(10.dp))
                        Txt(s, 14, lineHeight = 20, modifier = Modifier.weight(1f))
                    }
                }
                Txt(
                    tr("Зараз підключення по Bluetooth ще в розробці, тому поїздка симулюється.",
                        "Bluetooth connection is still in development, so the trip is simulated for now."),
                    12, FontWeight.SemiBold, Obd.warnText, lineHeight = 17,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Txt(tr("Зрозуміло", "Got it"), 14, FontWeight.Bold, Obd.accent) }
        },
    )
}

/** Поле з підказками: пише користувач, знизу пропонуються варіанти зі списку. */
@Composable
fun SuggestField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    options: List<String>,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val q = value.trim()
    val matches = if (q.isEmpty()) emptyList() else {
        val starts = options.filter { it.startsWith(q, ignoreCase = true) }
        val contains = options.filter { !it.startsWith(q, ignoreCase = true) && it.contains(q, ignoreCase = true) }
        (starts + contains).take(6)
    }
    val exact = matches.any { it.equals(q, ignoreCase = true) }
    Column(modifier.fillMaxWidth()) {
        ObdField(value, onChange, label, modifier = Modifier.onFocusChanged { focused = it.isFocused })
        if (focused && matches.isNotEmpty() && !exact) {
            val shape = RoundedCornerShape(14.dp)
            Column(
                Modifier
                    .padding(top = 4.dp)
                    .fillMaxWidth()
                    .clip(shape)
                    .background(Obd.surface)
                    .border(1.dp, Obd.border, shape)
            ) {
                matches.forEach { opt ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onChange(opt) }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) { Txt(opt, 15, FontWeight.SemiBold) }
                }
            }
        }
    }
}


/** Мерехтлива сіра плашка-заглушка на час завантаження. */
@Composable
fun SkeletonBlock(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "skeleton")
    val a by t.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
        label = "skeletonAlpha",
    )
    Box(
        modifier
            .alpha(a)
            .clip(RoundedCornerShape(8.dp))
            .background(Obd.surface2)
    )
}

/** Картка-заглушка: заголовок і кілька рядків. */
@Composable
fun SkeletonCard(lines: Int = 3) {
    ObdCard {
        SkeletonBlock(Modifier.fillMaxWidth(0.55f).height(20.dp))
        repeat(lines) { i ->
            SkeletonBlock(Modifier.fillMaxWidth(if (i == lines - 1) 0.4f else 0.9f).height(14.dp))
        }
    }
}

/** Стрілковий прилад (тахометр або спідометр). */
@Composable
fun Gauge(
    value: Float,
    max: Float,
    label: String,
    unit: String,
    valueText: String,
    redFrom: Float? = null,
    modifier: Modifier = Modifier,
) {
    val frac by animateFloatAsState((value / max).coerceIn(0f, 1f), tween(350), label = "gauge")
    val track = Obd.surface2
    val accent = Obd.accent
    val red = Obd.danger
    val ink = Obd.ink
    val muted = Obd.muted
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Obd.surface)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.15f), contentAlignment = Alignment.BottomCenter) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 9.dp.toPx()
                val d = minOf(size.width, size.height * 1.15f) - stroke * 2
                val c = Offset(size.width / 2, size.height * 0.52f)
                val r = d / 2
                val tl = Offset(c.x - r, c.y - r)
                val sz = Size(d, d)
                drawArc(track, 135f, 270f, false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
                drawArc(accent, 135f, 270f * frac, false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
                if (redFrom != null) {
                    val rf = (redFrom / max).coerceIn(0f, 1f)
                    drawArc(red.copy(alpha = 0.55f), 135f + 270f * rf, 270f * (1f - rf), false, tl, sz,
                        style = Stroke(stroke * 0.45f))
                }
                // поділки
                for (i in 0..10) {
                    val ang = Math.toRadians((135.0 + 27.0 * i))
                    val r1 = r - stroke * 1.1f
                    val r2 = r - stroke * (if (i % 5 == 0) 2.0f else 1.6f)
                    drawLine(
                        muted,
                        Offset(c.x + r1 * cos(ang).toFloat(), c.y + r1 * sin(ang).toFloat()),
                        Offset(c.x + r2 * cos(ang).toFloat(), c.y + r2 * sin(ang).toFloat()),
                        strokeWidth = 1.5.dp.toPx(),
                    )
                }
                // стрілка
                val ang = Math.toRadians(135.0 + 270.0 * frac)
                val len = r - stroke * 2.2f
                drawLine(
                    ink,
                    c,
                    Offset(c.x + len * cos(ang).toFloat(), c.y + len * sin(ang).toFloat()),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                drawCircle(ink, 6.dp.toPx(), c)
                drawCircle(track, 2.5.dp.toPx(), c)
            }
            Txt(valueText, 20, FontWeight.SemiBold, mono = true)
        }
        Txt(label, 12, FontWeight.SemiBold, Obd.muted)
        if (unit.isNotEmpty()) Txt(unit, 11, color = Obd.muted)
    }
}

/** Картка, яку можна свайпнути вліво, щоб відкрити червону дію (наприклад «Видалити»). */
@Composable
fun SwipeToReveal(
    actionLabel: String,
    onAction: () -> Unit,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val maxPx = with(density) { 108.dp.toPx() }
    val x = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val shape = RoundedCornerShape(18.dp)
    Box(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .matchParentSize()
                .clip(shape)
                .background(Obd.danger),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Box(
                Modifier
                    .width(108.dp)
                    .fillMaxSize()
                    .clickable {
                        scope.launch { x.animateTo(0f, tween(250)) }
                        onAction()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Txt(actionLabel, 14, FontWeight.ExtraBold, Obd.bg)
            }
        }
        Box(
            Modifier
                .offset { IntOffset(x.value.roundToInt(), 0) }
                .pointerInput(maxPx) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch { x.animateTo(if (x.value < -maxPx / 2) -maxPx else 0f, tween(220)) }
                        },
                        onDragCancel = { scope.launch { x.animateTo(0f, tween(220)) } },
                        onHorizontalDrag = { change, dx ->
                            change.consume()
                            scope.launch { x.snapTo((x.value + dx).coerceIn(-maxPx, 0f)) }
                        },
                    )
                }
        ) { content() }
    }
}
