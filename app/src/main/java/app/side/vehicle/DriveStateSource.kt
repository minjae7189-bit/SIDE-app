package app.side.vehicle

import kotlinx.coroutines.flow.StateFlow

/**
 * 화면 정책을 결정하는 차량 상태.
 * - [PARKED]: P단. 카드 화면을 보여 준다.
 * - [DRIVING]: P 이외 기어(D/R/N/수동 단수). 음성 위주 최소 화면.
 * - [UNKNOWN]: 아직 상태를 모름. 안전을 위해 [DRIVING]과 같은 화면 정책을 쓴다.
 */
enum class DriveState { UNKNOWN, PARKED, DRIVING;

    /** 화면 카드를 띄워도 되는 상태인지. */
    val allowsCardUi: Boolean get() = this == PARKED
}

/** 기어 상태 공급원. Pleos Vehicle SDK 구현을 다른 방식(CarPropertyManager 등)으로 교체할 수 있게 분리한다. */
interface DriveStateSource {
    val state: StateFlow<DriveState>

    /** 연결에 실패했는지(에뮬레이터가 아니거나 서비스가 없는 경우). */
    val unavailable: StateFlow<Boolean>

    fun start()
    fun stop()
}
