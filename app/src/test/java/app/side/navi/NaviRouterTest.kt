package app.side.navi

import ai.pleos.playground.navi.constants.WaypointIndex
import app.side.places.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NaviRouterTest {

    private val jobWorld = GeoPoint(37.3785437, 127.1066161)
    private val science = GeoPoint(37.4385264, 127.0062002)
    private val ready = NaviState(ready = true)

    @Test
    fun `주행 중에는 넣지 않는다`() {
        assertTrue(decide(ready, "국립과천과학관", science, driving = true) is NaviPlan.Refuse)
    }

    @Test
    fun `연결 안 되면 넣지 않는다`() {
        assertTrue(decide(NaviState(), "국립과천과학관", science, driving = false) is NaviPlan.Refuse)
    }

    @Test
    fun `경로가 없으면 목적지로`() {
        assertEquals(NaviPlan.Destination, decide(ready, "국립과천과학관", science, driving = false))
    }

    @Test
    fun `경로가 있으면 경유지로, 두 번째는 SECOND`() {
        val withRoute = ready.copy(destination = "한국잡월드", destinationPoint = jobWorld)
        assertEquals(NaviPlan.Waypoint(WaypointIndex.FIRST), decide(withRoute, "국립과천과학관", science, false))
        val oneWaypoint = withRoute.copy(waypoints = listOf("국립중앙박물관"), waypointPoints = listOf(GeoPoint(37.523953, 126.9803196)))
        assertEquals(NaviPlan.Waypoint(WaypointIndex.SECOND), decide(oneWaypoint, "국립과천과학관", science, false))
    }

    @Test
    fun `목적지 이름이 비어 있어도 안내 중이면 목적지를 덮어쓰지 않는다`() {
        listOf(
            ready.copy(routeGuiding = true),
            ready.copy(destinationPoint = jobWorld),
        ).forEach { st -> assertEquals(NaviPlan.Waypoint(WaypointIndex.FIRST), decide(st, "국립과천과학관", science, false)) }
    }

    @Test
    fun `경유지가 두 곳이면 더 넣지 않는다`() {
        val full = ready.copy(destination = "한국잡월드", waypoints = listOf("A", "B"))
        assertTrue(decide(full, "국립과천과학관", science, false) is NaviPlan.Refuse)
    }

    @Test
    fun `이미 경로에 있는 곳은 이름이 달라도 좌표로 알아본다`() {
        val st = ready.copy(destination = "잡월드 주차장", destinationPoint = GeoPoint(37.3790, 127.1068))
        assertTrue(decide(st, "한국잡월드", jobWorld, false) is NaviPlan.Refuse)
    }
}
