package app.side.assessment

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter

enum class Stage { INTRO, PROFILE, QUESTIONS, RESULT }

data class AssessmentUi(
    val content: CareerTestContent? = null,
    val loadError: String? = null,
    val stage: Stage = Stage.INTRO,
    /** 기본 정보 화면 쪽수: 0 학교급·학년·성별·지역, 1 잘하는 과목 */
    val profilePage: Int = 0,
    val profile: Profile = Profile(),
    val answers: Map<QuestionRef, Int> = emptyMap(),
    val index: Int = 0,
    val result: CareerTestResult? = null,
    val finishedAt: String? = null,
) {
    val total: Int get() = content?.questions?.size ?: 0
    val inProgress: Boolean get() = result == null && (answers.isNotEmpty() || profile.level != null)
}

class AssessmentActions(
    val start: () -> Unit,
    val restart: () -> Unit,
    val showResult: () -> Unit,
    val updateProfile: (Profile) -> Unit,
    val profileNext: () -> Unit,
    val profileBack: () -> Unit,
    val answer: (Int) -> Unit,
    val previous: () -> Unit,
    val exit: () -> Unit,
) {
    companion object {
        val None = AssessmentActions({}, {}, {}, {}, {}, {}, {}, {}, {})
    }
}

/**
 * 진로 검사 진행 상태. 응답은 기기에 저장해서, 기어를 바꾸거나 앱을 다시 켜도 이어서 할 수 있다.
 * 검사는 정차 중에만 화면에 보인다(주행 중에는 화면 전체가 음성 화면으로 바뀐다).
 */
class AssessmentViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("career_test", Context.MODE_PRIVATE)
    private val _ui = MutableStateFlow(AssessmentUi())
    val ui: StateFlow<AssessmentUi> = _ui

    val actions = AssessmentActions(
        start = ::start,
        restart = ::restart,
        showResult = { _ui.update { if (it.result != null) it.copy(stage = Stage.RESULT) else it } },
        updateProfile = { p -> update { it.copy(profile = p) } },
        profileNext = ::profileNext,
        profileBack = ::profileBack,
        answer = ::answer,
        previous = ::previous,
        exit = { _ui.update { it.copy(stage = Stage.INTRO) } },
    )

    init {
        viewModelScope.launch {
            val loaded = runCatching {
                withContext(Dispatchers.IO) {
                    getApplication<Application>().assets.open(ASSET).bufferedReader().use { CareerTestJson.parse(it.readText()) }
                }
            }
            loaded.onSuccess { content -> _ui.value = restore(content) }
                .onFailure { e ->
                    Log.e(TAG, "검사 자료를 읽지 못함", e)
                    _ui.update { it.copy(loadError = "검사 자료를 불러오지 못했어요") }
                }
        }
    }

    private fun start() = update {
        when {
            it.result != null -> it.copy(stage = Stage.RESULT)
            it.profile.complete -> it.copy(stage = Stage.QUESTIONS, index = firstUnanswered(it))
            else -> it.copy(stage = Stage.PROFILE, profilePage = 0)
        }
    }

    private fun restart() = update {
        AssessmentUi(content = it.content, stage = Stage.PROFILE, profile = Profile(level = it.profile.level))
    }

    private fun profileNext() = update {
        when {
            it.profilePage == 0 -> it.copy(profilePage = 1)
            it.profile.complete -> it.copy(stage = Stage.QUESTIONS, index = firstUnanswered(it))
            else -> it
        }
    }

    private fun profileBack() = update {
        if (it.profilePage == 1) it.copy(profilePage = 0) else it.copy(stage = Stage.INTRO)
    }

    private fun answer(value: Int) = update { s ->
        val content = s.content ?: return@update s
        val q = content.questions[s.index]
        val answers = s.answers + (q to value)
        if (answers.size == content.questions.size) {
            s.copy(
                answers = answers,
                result = CareerTestScoring.score(content, answers),
                finishedAt = LocalDate.now().format(DATE),
                stage = Stage.RESULT,
            )
        } else {
            // 이전 문항으로 돌아가 고친 경우에도 아직 답하지 않은 다음 문항으로 간다.
            val next = (s.index + 1 until content.questions.size).firstOrNull { content.questions[it] !in answers }
                ?: content.questions.indices.first { content.questions[it] !in answers }
            s.copy(answers = answers, index = next)
        }
    }

    private fun previous() = update {
        if (it.index > 0) it.copy(index = it.index - 1) else it.copy(stage = Stage.PROFILE, profilePage = 1)
    }

    private fun firstUnanswered(s: AssessmentUi): Int =
        s.content?.questions?.indexOfFirst { it !in s.answers }?.takeIf { it >= 0 } ?: 0

    /** 상태를 바꾸고 기기에 저장한다. */
    private fun update(change: (AssessmentUi) -> AssessmentUi) {
        _ui.update(change)
        save(_ui.value)
    }

    private fun save(s: AssessmentUi) {
        val content = s.content ?: return
        val p = s.profile
        prefs.edit()
            .putString(
                KEY_PROFILE,
                JSONObject()
                    .put("level", p.level?.name)
                    .put("grade", p.grade)
                    .put("gender", p.gender)
                    .put("region", p.region)
                    .put("subjects", JSONArray(p.subjects))
                    .toString(),
            )
            .putString(KEY_ANSWERS, content.questions.joinToString("") { (s.answers[it] ?: 0).toString() })
            .putString(KEY_FINISHED, s.finishedAt)
            .apply()
    }

    private fun restore(content: CareerTestContent): AssessmentUi {
        val profile = runCatching {
            prefs.getString(KEY_PROFILE, null)?.let { JSONObject(it) }?.let { o ->
                Profile(
                    level = o.optString("level").takeIf { it.isNotEmpty() && it != "null" }?.let { Level.valueOf(it) },
                    grade = o.optInt("grade", -1),
                    gender = o.optInt("gender", -1),
                    region = o.optInt("region", -1),
                    subjects = o.optJSONArray("subjects")?.let { a -> (0 until a.length()).map { a.getInt(it) } } ?: emptyList(),
                )
            }
        }.getOrNull() ?: Profile()
        val digits = prefs.getString(KEY_ANSWERS, "").orEmpty()
        val answers = if (digits.length == content.questions.size) {
            content.questions.withIndex()
                .mapNotNull { (i, q) -> digits[i].digitToIntOrNull()?.takeIf { it in 1..6 }?.let { q to it } }
                .toMap()
        } else {
            emptyMap() // 문항 수가 바뀐 경우 예전 응답은 버린다.
        }
        val done = answers.size == content.questions.size
        val base = AssessmentUi(content = content, profile = profile, answers = answers)
        return base.copy(
            index = firstUnanswered(base),
            result = if (done) CareerTestScoring.score(content, answers) else null,
            finishedAt = if (done) prefs.getString(KEY_FINISHED, null) else null,
        )
    }

    private companion object {
        const val TAG = "CareerTest"
        const val ASSET = "career_test_demo.json"
        const val KEY_PROFILE = "profile"
        const val KEY_ANSWERS = "answers"
        const val KEY_FINISHED = "finished_at"
        val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy. M. d.")
    }
}
