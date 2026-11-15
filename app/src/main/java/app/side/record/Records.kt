package app.side.record

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** 기록 종류. */
enum class RecordKind {
    /** 이야기 카드 하나를 마침(질문과 알아들은 대답). */
    TALK,

    /** 진로 검사를 마침(결과 유형). */
    ASSESSMENT,

    /** 가 볼 만한 곳을 내비에 넣음. */
    PLACE,
}

/**
 * 성장 기록 한 줄.
 * @property at 기록한 시각(기기 시간대 기준)
 * @property title 화면에 보일 제목(질문, 검사 결과, 장소 이름)
 * @property detail 덧붙일 내용(아이의 대답, 유형 설명 등). 없으면 빈 문자열
 * @property tag 묶음 이름(이야기 카드, 밸런스 게임 등)이나 분류
 */
data class RecordEntry(
    val at: LocalDateTime,
    val kind: RecordKind,
    val title: String,
    val detail: String = "",
    val tag: String = "",
)

/** 하루치 기록. [entries]는 시간 순서. */
data class DayRecord(val date: LocalDate, val entries: List<RecordEntry>) {
    val talks: List<RecordEntry> get() = entries.filter { it.kind == RecordKind.TALK }
    val assessments: List<RecordEntry> get() = entries.filter { it.kind == RecordKind.ASSESSMENT }
    val places: List<RecordEntry> get() = entries.filter { it.kind == RecordKind.PLACE }
}

/** 기록을 날짜별로 묶는다. 최근 날짜가 먼저, 하루 안에서는 시간 순서. */
fun groupByDate(entries: List<RecordEntry>): List<DayRecord> =
    entries.groupBy { it.at.toLocalDate() }
        .map { (date, list) -> DayRecord(date, list.sortedBy { it.at }) }
        .sortedByDescending { it.date }

/**
 * 성장 기록 저장소. 앱 내부 저장소(filesDir/records.json)에 쌓고, 바뀔 때마다 [entries]로 알린다.
 * 아이의 대답이 들어 있으므로 기기 밖으로 보내거나 로그에 남기지 않는다.
 */
class RecordStore private constructor(private val file: File) {

    private val _entries = MutableStateFlow(load())
    val entries: StateFlow<List<RecordEntry>> = _entries.asStateFlow()

    /** 파일 쓰기는 한 줄로 세운 백그라운드 스레드에서 한다(메인 스레드를 막지 않고, 쓰는 순서를 지킨다). */
    private val writer = Executors.newSingleThreadExecutor { r -> Thread(r, "side-records").apply { isDaemon = true } }

    @Synchronized
    fun add(entry: RecordEntry) {
        // 너무 많이 쌓이면 오래된 것부터 버린다.
        _entries.value = (_entries.value + entry).takeLast(MAX_ENTRIES)
        save()
    }

    @Synchronized
    fun clear() {
        _entries.value = emptyList()
        save()
    }

    private fun load(): List<RecordEntry> = runCatching {
        if (!file.exists()) return emptyList()
        val arr = runCatching { JSONArray(file.readText()) }.getOrElse {
            // 읽지 못하는 파일은 덮어쓰기 전에 따로 남겨 둔다(기록을 영영 잃지 않게).
            file.copyTo(File(file.parentFile, file.name + ".broken"), overwrite = true)
            Log.w(TAG, "기록 파일을 읽지 못해 백업해 둠")
            return emptyList()
        }
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            runCatching {
                RecordEntry(
                    at = LocalDateTime.parse(o.getString("at"), FORMAT),
                    kind = RecordKind.valueOf(o.getString("kind")),
                    title = o.getString("title"),
                    detail = o.optString("detail"),
                    tag = o.optString("tag"),
                )
            }.getOrNull()
        }
    }.onFailure { Log.w(TAG, "기록을 읽지 못함") }.getOrDefault(emptyList())

    private fun save() {
        val snapshot = _entries.value
        writer.execute { write(snapshot) }
    }

    private fun write(entries: List<RecordEntry>) {
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(
                JSONObject()
                    .put("at", e.at.format(FORMAT))
                    .put("kind", e.kind.name)
                    .put("title", e.title)
                    .put("detail", e.detail)
                    .put("tag", e.tag),
            )
        }
        runCatching {
            // 임시 파일에 다 쓴 뒤 바꿔 끼워, 쓰는 도중 꺼져도 이전 기록이 남게 한다.
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(arr.toString())
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
        }.onFailure { Log.w(TAG, "기록을 저장하지 못함") }
    }

    companion object {
        private const val TAG = "SIDE/Records"
        private const val MAX_ENTRIES = 2000
        private val FORMAT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

        @Volatile
        private var instance: RecordStore? = null

        /** 앱 전체에서 하나만 쓴다(이야기·검사·장소 화면이 같이 쓴다). */
        fun get(context: Context): RecordStore =
            instance ?: synchronized(this) {
                instance ?: RecordStore(File(context.applicationContext.filesDir, "records.json")).also { instance = it }
            }
    }
}
