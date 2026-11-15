package app.side

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.side.assessment.AssessmentViewModel
import app.side.navi.NaviDebugReceiver
import app.side.places.Place
import app.side.places.PlacesActions
import app.side.places.PlacesViewModel
import app.side.places.Recommender
import app.side.talk.TalkViewModel
import app.side.ui.SideApp
import app.side.ui.SideTheme
import app.side.ui.TalkActions
import app.side.ui.buildProfile

class MainActivity : ComponentActivity() {

    private val viewModel: TalkViewModel by viewModels()
    private val assessmentViewModel: AssessmentViewModel by viewModels()
    private val placesViewModel: PlacesViewModel by viewModels()
    private var naviDebug: NaviDebugReceiver? = null

    override fun onDestroy() {
        naviDebug?.let { unregisterReceiver(it) }
        naviDebug = null
        super.onDestroy()
    }


    override fun onStop() {
        super.onStop()
        // 다른 앱이 이 화면을 가려 액티비티가 멈추면(onStop) 음성 토크를 멈춘다. 구성 변경으로 인한 재생성은 제외.
        // Pleos 멀티윈도우에서는 HOME/BACK 키만으로는 onStop이 오지 않아 계속 재생된다(에뮬레이터 실측).
        if (!isChangingConfigurations) {
            viewModel.pauseIfActive()
            assessmentViewModel.stopVoice()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.DEBUG) {
            // adb로 내비 동작을 시험하는 디버그 전용 수신기(NaviDebugReceiver 참고). adb shell만 보낼 수 있다.
            naviDebug = NaviDebugReceiver(placesViewModel.navi).also {
                ContextCompat.registerReceiver(
                    this, it, IntentFilter(NaviDebugReceiver.ACTION), NaviDebugReceiver.SENDER_PERMISSION, null, ContextCompat.RECEIVER_EXPORTED,
                )
            }
        }
        val actions = TalkActions(
            play = viewModel::play,
            pause = viewModel::pause,
            replay = viewModel::replay,
            next = viewModel::next,
            previous = viewModel::previous,
            simulateGear = viewModel::simulateGear,
            startDeck = viewModel::startDeck,
            setFamilyMbti = { p, c -> viewModel.setFamilyMbti(p, c) },
        )
        val placesActions = PlacesActions(
            toggleTheme = placesViewModel::toggleTheme,
            refreshLocation = placesViewModel::refreshLocation,
            onPermissionResult = placesViewModel::onPermissionResult,
            openMap = ::navigateTo,
            markExplored = placesViewModel::markExplored,
        )
        // 진로 검사도 이야기 화면의 음성 엔진을 같이 쓴다. 한쪽을 시작하면 다른 쪽은 멈춘다(마이크가 하나).
        assessmentViewModel.voice = viewModel.voice
        assessmentViewModel.isDriving = { !viewModel.driveState.value.allowsCardUi }
        viewModel.onTalkStart = { assessmentViewModel.stopVoice() }
        // 이야기를 다 마치면 가장 잘 맞는 가까운 장소 한 곳을 음성으로 권한다(주행 중에도 소리로만 안내).
        viewModel.closingSuggestion = {
            val places = placesViewModel.ui.value
            val profile = buildProfile(viewModel.ui.value, assessmentViewModel.ui.value, places.chosen, places.explored)
            if (places.places.isEmpty() || profile.isEmpty) {
                null
            } else {
                Recommender.recommend(places.places, places.here, profile, count = 1, today = java.time.LocalDate.now().dayOfWeek)
                    .firstOrNull()?.let { Recommender.voiceLine(it, knownLocation = places.locationSource != app.side.places.LocationSource.DEFAULT) }
            }
        }

        setContent {
            SideTheme {
                val ui by viewModel.ui.collectAsStateWithLifecycle()
                val driveState by viewModel.driveState.collectAsStateWithLifecycle()
                val gearUnavailable by viewModel.gearUnavailable.collectAsStateWithLifecycle()
                val simulatedGear by viewModel.simulatedGear.collectAsStateWithLifecycle()
                val assessment by assessmentViewModel.ui.collectAsStateWithLifecycle()
                val places by placesViewModel.ui.collectAsStateWithLifecycle()
                SideApp(
                    ui = ui,
                    driveState = driveState,
                    gearUnavailable = gearUnavailable,
                    simulatedGear = simulatedGear,
                    showDebugGear = BuildConfig.DEBUG,
                    actions = actions,
                    assessment = assessment,
                    assessmentActions = assessmentViewModel.actions,
                    places = places,
                    placesActions = placesActions,
                )
            }
        }
    }


    /**
     * 장소로 길 안내: 차량 내비(NaviHelper)에 연결돼 있으면 목적지·경유지로 넣고(주행 중이면 넣지 않음),
     * 연결이 안 되면 지도 앱을 연다. 정차 화면에서만 누를 수 있다.
     */
    private fun navigateTo(place: Place) {
        if (placesViewModel.ui.value.naviReady) {
            placesViewModel.addToNavi(place, driving = { !viewModel.driveState.value.allowsCardUi })
        } else {
            openMap(place)
        }
    }

    /** 지도 앱으로 장소를 연다(내비 연결이 안 될 때). */
    private fun openMap(place: Place) {
        val uri = Uri.parse("geo:${place.lat},${place.lng}?q=${place.lat},${place.lng}(${Uri.encode(place.name)})")
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: ActivityNotFoundException) {
            Log.w("SIDE/Places", "지도 앱이 없음", e)
        }
    }
}
