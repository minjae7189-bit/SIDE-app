package app.side.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Pleos Vehicle SDK의 기어 선택 상태를 [DriveState]로 변환한다.
 *
 * 정책: P단만 [DriveState.PARKED]. N단은 신호 대기일 수도 있지만 차가 굴러갈 수 있으므로 [DriveState.DRIVING]으로 본다.
 */
@Suppress("DEPRECATION") // Gear API는 deprecated(AOSP GEAR_SELECTION 권장)지만 공모전 SDK 기준으로 사용한다.
class PleosGearSource(context: Context) : DriveStateSource {

    private val vehicle = Vehicle(context.applicationContext)
    private val _state = MutableStateFlow(DriveState.UNKNOWN)
    override val state: StateFlow<DriveState> = _state.asStateFlow()
    private val _unavailable = MutableStateFlow(false)
    override val unavailable: StateFlow<Boolean> = _unavailable.asStateFlow()
    private var started = false

    private val listener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) {
            Log.i(TAG, "gear changed: $gear")
            update(gear)
        }

        override fun onFailed(e: Exception) {
            Log.w(TAG, "gear listener failed", e)
            markUnavailable()
        }
    }

    override fun start() {
        if (started) return
        started = true
        try {
            vehicle.initialize()
            // registerGearSelection은 첫 등록 때 SDK가 현재 값을 읽어 리스너로 보내 준다.
            // 별도로 getCurrentGearSelectionState를 부르면 오래된 값이 덮어쓸 수 있는 경로가 하나 더 생기므로 부르지 않는다.
            // (SDK 내부의 초기값 읽기와 변경 이벤트 사이 경합은 남아 있다. 시작 직후 기어 변경이 겹칠 때만 해당.)
            vehicle.getGear().registerGearSelection(listener)
        } catch (e: Exception) {
            Log.e(TAG, "vehicle init failed", e)
            markUnavailable()
        }
    }

    /** 기어를 알 수 없게 되면 마지막 값(PARKED일 수 있음)을 유지하지 않고 안전한 UNKNOWN으로 되돌린다. */
    private fun markUnavailable() {
        _unavailable.value = true
        _state.value = DriveState.UNKNOWN
    }

    override fun stop() {
        if (!started) return
        started = false
        runCatching { vehicle.getGear().unregisterGearSelection(listener) }
            .onFailure { Log.w(TAG, "unregister failed", it) }
        runCatching { vehicle.release() }
            .onFailure { Log.w(TAG, "release failed", it) }
    }

    private fun update(gear: VehicleGear?) {
        _unavailable.value = false
        _state.value = gear?.toDriveState() ?: DriveState.UNKNOWN
    }

    companion object {
        private const val TAG = "SIDE/Gear"

        fun VehicleGear.toDriveState(): DriveState = when (this) {
            VehicleGear.GEAR_PARK -> DriveState.PARKED
            VehicleGear.GEAR_UNKNOWN -> DriveState.UNKNOWN
            else -> DriveState.DRIVING
        }
    }
}
