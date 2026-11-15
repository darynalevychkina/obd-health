package com.example.obdhealth.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale

/** Сервер відповів помилкою (код 4xx/5xx). */
class ApiException(val code: Int, message: String) : Exception(message)

/** Не вдалося достукатися до сервера. */
class NetworkException(message: String) : Exception(message)

data class UserInfo(val id: Long, val email: String, val locale: String, val theme: String)

data class VehicleInfo(
    val id: Long,
    val vin: String?,
    val make: String,
    val model: String,
    val year: Int,
    val fuel: String,
    val engineVolL: Double?,
    val odometerBaseKm: Int,
    val odometerEstKm: Int,
    val tripsCount: Int,
    val baselineTrips: Int,
    val lastTripAt: Long?,
    val lastHealth: Int?,
    val colorHex: String? = null,
)

data class TripInfo(
    val id: Long,
    val vehicleId: Long,
    val startedAt: Long,
    val endedAt: Long?,
    val exitReason: String?,
    val distanceKm: Double,
    val readingsCount: Int,
    val countsForBaseline: Boolean,
    val mode: String,
    val healthIndex: Int?,
) {
    val durationSec: Long
        get() = if (endedAt != null) (endedAt - startedAt) / 1000 else 0L
}

data class TripEventInfo(val ts: Long, val type: String, val severity: String, val message: String?)

data class SamplePoint(val tSec: Double, val coolant: Int?, val speed: Int?, val rpm: Int?, val load: Double?)

data class TripDetail(val trip: TripInfo, val events: List<TripEventInfo>, val samples: List<SamplePoint>)

/** Результат розшифровки VIN. */
data class VinInfo(
    val vin: String, val make: String?, val model: String?, val year: Int?, val fuel: String?, val engineVolL: Double?,
    val color: String? = null,
)

data class ForecastInfo(val createdAt: Long, val aiText: String, val urgency: String, val trendRaw: String?)

private fun JSONObject.str(k: String): String? = if (isNull(k)) null else getString(k)
private fun JSONObject.dbl(k: String): Double? = if (isNull(k)) null else get(k).toString().toDoubleOrNull()
private fun JSONObject.int(k: String): Int? = if (isNull(k)) null else optInt(k)

/** Розбирає ISO-час із сервера (з дробовими секундами або без, Z або +00:00). */
fun parseIso(s: String): Long {
    var x = s.replace("Z", "+00:00")
    val tzIdx = x.lastIndexOfAny(charArrayOf('+', '-'))
    val base: String
    val tz: String
    if (tzIdx > 10) {
        base = x.substring(0, tzIdx)
        tz = x.substring(tzIdx)
    } else {
        base = x
        tz = "+00:00"
    }
    x = base.substringBefore('.') + tz
    val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
    return fmt.parse(x)?.time ?: 0L
}

private fun parseUser(o: JSONObject) = UserInfo(
    id = o.getLong("id"),
    email = o.getString("email"),
    locale = o.optString("locale", "uk"),
    theme = o.optString("theme", "system"),
)

private fun parseVehicle(o: JSONObject) = VehicleInfo(
    id = o.getLong("id"),
    vin = o.str("vin"),
    make = o.getString("make"),
    model = o.getString("model"),
    year = o.getInt("year"),
    fuel = o.getString("fuel"),
    engineVolL = o.dbl("engine_vol_l"),
    odometerBaseKm = o.optInt("odometer_base_km", 0),
    odometerEstKm = o.optInt("odometer_est_km", 0),
    tripsCount = o.optInt("trips_count", 0),
    baselineTrips = o.optInt("baseline_trips", 0),
    lastTripAt = o.str("last_trip_at")?.let { parseIso(it) },
    lastHealth = o.int("last_health_index"),
    colorHex = o.str("color_hex"),
)

private fun parseTrip(o: JSONObject) = TripInfo(
    id = o.getLong("id"),
    vehicleId = o.getLong("vehicle_id"),
    startedAt = parseIso(o.getString("started_at")),
    endedAt = o.str("ended_at")?.let { parseIso(it) },
    exitReason = o.str("exit_reason"),
    distanceKm = o.dbl("distance_km") ?: 0.0,
    readingsCount = o.optInt("readings_count", 0),
    countsForBaseline = o.optBoolean("counts_for_baseline", false),
    mode = o.optString("mode", "REFERENCE"),
    healthIndex = o.int("health_index"),
)

object Api {
    var baseUrl: String = Prefs.DEFAULT_SERVER
    var token: String? = null

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun extractDetail(text: String, code: Int): String {
        try {
            val d = JSONObject(text).opt("detail")
            if (d is String) return d
            if (d is JSONArray && d.length() > 0) {
                val first = d.getJSONObject(0)
                return first.optString("msg", "HTTP $code")
            }
        } catch (_: Exception) {
        }
        return "HTTP $code"
    }

    private suspend fun request(
        method: String,
        path: String,
        body: String? = null,
        contentType: String = "application/json",
    ): String = withContext(Dispatchers.IO) {
        val conn = try {
            URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
        } catch (e: Exception) {
            throw NetworkException("Bad URL")
        }
        try {
            conn.requestMethod = method
            conn.connectTimeout = 20000
            conn.readTimeout = 75000   // безкоштовний хостинг прокидається до хвилини
            conn.setRequestProperty("Accept", "application/json")
            token?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", contentType)
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code !in 200..299) throw ApiException(code, extractDetail(text, code))
            text
        } catch (e: ApiException) {
            throw e
        } catch (e: IOException) {
            // Деякі версії Android кидають таке виключення на 401 без заголовка
            if (e.message?.contains("authentication challenge", ignoreCase = true) == true) {
                throw ApiException(401, "Unauthorized")
            }
            throw NetworkException(e.message ?: "network")
        } finally {
            conn.disconnect()
        }
    }

    suspend fun health(): Boolean {
        val o = JSONObject(request("GET", "/health"))
        return o.optString("status") == "ok"
    }

    suspend fun register(email: String, password: String) {
        val body = JSONObject().put("email", email).put("password", password).toString()
        request("POST", "/auth/register", body)
    }

    suspend fun login(email: String, password: String): String {
        val form = "username=${enc(email)}&password=${enc(password)}"
        val text = request("POST", "/auth/login", form, "application/x-www-form-urlencoded")
        return JSONObject(text).getString("access_token")
    }

    suspend fun me(): UserInfo = parseUser(JSONObject(request("GET", "/auth/me")))

    suspend fun updateSettings(locale: String?, theme: String?) {
        val o = JSONObject()
        if (locale != null) o.put("locale", locale)
        if (theme != null) o.put("theme", theme)
        request("PUT", "/auth/me/settings", o.toString())
    }

    suspend fun vehicles(): List<VehicleInfo> {
        val arr = JSONArray(request("GET", "/vehicles"))
        return (0 until arr.length()).map { parseVehicle(arr.getJSONObject(it)) }
    }

    suspend fun addVehicle(
        make: String,
        model: String,
        year: Int,
        fuel: String,
        engineVolL: Double?,
        vin: String?,
        odometerKm: Int,
        color: String? = null,
    ): VehicleInfo {
        val o = JSONObject()
            .put("make", make)
            .put("model", model)
            .put("year", year)
            .put("fuel", fuel)
            .put("odometer_km", odometerKm)
        if (engineVolL != null) o.put("engine_vol_l", engineVolL)
        if (!vin.isNullOrBlank()) o.put("vin", vin)
        if (!color.isNullOrBlank()) o.put("color", color)
        return parseVehicle(JSONObject(request("POST", "/vehicles", o.toString())))
    }

    suspend fun trips(vehicleId: Long): List<TripInfo> {
        val arr = JSONArray(request("GET", "/trips?vehicle_id=$vehicleId"))
        return (0 until arr.length()).map { parseTrip(arr.getJSONObject(it)) }
    }

    suspend fun uploadTrip(payload: JSONObject): TripInfo =
        parseTrip(JSONObject(request("POST", "/trips", payload.toString())))

    suspend fun tripDetail(id: Long): TripDetail {
        val o = JSONObject(request("GET", "/trips/$id/detail"))
        val evArr = o.getJSONArray("events")
        val events = (0 until evArr.length()).map {
            val e = evArr.getJSONObject(it)
            TripEventInfo(
                ts = parseIso(e.getString("ts")),
                type = e.getString("event_type"),
                severity = e.optString("severity", "INFO"),
                message = e.str("message"),
            )
        }
        val sArr = o.getJSONArray("samples")
        val samples = (0 until sArr.length()).map {
            val x = sArr.getJSONObject(it)
            SamplePoint(
                tSec = x.optDouble("t_s", 0.0),
                coolant = x.int("coolant"),
                speed = x.int("speed"),
                rpm = x.int("rpm"),
                load = x.dbl("load"),
            )
        }
        return TripDetail(parseTrip(o.getJSONObject("trip")), events, samples)
    }

    private fun parseForecast(o: JSONObject) = ForecastInfo(
        createdAt = parseIso(o.getString("created_at")),
        aiText = o.getString("ai_text"),
        urgency = o.optString("urgency", "LOW"),
        trendRaw = if (o.isNull("trend")) null else o.get("trend").toString(),
    )

    suspend fun forecast(vehicleId: Long): ForecastInfo? {
        val text = request("GET", "/vehicles/$vehicleId/forecast").trim()
        if (text == "null" || text.isEmpty()) return null
        return parseForecast(JSONObject(text))
    }

    suspend fun createForecast(vehicleId: Long, tripId: Long? = null): ForecastInfo {
        val q = if (tripId != null) "?trip_id=$tripId" else ""
        return parseForecast(JSONObject(request("POST", "/vehicles/$vehicleId/forecast$q", "{}")))
    }

    private fun stringList(text: String): List<String> {
        val arr = JSONArray(text)
        return (0 until arr.length()).map { arr.getString(it) }
    }

    suspend fun makes(): List<String> = stringList(request("GET", "/catalog/makes"))

    suspend fun models(make: String): List<String> =
        stringList(request("GET", "/catalog/models?make=${enc(make)}"))

    suspend fun decodeVin(vin: String): VinInfo {
        val o = JSONObject(request("GET", "/catalog/vin/${enc(vin)}"))
        return VinInfo(
            vin = o.getString("vin"),
            make = o.str("make"),
            model = o.str("model"),
            year = o.int("year"),
            fuel = o.str("fuel"),
            engineVolL = o.dbl("engine_vol_l"),
            color = o.str("color"),
        )
    }

    suspend fun lookupPlate(plate: String): VinInfo {
        val o = JSONObject(request("GET", "/catalog/plate/${enc(plate)}"))
        return VinInfo(
            vin = o.optString("vin", ""),
            make = o.str("make"),
            model = o.str("model"),
            year = o.int("year"),
            fuel = o.str("fuel"),
            engineVolL = o.dbl("engine_vol_l"),
            color = o.str("color"),
        )
    }

    suspend fun deleteVehicle(id: Long) {
        request("DELETE", "/vehicles/$id")
    }
}
