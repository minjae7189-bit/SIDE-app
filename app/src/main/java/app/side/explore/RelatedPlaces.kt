package app.side.explore

import app.side.places.GeoPoint
import app.side.places.Place
import app.side.places.Recommender
import app.side.places.Theme
import java.time.DayOfWeek

/** 직업과 관련된 장소 하나. [direct]가 true면 장소 이름·종류가 직업 낱말과 맞은 곳, false면 같은 주제로 채운 곳. */
data class RelatedPlace(val place: Place, val km: Double, val direct: Boolean)

/**
 * [job]과 관련된 장소를 [here]에서 가까운 순서로 [limit]곳 고른다.
 * - 먼저 장소 이름이나 종류에 직업 낱말([Job.keywords])이 들어간 곳을 가까운 순으로.
 * - 모자라면 직업 주제([Job.themes])가 같은 곳으로 채운다(역시 가까운 순).
 * - [maxKm]보다 먼 곳과, [today]에 매주 쉬는 곳은 뺀다.
 */
fun relatedPlaces(
    job: Job,
    places: List<Place>,
    here: GeoPoint,
    limit: Int = 5,
    maxKm: Double = 100.0,
    today: DayOfWeek? = null,
): List<RelatedPlace> {
    // 체육시설(○○과학관 농구장 등)은 이름에 낱말이 들어 있어도 운동 관련 직업이 아니면 뺀다.
    val sportsOk = Theme.SPORTS in job.themes
    val open = places.filter { (today == null || !it.closedOn(today)) && (sportsOk || it.kind !in SPORTS_KINDS) }
    fun near(list: List<Place>) = list
        .map { it to Recommender.distanceKm(here, GeoPoint(it.lat, it.lng)) }
        .filter { it.second <= maxKm }
        .sortedBy { it.second }
    // 이름으로 맞추는 것은 기관(박물관·과학관 등)만 한다. 체험 클래스·방탈출 이름에는 낱말이 섞여 들어가기 쉽다
    // ("푸어링 아트… 우주", "레코딩"). 낱말이 종류와 같으면(VR, 방탈출) 종류로 맞춘다.
    // 이름으로 맞춘 곳은 분야도 겹칠 때만 직접 관련으로 본다("우주미술관"은 우주비행사, "김홍신문학관"은 기자와 관련이 없다).
    val direct = near(
        open.filter { p ->
            job.keywords.any { k -> p.kind == k || (p.kind in INSTITUTION_KINDS && p.name.contains(k) && p.themes.any { it in job.themes }) }
        },
    )
        .take(limit)
        .map { RelatedPlace(it.first, it.second, direct = true) }
    if (direct.size >= limit) return direct
    val taken = direct.map { it.place }.toSet()
    val byTheme = near(open.filter { it !in taken && it.themes.any { t -> t in job.themes } })
        .take(limit - direct.size)
        .map { RelatedPlace(it.first, it.second, direct = false) }
    return direct + byTheme
}

/** 운동 시설 종류(장소 자료의 kind). */
private val SPORTS_KINDS = setOf("볼링장", "풋살장", "농구장", "야구연습장", "배드민턴장", "스케이트장", "클라이밍", "눈썰매", "열린 운동장")

/**
 * 이름으로 직업 낱말을 맞춰 볼 기관 종류(장소 자료의 kind). 이름이 곧 주제인 전시·체험 기관만 넣는다.
 * 도서관("숲속도서관", "음악도서관")과 캠핑장은 이름에 낱말이 들어가도 그 직업을 보여 주는 곳이 아니라 뺐다.
 */
private val INSTITUTION_KINDS = setOf("박물관", "과학관", "미술관", "전시관", "문학관", "공연장")
