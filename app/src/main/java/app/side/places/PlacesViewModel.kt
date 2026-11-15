package app.side.places

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.side.navi.NaviRouter
import app.side.navi.NaviSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** 위치를 어디서 얻었는지. 화면에 "현재 위치 기준" / "기본 위치 기준"으로 보여 준다. VEHICLE은 차량 내비(NaviHelper)의 현재 위치. */
enum class LocationSource { VEHICLE, CURRENT, LAST_KNOWN, DEFAULT }

data class PlacesUi(
    val places: List<Place> = emptyList(),
    val loading: Boolean = true,
    val here: GeoPoint = DEFAULT_POINT,
    val locationSource: LocationSource = LocationSource.DEFAULT,
    val permissionGranted: Boolean = false,
    /** 정차 화면에서 고른 "오늘 나온 관심사" */
    val chosen: Set<Theme> = emptySet(),
    val message: String? = null,
    /** 내비에 넣은 결과 안내(장소 화면 위쪽에 보인다). */
    val naviMessage: String? = null,
    /** 차량 내비게이션(NaviHelper)에 연결됐는지. 아니면 "길 안내"는 지도 앱을 연다. */
    val naviReady: Boolean = false,
)

/** 위치를 받지 못할 때 쓰는 기본 위치(서울시청). 화면에 그 사실을 알린다. */
val DEFAULT_POINT = GeoPoint(37.5663, 126.9779)
const val DEFAULT_POINT_NAME = "서울시청"

class PlacesActions(
    val toggleTheme: (Theme) -> Unit,
    val refreshLocation: () -> Unit,
    val onPermissionResult: (Boolean) -> Unit,
    val openMap: (Place) -> Unit,
) {
    companion object {
        val None = PlacesActions({}, {}, {}, {})
    }
}

/**
 * 장소 자료(assets/places.json, scripts/build_places.py로 만든 문화기반시설·여가시설 약 9,700곳)와 현재 위치를 들고 있는다.
 * 추천 계산 자체는 [Recommender]가 한다.
 */
class PlacesViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("places", Context.MODE_PRIVATE)
    private val _ui = MutableStateFlow(
        PlacesUi(
            permissionGranted = hasPermission(),
            chosen = prefs.getStringSet(KEY_CHOSEN, emptySet()).orEmpty().mapNotNull { runCatching { Theme.valueOf(it) }.getOrNull() }.toSet(),
        ),
    )
    val ui: StateFlow<PlacesUi> = _ui

    /** 차량 내비게이션. 현재 위치(차량 위치)를 주고, 목적지·경유지를 넣는다. */
    val navi = NaviSource(app)
    private val router = NaviRouter(navi)

    init {
        navi.start()
        viewModelScope.launch {
            navi.state.collect { st ->
                // 새로 받은 차량 위치는 안드로이드 위치보다 우선한다.
                _ui.update { ui ->
                    val loc = st.location
                    if (loc != null && vehicleFresh(st.locationAt)) ui.copy(here = loc, locationSource = LocationSource.VEHICLE, naviReady = st.ready)
                    else ui.copy(naviReady = st.ready)
                }
            }
        }
        // 넣기 요청 직후 내비 오류가 오면 알린다(SDK는 요청별 결과를 주지 않는다).
        viewModelScope.launch {
            navi.state.map { it.errorCount }.distinctUntilChanged().drop(1).collect {
                if (System.currentTimeMillis() - router.lastRequestAt < NAVI_ERROR_WINDOW_MS) {
                    router.forgetLast() // 실패했으니 바로 다시 시도할 수 있게
                    showNaviMessage("내비게이션에 넣지 못했어요. " + naviErrorText(navi.state.value.lastError))
                }
            }
        }
        viewModelScope.launch {
            val places = runCatching {
                withContext(Dispatchers.IO) {
                    getApplication<Application>().assets.open(ASSET).bufferedReader().use { parse(it.readText()) }
                }
            }.onFailure { Log.e(TAG, "장소 자료를 읽지 못함", it) }.getOrDefault(emptyList())
            _ui.update { it.copy(places = places, loading = false, message = if (places.isEmpty()) "장소 자료를 불러오지 못했어요" else it.message) }
        }
        refreshLocation()
    }

    fun toggleTheme(theme: Theme) {
        _ui.update { s -> s.copy(chosen = if (theme in s.chosen) s.chosen - theme else s.chosen + theme) }
        prefs.edit().putStringSet(KEY_CHOSEN, _ui.value.chosen.map { it.name }.toSet()).apply()
    }

    fun onPermissionResult(granted: Boolean) {
        _ui.update { it.copy(permissionGranted = granted) }
        if (granted) refreshLocation()
    }

    /** 마지막으로 알려진 위치를 먼저 쓰고, 현재 위치를 한 번 더 받아 바꾼다. 둘 다 없으면 기본 위치. */
    @SuppressLint("MissingPermission")
    fun refreshLocation() {
        navi.refresh() // 차량 위치도 새로 읽는다
        if (!hasPermission()) {
            _ui.update { it.copy(permissionGranted = false) }
            return
        }
        val lm = getApplication<Application>().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = lm.getProviders(true)
        val last = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
        last?.let { set(it, LocationSource.LAST_KNOWN) }
        val provider = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).firstOrNull { it in providers } ?: return
        LocationManagerCompat.getCurrentLocation(
            lm,
            provider,
            null as android.os.CancellationSignal?, // 같은 이름의 옛 오버로드와 헷갈리지 않게 타입을 밝힌다
            ContextCompat.getMainExecutor(getApplication()),
        ) { loc -> if (loc != null) set(loc, LocationSource.CURRENT) }
    }

    private fun set(loc: Location, source: LocationSource) {
        _ui.update {
            // 새로 받은 차량 위치가 있으면 안드로이드 위치로 덮지 않는다(오래됐으면 안드로이드 위치를 쓴다).
            if (it.locationSource == LocationSource.VEHICLE && vehicleFresh(navi.state.value.locationAt)) it.copy(permissionGranted = true)
            else it.copy(here = GeoPoint(loc.latitude, loc.longitude), locationSource = source, permissionGranted = true)
        }
    }

    /**
     * [place]를 차량 내비에 넣는다(경로가 없으면 목적지, 있으면 경유지). 규칙은 [decide] 참고.
     * [driving]이면 넣지 않는다. 결과는 [PlacesUi.naviMessage]로 알린다.
     */
    fun addToNavi(place: Place, driving: () -> Boolean) {
        viewModelScope.launch {
            showNaviMessage(router.add(place.name, GeoPoint(place.lat, place.lng), address = "", driving = driving))
        }
    }

    private var messageJob: kotlinx.coroutines.Job? = null

    /** 안내 문구를 잠시 보여 주고 지운다. */
    private fun showNaviMessage(message: String) {
        _ui.update { it.copy(naviMessage = message) }
        messageJob?.cancel()
        messageJob = viewModelScope.launch {
            kotlinx.coroutines.delay(NAVI_MESSAGE_MS)
            _ui.update { it.copy(naviMessage = null) }
        }
    }

    /** 내비 오류 코드를 사용자 문구로. */
    private fun naviErrorText(code: String?): String = when (code) {
        "CAUSE_SEARCH_ROUTE" -> "경로를 찾지 못했어요."
        "CAUSE_SEARCH_ROUTE_OVER_DISTANCE" -> "너무 멀어서 경로를 만들 수 없어요."
        "CAUSE_SEARCH_ROUTE_TOO_SHORT_DISTANCE" -> "너무 가까워서 경로를 만들 수 없어요."
        "CAUSE_LOW_BATTERY" -> "배터리가 부족해요."
        else -> ""
    }

    private fun vehicleFresh(at: Long) = at > 0 && System.currentTimeMillis() - at < VEHICLE_LOCATION_FRESH_MS

    override fun onCleared() {
        navi.stop()
    }

    private fun hasPermission(): Boolean = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        .any { ContextCompat.checkSelfPermission(getApplication(), it) == PackageManager.PERMISSION_GRANTED }

    private companion object {
        const val TAG = "SIDE/Places"
        const val ASSET = "places.json"
        const val KEY_CHOSEN = "chosen_themes"
        const val NAVI_ERROR_WINDOW_MS = 5_000L
        const val NAVI_MESSAGE_MS = 10_000L
        /** 이보다 오래된 차량 위치는 기준으로 쓰지 않는다. */
        const val VEHICLE_LOCATION_FRESH_MS = 10 * 60_000L

        /** {"themes":[...], "places":[[이름, 종류, 위도, 경도, 대략(0/1), 주제 비트, 설명, 운영 시간, 휴관, 요금, 쉬는 요일 비트, 소개], ...]} */
        fun parse(text: String): List<Place> {
            val o = JSONObject(text)
            val names = o.getJSONArray("themes")
            val themes = (0 until names.length()).map { runCatching { Theme.valueOf(names.getString(it)) }.getOrNull() }
            val arr = o.getJSONArray("places")
            return (0 until arr.length()).map { i ->
                val p = arr.getJSONArray(i)
                val mask = p.getInt(5)
                Place(
                    name = p.getString(0),
                    kind = p.getString(1),
                    lat = p.getDouble(2),
                    lng = p.getDouble(3),
                    approx = p.getInt(4) == 1,
                    themes = themes.indices.filter { mask shr it and 1 == 1 }.mapNotNull { themes[it] }.toSet(),
                    detail = p.optString(6),
                    hours = p.optString(7),
                    closed = p.optString(8),
                    fee = p.optString(9),
                    closedDays = p.optInt(10),
                    intro = p.optString(11),
                )
            }
        }
    }
}
