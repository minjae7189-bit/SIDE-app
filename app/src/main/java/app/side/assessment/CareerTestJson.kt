package app.side.assessment

import org.json.JSONArray
import org.json.JSONObject

/** assets/career_test_demo.json(데모용 예시 문항)을 [CareerTestContent]로 읽는다. */
object CareerTestJson {

    fun parse(text: String): CareerTestContent {
        val o = JSONObject(text)
        val intro = o.getJSONObject("intro")
        val levels = o.getJSONObject("levels")
        val cons = o.getJSONObject("consistency")
        val diff = o.getJSONObject("differentiation")
        return CareerTestContent(
            name = o.getString("name"),
            source = o.optString("source"),
            introLead = intro.getString("lead"),
            introSummary = intro.getString("summary"),
            introNotes = intro.getJSONArray("notes").objects().map { TextBlock(it.getString("title"), it.getString("body")) },
            levelLabels = Level.entries.associateWith { levels.getJSONObject(it.key).getString("label") },
            grades = Level.entries.associateWith { levels.getJSONObject(it.key).getJSONArray("grades").strings() },
            levelNotes = Level.entries.mapNotNull { l -> levels.getJSONObject(l.key).optString("note").takeIf { it.isNotEmpty() }?.let { l to it } }.toMap(),
            genders = o.getJSONArray("genders").strings(),
            regions = o.getJSONArray("regions").strings(),
            subjects = o.getJSONArray("subjects").strings(),
            levelSubjects = Level.entries.mapNotNull { l -> levels.getJSONObject(l.key).optJSONArray("subjects")?.let { l to it.strings() } }.toMap(),
            agreeLabels = Level.entries.mapNotNull { l -> levels.getJSONObject(l.key).optJSONArray("agree")?.let { l to it.strings() } }.toMap(),
            mildWords = Level.entries.mapNotNull { l -> levels.getJSONObject(l.key).optString("mild").takeIf { it.isNotEmpty() }?.let { l to it } }.toMap(),
            factors = Domain.entries.associateWith { d -> o.getJSONArray(d.key).objects().map { factor(it) } },
            attributes = Domain.entries.associateWith { d ->
                val a = o.getJSONObject("attributes").getJSONObject(d.key)
                a.keys().asSequence().associateWith { k ->
                    val v = a.getJSONObject(k)
                    Attribute(v.getString("group"), v.getString("predicate"))
                }
            },
            types = o.getJSONObject("types").let { t ->
                t.keys().asSequence().associateWith { k ->
                    val v = t.getJSONObject(k)
                    CareerType(k, v.getString("name"), v.getString("feature"), v.getJSONArray("jobs").strings())
                }
            },
            riasec = o.getJSONObject("riasec").let { r ->
                r.keys().asSequence().associate { k ->
                    val v = r.getJSONObject(k)
                    k[0] to RiasecInfo(
                        code = k[0],
                        name = v.getString("name"),
                        core = v.getString("core"),
                        easy = v.optString("easy"),
                        activities = v.optString("activities"),
                        tags = v.getJSONArray("tags").strings(),
                        jobs = v.getJSONArray("jobs").strings(),
                    )
                }
            },
            consistencyIntro = cons.getString("intro"),
            consistencyHigh = cons.getJSONObject("high").let { TextBlock(it.getString("title"), it.getString("desc"), it.getString("when")) },
            consistencyLow = cons.getJSONObject("low").let { TextBlock(it.getString("title"), it.getString("desc"), it.getString("when")) },
            differentiationIntro = diff.getString("intro"),
            differentiation = listOf("high_peaked", "high_flat", "low_peaked", "low_flat").associateWith { k ->
                diff.getJSONObject(k).let { TextBlock(it.getString("title"), it.getString("desc")) }
            },
        )
    }

    private val Level.key get() = name.lowercase()

    private val Domain.key get() = name.lowercase()

    private fun factor(o: JSONObject): Factor {
        val q = o.getJSONObject("q")
        return Factor(
            id = o.getString("id"),
            name = o.getString("name"),
            desc = o.optString("desc"),
            codes = o.getJSONArray("codes").strings().map { it[0] },
            attrs = o.getJSONArray("attrs").strings(),
            questions = Level.entries.associateWith { l ->
                when (val v = q.get(l.key)) {
                    is JSONArray -> v.strings()
                    else -> listOf(v.toString())
                }
            },
            scale = o.optJSONObject("scale")?.let { s ->
                Level.entries.mapNotNull { l ->
                    s.optJSONArray(l.key)?.let { arr -> l to (arr.getString(0) to arr.getString(1)) }
                }.toMap()
            } ?: emptyMap(),
        )
    }

    private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
}
