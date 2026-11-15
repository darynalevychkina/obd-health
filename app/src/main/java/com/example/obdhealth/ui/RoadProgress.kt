package com.example.obdhealth.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.sin

/** Вечір і ніч за годинником телефона: з 18:00 до 7:00. */
fun isNightNow(): Boolean {
    val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return h >= 18 || h < 7
}

/** Смужка прогресу, по якій їде авто. Увечері й уночі в авто вмикаються фари. */
@Composable
fun RoadProgress(fraction: Float, color: Color, modifier: Modifier = Modifier, carColor: Color? = null) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(500), label = "roadProgress")
    var night by remember { mutableStateOf(isNightNow()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            night = isNightNow()
        }
    }
    val carW = 46.dp
    BoxWithConstraints(modifier.fillMaxWidth().height(38.dp)) {
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Obd.surface2)
        )
        Box(
            Modifier
                .align(Alignment.BottomStart)
                // лінія завжди доходить до переднього колеса авто, тож авто не «перегонить» її
                .width((maxWidth - carW) * f + carW * 0.76f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color)
        )
        Box(
            Modifier
                .align(Alignment.TopStart)
                .offset(x = (maxWidth - carW) * f)
                .size(carW, 28.dp)
        ) {
            CarSide(visibleBody(carColor ?: color), Obd.surface, moving = f < 1f, lights = night, modifier = Modifier.fillMaxSize())
        }
    }
}

/** Якщо колір авто майже збігається з фоном картки (чорне в темній темі, біле у світлій), трохи зсуваємо його. */
@Composable
fun visibleBody(c: Color): Color {
    val bg = Obd.surface.luminance()
    return if (kotlin.math.abs(c.luminance() - bg) < 0.06f) lerp(c, Obd.ink, 0.28f) else c
}

/** Авто збоку: кузов, вікна, колеса, що крутяться, і фари з променями (коли lights = true). */
@Composable
fun CarSide(body: Color, window: Color, moving: Boolean, lights: Boolean, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "wheels")
    val spin by t.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(550, easing = LinearEasing)),
        label = "wheelSpin",
    )
    val flicker by t.animateFloat(
        initialValue = 0.85f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "beamFlicker",
    )
    val beam by animateFloatAsState(if (lights) 1f else 0f, tween(700), label = "headlights")
    val angle = if (moving) spin else 0f
    val tire = Obd.ink
    val hub = Obd.muted
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val beamColor = Color(0xFFFFE9A6)
        // промені фар (поза межами малюнка вперед)
        if (beam > 0.01f) {
            val len = 64.dp.toPx()
            val path = Path().apply {
                moveTo(w * 0.95f, h * 0.52f)
                lineTo(w + len, h * 0.22f)
                lineTo(w + len, h * 0.86f)
                lineTo(w * 0.95f, h * 0.62f)
                close()
            }
            drawPath(
                path,
                Brush.linearGradient(
                    listOf(beamColor.copy(alpha = 0.7f * beam * flicker), beamColor.copy(alpha = 0f)),
                    start = Offset(w * 0.95f, h * 0.57f),
                    end = Offset(w + len, h * 0.57f),
                ),
            )
        }
        // кабіна
        val cabin = Path().apply {
            moveTo(w * 0.20f, h * 0.50f)
            lineTo(w * 0.33f, h * 0.12f)
            lineTo(w * 0.67f, h * 0.12f)
            lineTo(w * 0.82f, h * 0.50f)
            close()
        }
        drawPath(cabin, body)
        // кузов
        drawRoundRect(
            color = body,
            topLeft = Offset(w * 0.02f, h * 0.44f),
            size = Size(w * 0.96f, h * 0.34f),
            cornerRadius = CornerRadius(h * 0.14f),
        )
        // вікна
        val win1 = Path().apply {
            moveTo(w * 0.30f, h * 0.44f)
            lineTo(w * 0.38f, h * 0.20f)
            lineTo(w * 0.48f, h * 0.20f)
            lineTo(w * 0.48f, h * 0.44f)
            close()
        }
        val win2 = Path().apply {
            moveTo(w * 0.52f, h * 0.20f)
            lineTo(w * 0.64f, h * 0.20f)
            lineTo(w * 0.73f, h * 0.44f)
            lineTo(w * 0.52f, h * 0.44f)
            close()
        }
        drawPath(win1, window)
        drawPath(win2, window)
        // фара й задній ліхтар
        drawCircle(beamColor.copy(alpha = 0.3f + 0.7f * beam), radius = h * 0.07f, center = Offset(w * 0.94f, h * 0.55f))
        drawCircle(Color(0xFFFF5A4D).copy(alpha = 0.15f + 0.85f * beam), radius = h * 0.05f, center = Offset(w * 0.04f, h * 0.55f))
        // колеса
        for (cx in listOf(w * 0.26f, w * 0.76f)) {
            val c = Offset(cx, h * 0.78f)
            val r = h * 0.20f
            drawCircle(tire, r, c)
            drawCircle(hub, r * 0.55f, c)
            for (i in 0 until 3) {
                val a = Math.toRadians((angle + i * 120f).toDouble())
                drawLine(
                    tire,
                    c,
                    Offset(c.x + r * 0.55f * cos(a).toFloat(), c.y + r * 0.55f * sin(a).toFloat()),
                    strokeWidth = 1.6.dp.toPx(),
                )
            }
        }
    }
}
