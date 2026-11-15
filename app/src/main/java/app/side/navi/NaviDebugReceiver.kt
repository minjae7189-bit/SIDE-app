package app.side.navi

import ai.pleos.playground.navi.constants.WaypointIndex
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.side.places.GeoPoint

/**
 * 디버그 빌드에서 adb로 내비 동작을 시험하는 수신기. 릴리스 빌드에서는 등록하지 않는다.
 *
 * ```
 * adb shell am broadcast -a app.side.DEBUG_NAVI --es cmd refresh
 * adb shell am broadcast -a app.side.DEBUG_NAVI --es cmd route --es name 국립과천과학관 --ef lat 37.4385264 --ef lon 127.0062002
 * adb shell am broadcast -a app.side.DEBUG_NAVI --es cmd waypoint --es name ... --ef lat ... --ef lon ... --es index FIRST
 * adb shell am broadcast -a app.side.DEBUG_NAVI --es cmd cancel
 * ```
 */
class NaviDebugReceiver(private val navi: NaviSource) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val cmd = intent.getStringExtra("cmd")
        val name = intent.getStringExtra("name").orEmpty()
        val point = GeoPoint(intent.getFloatExtra("lat", 0f).toDouble(), intent.getFloatExtra("lon", 0f).toDouble())
        Log.i("SIDE/NaviDebug", "cmd=$cmd name=$name point=$point")
        when (cmd) {
            "refresh" -> navi.refresh()
            "route" -> navi.requestRoute(name, point)
            "waypoint" -> {
                val index = runCatching { WaypointIndex.valueOf(intent.getStringExtra("index") ?: "FIRST") }.getOrDefault(WaypointIndex.FIRST)
                navi.addWaypoint(name, point, index)
            }
            "cancel" -> navi.cancelRoute()
        }
    }

    companion object {
        const val ACTION = "app.side.DEBUG_NAVI"

        /** adb shell만 가진 권한. 다른 앱이 이 수신기로 이 앱의 내비 권한을 빌려 쓰지 못하게 한다. */
        const val SENDER_PERMISSION = "android.permission.DUMP"
    }
}
