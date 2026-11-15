package app.side.navi

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NaviMode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.data.WaypointArrivedInfo
import ai.pleos.playground.navi.data.WaypointChangedInfo
import ai.pleos.playground.navi.data.WaypointInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import app.side.BuildConfig
import app.side.places.GeoPoint
import app.side.places.Recommender
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 내비 상태 요약(앱이 쓰는 것만). */
data class NaviState(
    val ready: Boolean = false,
    val status: NavigationStatus? = null,
    val location: GeoPoint? = null,
    val destination: String? = null,
    val destinationPoint: GeoPoint? = null,
    val waypoints: List<String> = emptyList(),
    val waypointPoints: List<GeoPoint> = emptyList(),
    /** RouteStateInfo.naviMode가 경로 안내 중(ROUTE_GUIDING)인지. */
    val routeGuiding: Boolean = false,
    val lastError: String? = null,
    /** 오류가 날 때마다 1씩 는다. 요청 뒤 새 오류가 났는지 비교하는 데 쓴다. */
    val errorCount: Int = 0,
    /** 마지막으로 내비 콜백을 받은 시각(ms). 0이면 아직 응답이 없다. SDK는 연결이 끊긴 것을 알려 주지 않으므로 응답 여부로 판단한다. */
    val updatedAt: Long = 0,
    /** 차량 위치를 받은 시각(ms). 오래된 차량 위치를 계속 기준으로 쓰지 않게 한다. */
    val locationAt: Long = 0,
    /** 목적지 정보·경로 상태를 마지막으로 받은 시각(ms). 둘 다 새로 받았을 때만 "경로 없음"을 믿는다. */
    val destinationAt: Long = 0,
    val routeStateAt: Long = 0,
) {
    /**
     * 안내 중인 경로가 있는지. 없으면 경유지를 넣을 수 없다(실측: CAUSE_SEARCH_ROUTE 오류).
     * 이름 없는 지도 핀 목적지도 있을 수 있어 목적지 이름·좌표, 안내 상태(푸시), 경로 상태(조회) 중 하나라도 있으면 경로가 있다고 본다.
     * 잘못 "없음"으로 보면 운전자가 정한 목적지를 덮어쓰게 되므로 "있음" 쪽으로 기운다.
     */
    val hasRoute: Boolean
        // status(onNavigationStatus)는 푸시로만 바뀌어 오래된 값이 남을 수 있어 쓰지 않는다. 아래 셋은 refresh 때마다 다시 읽는다.
        get() = !destination.isNullOrBlank() || destinationPoint != null || routeGuiding

    /** 경로(목적지·경유지)에 [point]와 200m 안의 곳이 있는지. 내비가 이름을 바꿔 돌려줘도 같은 곳으로 알아본다. */
    fun isOnRoute(point: GeoPoint): Boolean =
        (listOfNotNull(destinationPoint) + waypointPoints).any { Recommender.distanceKm(it, point) < 0.2 }
}

/** 좌표가 (0, 0)이면 "없음"으로 본다(목적지가 없을 때 SDK가 0을 준다). */
private fun pointOrNull(lat: Double, lon: Double): GeoPoint? = if (lat == 0.0 && lon == 0.0) null else GeoPoint(lat, lon)

/**
 * Pleos NaviHelper SDK 래퍼. 차량 내비게이션의 현재 위치·목적지·경유지를 읽고, 경로와 경유지를 요청한다.
 * SDK 콜백은 메인 스레드로 넘겨 [state]에 반영한다.
 */
class NaviSource(context: Context) {

    private val main = Handler(Looper.getMainLooper())
    private val navi = NaviHelper(context.applicationContext)
    private val _state = MutableStateFlow(NaviState())
    val state: StateFlow<NaviState> = _state.asStateFlow()
    private var started = false

    private fun update(block: (NaviState) -> NaviState) = main.post {
        _state.value = block(_state.value).copy(updatedAt = System.currentTimeMillis())
    }

    private val listener = object : NaviHelperEventListener {
        override fun onNavigationStatus(status: NavigationStatus) {
            Log.i(TAG, "onNavigationStatus: $status")
            update { it.copy(status = status) }
        }

        override fun onCurrentLocationInfo(info: CurrentLocationInfo) {
            // 위치·목적지는 개인정보라 디버그 빌드에서만 기록한다.
            if (BuildConfig.DEBUG) Log.i(TAG, "onCurrentLocationInfo: ${info.latitude}, ${info.longitude} (${info.address})")
            // 위치를 아직 못 잡으면 SDK가 (0, 0)을 줄 수 있어 거른다.
            val point = pointOrNull(info.latitude, info.longitude) ?: return
            update { it.copy(location = point, locationAt = System.currentTimeMillis()) }
        }

        override fun onDestinationInfo(info: DestinationInfo) {
            if (BuildConfig.DEBUG) Log.i(TAG, "onDestinationInfo: ${info.name} ${info.latitude}, ${info.longitude} remain=${info.remainDistance}m")
            update {
                it.copy(
                    destination = info.name.ifBlank { null },
                    destinationPoint = pointOrNull(info.latitude, info.longitude),
                    destinationAt = System.currentTimeMillis(),
                )
            }
        }

        override fun onWayPointInfo(list: List<WaypointInfo>) {
            if (BuildConfig.DEBUG) Log.i(TAG, "onWayPointInfo: ${list.map { it.name }}")
            update { it.copy(waypoints = list.map { w -> w.name }, waypointPoints = list.mapNotNull { w -> pointOrNull(w.latitude, w.longitude) }) }
        }

        override fun onRouteStateInfo(info: RouteStateInfo) {
            Log.i(TAG, "onRouteStateInfo: mode=${info.naviMode} routing=${info.isRouting}")
            update { it.copy(routeGuiding = info.naviMode == NaviMode.ROUTE_GUIDING, routeStateAt = System.currentTimeMillis()) }
        }

        override fun onDestinationArrived(info: DestinationArrivedInfo) {
            Log.i(TAG, "onDestinationArrived")
            refresh()
        }

        override fun onWayPointArrived(info: WaypointArrivedInfo) {
            Log.i(TAG, "onWayPointArrived")
            refresh()
        }

        override fun onRouteStarted(info: ai.pleos.playground.navi.data.RouteStartInfo) {
            if (BuildConfig.DEBUG) Log.i(TAG, "onRouteStarted: $info")
            refresh()
        }

        override fun onRoutingCompleted() {
            Log.i(TAG, "onRoutingCompleted")
            refresh()
        }

        override fun onRouteCancelled() {
            Log.i(TAG, "onRouteCancelled")
            refresh() // 지우지 않고 다시 읽는다(콜백 순서가 뒤섞여 새 경로를 지우는 일을 막기 위해)
        }

        override fun onRouteEnded() {
            Log.i(TAG, "onRouteEnded")
            refresh()
        }

        override fun onWaypointChanged(info: WaypointChangedInfo) {
            if (BuildConfig.DEBUG) Log.i(TAG, "onWaypointChanged: $info")
            refresh()
        }

        override fun onDestinationChanged() {
            Log.i(TAG, "onDestinationChanged")
            refresh()
        }

        override fun onError(code: NaviErrorCode) {
            Log.w(TAG, "onError: $code")
            update { it.copy(lastError = code.toString(), errorCount = it.errorCount + 1) }
        }
    }

    fun start() {
        if (started) return
        started = true
        try {
            navi.initialize(
                {
                    Log.i(TAG, "initialized")
                    main.post {
                        if (!started) return@post // 초기화 중에 stop()됨
                        // TTS SDK처럼 초기화 전 리스너 등록이 문제될 수 있어 초기화 성공 뒤에 등록한다.
                        runCatching { navi.addListener(listener) }.onFailure { Log.e(TAG, "addListener failed", it) }
                        _state.value = _state.value.copy(ready = true)
                        refresh()
                    }
                },
                { e ->
                    Log.e(TAG, "initialize failed", e)
                    update { it.copy(lastError = "init: ${e.message}") }
                },
            )
        } catch (e: Exception) {
            Log.e(TAG, "initialize threw", e)
            update { it.copy(lastError = "init: ${e.message}") }
        }
    }

    /** 현재 위치·목적지·경유지를 다시 읽는다(결과는 리스너로 온다). */
    fun refresh() {
        // 하나가 실패해도(권한 예외 등) 나머지는 읽도록 따로 부른다.
        safe("getCurrentLocationInfo") { navi.getCurrentLocationInfo() }
        safe("getDestinationInfo") { navi.getDestinationInfo() }
        safe("getWaypointInfo") { navi.getWaypointInfo() }
        safe("getRouteStateInfo") { navi.getRouteStateInfo() }
    }

    /** [place]를 목적지로 경로를 요청한다. */
    /** 요청을 보냈으면 true(내비가 받아들였는지는 아니다), 예외로 보내지 못했으면 false. */
    fun requestRoute(name: String, point: GeoPoint, address: String = ""): Boolean = safe("requestRoute") {
        navi.requestRoute(RouteInfo(latitude = point.lat, longitude = point.lng, poiName = name, poiId = "", poiSubId = "", address = address))
    }

    /** [place]를 경유지로 추가한다. */
    fun addWaypoint(name: String, point: GeoPoint, index: WaypointIndex = WaypointIndex.FIRST, address: String = ""): Boolean = safe("addWaypoint") {
        navi.addWaypoint(RequestWaypointInfo(latitude = point.lat, longitude = point.lng, poiName = name, waypointIndex = index, poiId = "", poiSubId = "", address = address))
    }

    fun cancelRoute() = safe("cancelRoute") { navi.cancelRoute() }

    private fun safe(what: String, block: () -> Unit): Boolean {
        if (!_state.value.ready) {
            Log.w(TAG, "$what ignored: not ready")
            return false
        }
        return try {
            block()
            true
        } catch (e: Exception) {
            Log.e(TAG, "$what failed", e)
            update { it.copy(lastError = "$what: ${e.message}") }
            false
        }
    }

    fun stop() {
        if (!started) return
        started = false
        _state.value = _state.value.copy(ready = false)
        runCatching { navi.removeListener(listener) }
        runCatching { navi.release() }
    }

    private companion object {
        const val TAG = "SIDE/Navi"
    }
}
