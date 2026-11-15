package com.example.obdhealth.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/** Заставка: авто їздить по колу. */
@Composable
fun SplashScreen(lightsOn: Boolean) {
    val light by animateFloatAsState(if (lightsOn) 1f else 0f, tween(600), label = "lights")
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var start = 0L
        while (true) {
            withFrameNanos { now ->
                if (start == 0L) start = now
                t = (now - start) / 1_000_000_000f
            }
        }
    }

    val accent = Obd.accent
    val onAccent = Obd.onAccent
    val track = Obd.border
    val ink = Obd.ink

    Box(
        Modifier
            .fillMaxSize()
            .background(Obd.bg),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Canvas(Modifier.size(240.dp)) {
                val c = Offset(size.width / 2, size.height / 2)
                val r = size.minDimension * 0.36f
                val theta = t * 2.0f // радіанів за секунду

                // доріжка
                drawCircle(
                    color = track,
                    radius = r,
                    center = c,
                    style = Stroke(
                        width = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 14f)),
                    ),
                )
                // слід за авто
                val deg = Math.toDegrees(theta.toDouble()).toFloat()
                drawArc(
                    color = accent.copy(alpha = 0.35f),
                    startAngle = deg - 75f,
                    sweepAngle = 75f,
                    useCenter = false,
                    topLeft = Offset(c.x - r, c.y - r),
                    size = Size(r * 2, r * 2),
                    style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round),
                )

                // положення авто на колі
                val pos = Offset(c.x + r * cos(theta), c.y + r * sin(theta))
                val w = 20.dp.toPx()
                val h = 38.dp.toPx()
                // авто малюємо носом угору і повертаємо за дотичною до кола
                rotate(degrees = deg + 180f, pivot = pos) {
                    val left = pos.x - w / 2
                    val top = pos.y - h / 2
                    // світло фар (вмикається, коли застосунок готовий)
                    val beam = Color(0xFFFFE9A6)
                    val beamLen = 74.dp.toPx()
                    for (fx in listOf(left + w * 0.2f, left + w * 0.8f)) {
                        val path = Path().apply {
                            moveTo(fx - 3.dp.toPx(), top)
                            lineTo(fx - 15.dp.toPx(), top - beamLen)
                            lineTo(fx + 15.dp.toPx(), top - beamLen)
                            lineTo(fx + 3.dp.toPx(), top)
                            close()
                        }
                        drawPath(
                            path,
                            Brush.linearGradient(
                                listOf(beam.copy(alpha = 0f), beam.copy(alpha = 0.6f * light)),
                                start = Offset(fx, top - beamLen),
                                end = Offset(fx, top),
                            ),
                        )
                    }
                    // колеса
                    val wheelW = 4.dp.toPx()
                    val wheelH = 9.dp.toPx()
                    for (dx in listOf(-w / 2 - wheelW / 2 + 1f, w / 2 - wheelW / 2 - 1f)) {
                        for (dy in listOf(-h * 0.28f, h * 0.28f)) {
                            drawRoundRect(
                                color = ink,
                                topLeft = Offset(pos.x + dx, pos.y + dy - wheelH / 2),
                                size = Size(wheelW, wheelH),
                                cornerRadius = CornerRadius(2.dp.toPx()),
                            )
                        }
                    }
                    // кузов
                    drawRoundRect(
                        color = accent,
                        topLeft = Offset(left, top),
                        size = Size(w, h),
                        cornerRadius = CornerRadius(7.dp.toPx()),
                    )
                    // фари
                    for (fx in listOf(left + w * 0.2f, left + w * 0.8f)) {
                        drawCircle(Color(0xFFFFF3C4).copy(alpha = 0.25f + 0.75f * light), 2.6.dp.toPx(), Offset(fx, top + 3.dp.toPx()))
                    }
                    // лобове скло
                    drawRoundRect(
                        color = onAccent,
                        topLeft = Offset(left + w * 0.15f, top + h * 0.2f),
                        size = Size(w * 0.7f, h * 0.18f),
                        cornerRadius = CornerRadius(3.dp.toPx()),
                    )
                    // заднє скло
                    drawRoundRect(
                        color = onAccent,
                        topLeft = Offset(left + w * 0.18f, top + h * 0.68f),
                        size = Size(w * 0.64f, h * 0.12f),
                        cornerRadius = CornerRadius(3.dp.toPx()),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Txt("OBD Health", 28, FontWeight.ExtraBold)
            Spacer(Modifier.height(6.dp))
            Txt(
                tr("Стан вашого авто під контролем", "Your car's health, under control"),
                14,
                color = Obd.muted,
            )
        }
    }
}
