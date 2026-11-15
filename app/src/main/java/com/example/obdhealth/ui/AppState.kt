package com.example.obdhealth.ui

import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.obdhealth.data.Api
import com.example.obdhealth.data.ApiException
import com.example.obdhealth.data.ForecastInfo
import com.example.obdhealth.data.NetworkException
import com.example.obdhealth.data.Prefs
import com.example.obdhealth.data.TripDetail
import com.example.obdhealth.data.TripInfo
import com.example.obdhealth.data.TripSim
import com.example.obdhealth.data.UserInfo
import com.example.obdhealth.data.VinInfo
import com.example.obdhealth.data.VehicleInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import kotlin.random.Random

enum class Screen { Garage, Trips, Forecast, Settings, AddCar, Live, TripDetail }

/** Поточна (ще не збережена) симульована поїздка. */
class SimSession {
    val samples = ArrayList<TripSim.Sample>()
    var tick by mutableIntStateOf(0)
    var last by mutableStateOf<TripSim.Sample?>(null)
    var finished by mutableStateOf(false)
    var fault = 0

    /** Готовий JSON для відправки. Створюється один раз, щоб повторна відправка не дублювала поїздку. */
    var pending: JSONObject? = null
}

/** Увесь стан застосунку в одному місці. */
class AppState private constructor(ctx: Context) {
    companion object {
        @Volatile
        private var inst: AppState? = null

        fun get(ctx: Context): AppState =
            inst ?: synchronized(this) {
                inst ?: AppState(ctx.applicationContext).also { inst = it }
            }
    }

    private val prefs = Prefs(ctx)
    private val notifier = TripNotifier(ctx)

    /** Задає MainActivity: просить дозвіл на сповіщення (Android 13+). */
    var requestNotificationPermission: (() -> Unit)? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var started = false

    // ---- налаштування ----
    var serverUrl by mutableStateOf(prefs.serverUrl)
        private set
    var theme by mutableStateOf(prefs.theme)
        private set
    var lang by mutableStateOf(prefs.lang)
        private set

    // ---- сесія ----
    var user by mutableStateOf<UserInfo?>(null)
        private set
    var splashDone by mutableStateOf(false)
        private set

    /** Застосунок готовий (сервер відповів): на заставці вмикаються фари. */
    var bootDone by mutableStateOf(false)
        private set
    var authBusy by mutableStateOf(false)
        private set
    var authError by mutableStateOf<String?>(null)

    // ---- навігація ----
    var screen by mutableStateOf(Screen.Garage)

    // ---- дані ----
    var vehicles by mutableStateOf<List<VehicleInfo>>(emptyList())
        private set
    var vehiclesLoading by mutableStateOf(false)
        private set
    var vehiclesError by mutableStateOf<String?>(null)
        private set
    var selectedVehicleId by mutableStateOf<Long?>(null)
        private set

    var trips by mutableStateOf<List<TripInfo>>(emptyList())
        private set
    var tripsLoading by mutableStateOf(false)
        private set
    var tripsError by mutableStateOf<String?>(null)
        private set

    var tripDetail by mutableStateOf<TripDetail?>(null)
        private set
    var tripDetailError by mutableStateOf<String?>(null)
        private set

    var forecast by mutableStateOf<ForecastInfo?>(null)
        private set
    var forecastLoading by mutableStateOf(false)
        private set
    var forecastError by mutableStateOf<String?>(null)
        private set

    var addBusy by mutableStateOf(false)
        private set
    var addError by mutableStateOf<String?>(null)

    var connectionStatus by mutableStateOf<String?>(null)
        private set

    // ---- симуляція поїздки ----
    var sim by mutableStateOf<SimSession?>(null)
        private set
    var uploadBusy by mutableStateOf(false)
        private set
    var uploadError by mutableStateOf<String?>(null)
        private set
    private var simJob: Job? = null

    val selectedVehicle: VehicleInfo?
        get() = vehicles.firstOrNull { it.id == selectedVehicleId }

    fun t(uk: String, en: String): String = if (lang == "en") en else uk

    // =====================================================
    // Запуск
    // =====================================================
    fun start() {
        if (started) return
        started = true
        Api.baseUrl = serverUrl
        Api.token = prefs.token
        scope.launch {
            val t0 = SystemClock.elapsedRealtime()
            if (prefs.token != null) {
                try {
                    val u = Api.me()
                    user = u
                    loadVehicles()
                } catch (e: ApiException) {
                    if (e.code == 401) clearSession()
                } catch (e: NetworkException) {
                    authError = errText(e)
                } catch (e: Exception) {
                    authError = e.message
                }
            }
            if (prefs.token == null) {
                // без входу просто перевіряємо, чи відповідає сервер
                try {
                    withTimeoutOrNull(2500) { Api.health() }
                } catch (_: Exception) {
                }
            }
            val left = 2200 - (SystemClock.elapsedRealtime() - t0)
            if (left > 0) delay(left)
            bootDone = true
            delay(1100)
            splashDone = true
        }
    }

    // =====================================================
    // Тема, мова, адреса сервера
    // =====================================================
    fun updateTheme(value: String) {
        theme = value
        prefs.theme = value
        if (user != null) pushSettings(null, value)
    }

    fun updateLang(value: String) {
        lang = value
        prefs.lang = value
        if (user != null) pushSettings(value, null)
    }

    private fun pushSettings(locale: String?, themeValue: String?) {
        scope.launch {
            try {
                Api.updateSettings(locale, themeValue)
            } catch (_: Exception) {
                // не критично: налаштування збережені локально
            }
        }
    }

    fun updateServerUrl(raw: String) {
        var u = raw.trim()
        if (u.isEmpty()) u = Prefs.DEFAULT_SERVER
        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "http://$u"
        u = u.trimEnd('/')
        serverUrl = u
        prefs.serverUrl = u
        Api.baseUrl = u
        connectionStatus = null
    }

    fun checkConnection() {
        connectionStatus = t("Перевіряю…", "Checking…")
        scope.launch {
            connectionStatus = try {
                if (Api.health()) t("Сервер працює, база даних підключена", "Server is up, database connected")
                else t("Сервер відповів, але стан незрозумілий", "Server answered, but the state is unclear")
            } catch (e: Exception) {
                errText(e)
            }
        }
    }

    // =====================================================
    // Вхід / реєстрація
    // =====================================================
    fun login(email: String, password: String) {
        scope.launch {
            authBusy = true
            authError = null
            try {
                doLogin(email.trim(), password)
            } catch (e: Exception) {
                authError = errText(e)
            } finally {
                authBusy = false
            }
        }
    }

    fun register(email: String, password: String) {
        scope.launch {
            authBusy = true
            authError = null
            try {
                Api.register(email.trim(), password)
                doLogin(email.trim(), password)
                // нова обліковка отримує вибрані на телефоні мову й тему
                pushSettings(lang, theme)
                // новому користувачу одразу пропонуємо додати перше авто
                openAddCar()
            } catch (e: Exception) {
                authError = errText(e)
            } finally {
                authBusy = false
            }
        }
    }

    private suspend fun doLogin(email: String, password: String) {
        val token = Api.login(email, password)
        Api.token = token
        prefs.token = token
        val u = Api.me()
        user = u
        // для існуючого користувача застосовуємо його збережені налаштування
        if (u.locale == "uk" || u.locale == "en") {
            lang = u.locale
            prefs.lang = u.locale
        }
        if (u.theme == "light" || u.theme == "dark" || u.theme == "system") {
            theme = u.theme
            prefs.theme = u.theme
        }
        screen = Screen.Garage
        loadVehicles()
    }

    fun logout() {
        simJob?.cancel()
        notifier.cancel()
        sim = null
        clearSession()
    }

    private fun clearSession() {
        Api.token = null
        prefs.token = null
        user = null
        vehicles = emptyList()
        trips = emptyList()
        tripDetail = null
        forecast = null
        selectedVehicleId = null
        screen = Screen.Garage
    }

    // =====================================================
    // Авто
    // =====================================================
    fun loadVehicles() {
        scope.launch {
            vehiclesLoading = true
            vehiclesError = null
            try {
                val list = Api.vehicles()
                vehicles = list
                if (selectedVehicleId == null || list.none { it.id == selectedVehicleId }) {
                    selectedVehicleId = list.firstOrNull()?.id
                }
            } catch (e: Exception) {
                vehiclesError = onFail(e)
            } finally {
                vehiclesLoading = false
            }
        }
    }

    /** Відкриває порожню форму додавання авто. */
    fun openAddCar() {
        addError = null
        clearVin()
        screen = Screen.AddCar
    }

    fun deleteVehicle(id: Long) {
        scope.launch {
            try {
                Api.deleteVehicle(id)
                vehicles = vehicles.filter { it.id != id }
                if (selectedVehicleId == id) {
                    selectedVehicleId = vehicles.firstOrNull()?.id
                    trips = emptyList()
                    forecast = null
                }
                vehiclesError = null
            } catch (e: Exception) {
                vehiclesError = onFail(e)
            }
        }
    }

    fun selectVehicle(id: Long) {
        selectedVehicleId = id
        trips = emptyList()
        forecast = null
    }

    fun addVehicle(
        make: String,
        model: String,
        year: Int,
        fuel: String,
        engineVolL: Double?,
        vin: String?,
        odometerKm: Int,
        color: String? = null,
    ) {
        scope.launch {
            addBusy = true
            addError = null
            try {
                val v = Api.addVehicle(make, model, year, fuel, engineVolL, vin, odometerKm, color)
                vehicles = vehicles + v
                selectVehicle(v.id)
                clearVin()
                screen = Screen.Garage
            } catch (e: Exception) {
                addError = onFail(e)
            } finally {
                addBusy = false
            }
        }
    }

    // =====================================================
    // Довідник авто (марки, моделі, VIN)
    // =====================================================
    var makes by mutableStateOf<List<String>>(emptyList())
        private set
    var models by mutableStateOf<List<String>>(emptyList())
        private set
    private var modelsFor: String? = null
    var catalogError by mutableStateOf<String?>(null)
        private set
    var vinBusy by mutableStateOf(false)
        private set
    var vinResult by mutableStateOf<VinInfo?>(null)
        private set
    var vinError by mutableStateOf<String?>(null)
        private set

    fun loadMakes() {
        if (makes.isNotEmpty()) return
        scope.launch {
            try {
                makes = Api.makes()
                catalogError = null
            } catch (e: Exception) {
                catalogError = onFail(e)
            }
        }
    }

    /** Завантажує моделі для марки (якщо марка точно збігається з довідником). */
    fun loadModels(make: String) {
        val key = make.trim()
        if (key.isEmpty() || key.equals(modelsFor, ignoreCase = true)) return
        modelsFor = key
        models = emptyList()
        scope.launch {
            try {
                val list = Api.models(key)
                if (key.equals(modelsFor, ignoreCase = true)) models = list
            } catch (e: Exception) {
                catalogError = onFail(e)
            }
        }
    }

    fun decodeVin(vin: String) = lookup(false, vin)

    fun lookupPlate(plate: String) = lookup(true, plate)

    private fun lookup(byPlate: Boolean, query: String) {
        scope.launch {
            vinBusy = true
            vinError = null
            vinResult = null
            try {
                vinResult = if (byPlate) Api.lookupPlate(query.trim()) else Api.decodeVin(query.trim().uppercase())
            } catch (e: ApiException) {
                if (e.code == 401) {
                    onFail(e)
                } else if (e.code == 404) {
                    vinError = t(
                        "Нічого не знайдено. Перевір дані, спробуй інший спосіб або введи авто вручну.",
                        "Nothing found. Check the data, try another way or enter the car manually.",
                    )
                } else {
                    vinError = errText(e)
                }
            } catch (e: Exception) {
                vinError = errText(e)
            } finally {
                vinBusy = false
            }
        }
    }

    fun clearVin() {
        vinResult = null
        vinError = null
    }

    // =====================================================
    // Поїздки
    // =====================================================
    fun loadTrips() {
        val vid = selectedVehicleId ?: return
        scope.launch {
            tripsLoading = true
            tripsError = null
            try {
                val list = Api.trips(vid)
                if (vid == selectedVehicleId) trips = list
            } catch (e: Exception) {
                tripsError = onFail(e)
            } finally {
                tripsLoading = false
            }
        }
    }

    fun openTrip(id: Long) {
        tripDetail = null
        tripDetailError = null
        screen = Screen.TripDetail
        scope.launch {
            try {
                tripDetail = Api.tripDetail(id)
            } catch (e: Exception) {
                tripDetailError = onFail(e)
            }
        }
    }

    fun loadForecast() {
        val vid = selectedVehicleId ?: return
        scope.launch {
            forecastLoading = true
            forecastError = null
            try {
                val f = Api.forecast(vid)
                if (vid == selectedVehicleId) forecast = f
            } catch (e: Exception) {
                forecastError = onFail(e)
            } finally {
                forecastLoading = false
            }
        }
    }

    fun refreshForecast(tripId: Long? = null) {
        val vid = selectedVehicleId ?: return
        scope.launch {
            forecastLoading = true
            forecastError = null
            try {
                val f = Api.createForecast(vid, tripId)
                if (vid == selectedVehicleId) forecast = f
            } catch (e: Exception) {
                forecastError = onFail(e)
            } finally {
                forecastLoading = false
            }
        }
    }

    // =====================================================
    // Симуляція поїздки
    // =====================================================
    fun startSim(fault: Int = 0) {
        if (sim != null || selectedVehicleId == null) {
            screen = Screen.Live
            return
        }
        val s = SimSession()
        s.fault = fault
        sim = s
        requestNotificationPermission?.invoke()
        updateNotification(0, null)
        uploadError = null
        val rnd = Random(System.nanoTime())
        simJob = scope.launch {
            while (isActive) {
                delay(200)
                val sample = TripSim.sample(s.samples.size, rnd, s.fault)
                s.samples.add(sample)
                s.last = sample
                s.tick = s.samples.size
                if (s.tick % 5 == 0) updateNotification(s.tick, sample.speed)
            }
        }
        screen = Screen.Live
    }

    fun notificationsEnabled(): Boolean = notifier.enabled()
    fun openNotificationSettings() = notifier.openSettings()

    private fun updateNotification(elapsed: Int, speed: Int?) {
        val title = t("Поїздка триває (симуляція)", "Trip in progress (simulation)")
        val speedPart = if (speed != null) " · $speed " + t("км/год", "km/h") else ""
        val tail = if (elapsed < 300) {
            " · " + t("до зарахування ", "counts in ") + fmtMmSs((300 - elapsed).toLong())
        } else {
            " · " + t("поїздку буде зараховано", "this trip will count")
        }
        notifier.update(title, fmtMmSs(elapsed.toLong()) + speedPart + tail, minOf(elapsed, 300), 300)
    }

    fun finishSim() {
        val s = sim ?: return
        val vid = selectedVehicleId ?: return
        simJob?.cancel()
        notifier.cancel()
        if (s.samples.size < 2) {
            discardSim()
            return
        }
        s.finished = true
        if (s.pending == null) s.pending = TripSim.buildPayload(vid, s.samples)
        scope.launch {
            uploadBusy = true
            uploadError = null
            try {
                val saved = Api.uploadTrip(s.pending!!)
                sim = null
                loadVehicles()
                openTrip(saved.id)
                // сервер складає прогноз у фоні — підтягуємо свіжий через кілька секунд
                scope.launch {
                    delay(5000); loadForecast()
                    delay(10000); loadForecast()
                }
            } catch (e: Exception) {
                uploadError = onFail(e)
            } finally {
                uploadBusy = false
            }
        }
    }

    fun discardSim() {
        simJob?.cancel()
        notifier.cancel()
        sim = null
        uploadError = null
        screen = Screen.Trips
    }

    // =====================================================
    // Навігація «назад»
    // =====================================================
    fun back() {
        screen = when (screen) {
            Screen.AddCar -> Screen.Garage
            Screen.Live -> Screen.Trips
            Screen.TripDetail -> Screen.Trips
            else -> Screen.Garage
        }
    }

    // =====================================================
    // Помилки
    // =====================================================
    /** Для помилок даних: при 401 розлогінює і не показує текст. */
    private fun onFail(e: Exception): String? {
        if (e is ApiException && e.code == 401) {
            clearSession()
            return null
        }
        return errText(e)
    }

    private fun errText(e: Exception): String = when (e) {
        is NetworkException -> t(
            "Немає зв'язку з сервером. Перевір, що він запущений, і адресу сервера.",
            "Can't reach the server. Make sure it is running and check the server address.",
        )
        is ApiException -> when (e.code) {
            401 -> t("Невірний email або пароль", "Wrong email or password")
            409 -> if (e.message?.contains("VIN") == true)
                t("Авто з таким VIN уже є в гаражі", "A car with this VIN already exists")
            else t("Користувач з таким email уже існує", "This email is already registered")
            422 -> t("Перевір введені дані", "Please check the entered data")
            502 -> t("Довідник авто тимчасово недоступний", "The car database is temporarily unavailable")
            else -> e.message ?: "HTTP ${e.code}"
        }
        else -> e.message ?: e.toString()
    }
}
