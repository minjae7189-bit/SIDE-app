package app.side.navi

import ai.pleos.playground.navi.constants.WaypointIndex
import app.side.places.GeoPoint
import kotlinx.coroutines.delay

/** 장소를 내비에 넣을 때 할 일. [decide]가 정하고 [NaviRouter]가 실행한다. */
sealed interface NaviPlan {
    /** 안내 중인 경로가 없어 목적지로 안내를 시작한다. */
    data object Destination : NaviPlan

    /** 경로가 있어 경유지로 넣는다. */
    data class Waypoint(val index: WaypointIndex) : NaviPlan

    /** 넣지 않는다. [reason]을 안내한다. */
    data class Refuse(val reason: String) : NaviPlan
}

/**
 * 내비에 넣는 규칙(에뮬레이터 실측 근거, 개발 노트 8절).
 * - 주행 중에는 넣지 않는다(운전 중 경로 변경·내비 화면 전환 방지).
 * - 이미 목적지·경유지에 있는 곳(이름 또는 200m 안)은 넣지 않는다.
 * - 경로가 없으면 경유지를 넣을 수 없으므로(CAUSE_SEARCH_ROUTE) 목적지로 안내를 시작한다.
 * - 경로가 있으면 경유지로 넣는다. 두 곳까지다. 경로 유무는 "있음" 쪽으로 기울여 운전자의 목적지를 덮어쓰지 않는다.
 */
fun decide(state: NaviState, name: String, point: GeoPoint, driving: Boolean): NaviPlan = when {
    driving -> NaviPlan.Refuse("차가 섰을 때 내비에 넣을 수 있어요.")
    !state.ready -> NaviPlan.Refuse("지금은 내비게이션에 연결할 수 없어요.")
    name == state.destination || name in state.waypoints || state.isOnRoute(point) -> NaviPlan.Refuse("$name, 이미 경로에 있어요.")
    !state.hasRoute -> NaviPlan.Destination
    state.waypoints.size >= 2 -> NaviPlan.Refuse("경유지는 두 곳까지라 더 넣지 못했어요.")
    state.waypoints.isEmpty() -> NaviPlan.Waypoint(WaypointIndex.FIRST)
    else -> NaviPlan.Waypoint(WaypointIndex.SECOND)
}

/**
 * [decide] 규칙대로 내비에 넣고 안내 문구를 돌려준다.
 * - 한 번에 하나만 처리한다(처리 중 다른 요청은 거절). 같은 스냅샷으로 두 곳을 모두 목적지로 넣어 덮어쓰는 일을 막는다.
 * - 같은 곳을 짧은 시간에 다시 요청하면 무시한다(연타 방지). 실패하면 [forgetLast]로 기록을 지운다.
 * - 넣기 직전에 상태를 새로 읽고, 내비가 응답하지 않으면 오래된 상태로 판단하지 않고 거절한다.
 */
class NaviRouter(private val navi: NaviSource, private val now: () -> Long = System::currentTimeMillis) {
    private var lastName: String? = null
    private var lastAt = 0L
    private var busy = false

    /** 마지막 요청 시각. 요청 직후 온 내비 오류를 이 요청 탓으로 볼지 정하는 데 쓴다. */
    val lastRequestAt: Long get() = lastAt

    /** 마지막 요청이 실패했을 때 중복 방지 기록을 지워 다시 시도할 수 있게 한다. */
    fun forgetLast() {
        lastName = null
    }

    /** [driving]은 결정 직전에 다시 확인한다(누른 뒤 출발했을 수 있다). */
    suspend fun add(name: String, point: GeoPoint, address: String, driving: () -> Boolean): String {
        if (busy) return "내비에 넣는 중이에요. 잠시만 기다려 주세요."
        if (lastName == name && now() - lastAt < DUPLICATE_WINDOW_MS) return "방금 내비에 넣었어요. 내비게이션 화면을 확인해 주세요."
        busy = true
        try {
            return addInternal(name, point, address, driving)
        } finally {
            busy = false
        }
    }

    private suspend fun addInternal(name: String, point: GeoPoint, address: String, driving: () -> Boolean): String {
        if (driving()) return (decide(navi.state.value, name, point, driving = true) as NaviPlan.Refuse).reason
        // 콜백 순서가 뒤섞여 오래된 상태로 판단하지 않도록 넣기 직전에 새로 읽고, 새 응답이 올 때까지 기다린다.
        val asked = now()
        navi.refresh()
        var waited = 0L
        while (navi.state.value.updatedAt < asked && waited < RESPONSE_TIMEOUT_MS) {
            delay(POLL_MS)
            waited += POLL_MS
        }
        if (navi.state.value.updatedAt < asked) return "내비게이션이 응답하지 않아요. 잠시 뒤 다시 시도해 주세요."
        delay(SETTLE_MS) // 목적지·경유지·경로 상태 응답이 모두 도착할 틈
        val st = navi.state.value
        val plan = decide(st, name, point, driving())
        // 목적지 정보와 경로 상태를 이번에 새로 받지 못했으면 "경로 없음"을 믿지 않는다(운전자의 목적지를 덮어쓰지 않게).
        if (plan == NaviPlan.Destination && (st.destinationAt < asked || st.routeStateAt < asked)) {
            return "내비게이션 상태를 확인하지 못했어요. 잠시 뒤 다시 시도해 주세요."
        }
        return when (plan) {
            is NaviPlan.Refuse -> plan.reason
            NaviPlan.Destination -> {
                if (!navi.requestRoute(name, point, address)) return "내비게이션에 넣지 못했어요."
                mark(name)
                "$name, 안내를 시작할게요."
            }
            is NaviPlan.Waypoint -> {
                if (!navi.addWaypoint(name, point, plan.index, address)) return "내비게이션에 넣지 못했어요."
                mark(name)
                // SDK는 요청별 성공을 알려 주지 않고, 연결이 끊긴 동안의 요청은 나중에 보내므로 결과를 단정하지 않는다.
                "$name, 경유지로 넣을게요."
            }
        }
    }

    private fun mark(name: String) {
        lastName = name
        lastAt = now()
    }

    private companion object {
        const val RESPONSE_TIMEOUT_MS = 3_000L
        const val POLL_MS = 100L
        const val SETTLE_MS = 500L
        const val DUPLICATE_WINDOW_MS = 15_000L
    }
}
