package com.example.obdhealth.data

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Симуляція поїздки. Поки Bluetooth-адаптер ELM327 не підключено,
 * показники генеруються, але поїздка реально зберігається на сервері.
 * Одна симульована секунда = один відлік.
 */
object TripSim {
    data class Sample(
        val rpm: Int,
        val speed: Int,
        val coolant: Int,
        val load: Double,
        val throttle: Double,
        val maf: Double,
        val stft: Double,
        val ltft: Double,
        val battery: Double,
        val intake: Int,
    )

    /** fault: 0 норма, 1 перегрів, 2 велика корекція палива, 3 слабкий акумулятор. */
    fun sample(i: Int, rnd: Random, fault: Int = 0): Sample {
        val speed = max(0, (48 + 38 * sin(i / 45.0) + rnd.nextDouble(-3.0, 3.0)).roundToInt())
        var coolant = min(91, (24 + i * 0.26 + rnd.nextDouble(-0.6, 0.6)).roundToInt())
        if (fault == 1) coolant = min(121, coolant + (max(0, i - 120) * 0.18).roundToInt())
        val rpm = (780 + speed * 27 + rnd.nextInt(-40, 40)).coerceAtLeast(700)
        return Sample(
            rpm = rpm,
            speed = speed,
            coolant = coolant,
            load = r2(18 + speed * 0.42 + rnd.nextDouble(-2.0, 2.0)),
            throttle = r2(9 + speed * 0.28 + rnd.nextDouble(-1.5, 1.5)),
            maf = r2(3.2 + speed * 0.23 + rnd.nextDouble(-0.4, 0.4)),
            stft = r2(rnd.nextDouble(-2.5, 2.5)),
            ltft = r2((if (fault == 2) 7.8 + i * 0.008 else 1.8) + rnd.nextDouble(-0.3, 0.3)),
            battery = (if (fault == 3) 122 + rnd.nextInt(-2, 3) else 140 + rnd.nextInt(-3, 4)) / 10.0,
            intake = 27 + rnd.nextInt(-1, 3),
        )
    }

    private fun r2(v: Double) = (v * 100).roundToInt() / 100.0

    private fun iso(ms: Long): String {
        val f = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        f.timeZone = TimeZone.getTimeZone("UTC")
        return f.format(Date(ms))
    }

    /** Збирає JSON для POST /trips. Час початку відраховуємо назад від «зараз». */
    fun buildPayload(vehicleId: Long, samples: List<Sample>): JSONObject {
        val n = samples.size
        val startMs = System.currentTimeMillis() - n * 1000L
        val readings = JSONArray()
        var distance = 0.0
        samples.forEachIndexed { i, s ->
            distance += s.speed / 3600.0
            readings.put(
                JSONObject()
                    .put("ts", iso(startMs + i * 1000L))
                    .put("rpm", s.rpm)
                    .put("speed_kmh", s.speed)
                    .put("coolant_temp_c", s.coolant)
                    .put("engine_load_pct", s.load)
                    .put("throttle_pct", s.throttle)
                    .put("maf_gs", s.maf)
                    .put("stft_pct", s.stft)
                    .put("ltft_pct", s.ltft)
                    .put("battery_v", s.battery)
                    .put("intake_temp_c", s.intake)
            )
        }
        val endMs = startMs + (n - 1) * 1000L
        val events = JSONArray()
            .put(
                JSONObject().put("ts", iso(startMs)).put("event_type", "TRIP_STARTED")
                    .put("severity", "INFO").put("message", "Simulated trip started")
            )
            .put(
                JSONObject().put("ts", iso(endMs)).put("event_type", "TRIP_FINISHED")
                    .put("severity", "INFO").put("message", "Finished by user")
            )
        return JSONObject()
            .put("client_trip_id", UUID.randomUUID().toString())
            .put("vehicle_id", vehicleId)
            .put("started_at", iso(startMs))
            .put("ended_at", iso(endMs))
            .put("exit_reason", "USER_STOPPED")
            .put("distance_km", r2(distance))
            .put("readings", readings)
            .put("events", events)
    }
}
