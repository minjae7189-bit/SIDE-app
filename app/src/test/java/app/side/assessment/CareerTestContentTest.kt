package app.side.assessment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** assets/career_test_demo.json(데모 문항)으로 네 학교급(초등 저·고, 중, 고) 문항과 응답 문구를 확인한다. */
class CareerTestContentTest {

    private val content = CareerTestJson.parse(File("src/main/assets/career_test_demo.json").readText())

    @Test
    fun `학교급마다 모든 문항과 응답 6단계가 있다`() {
        assertEquals(Level.entries.toList(), content.levelLabels.keys.toList())
        Level.entries.forEach { level ->
            assertEquals(18, content.questions.size)
            content.questions.forEach { q ->
                val where = "$level ${content.factor(q).id}#${q.itemIndex}"
                assertTrue(where, content.text(q, level).isNotBlank())
                val options = content.options(q, level)
                assertEquals(where, 6, options.size)
                assertEquals("$where $options", 6, options.distinct().size)
                assertTrue("$where $options", options.none { it.isBlank() })
            }
            assertEquals(3, content.grades[level]?.size)
            assertTrue(content.subjectsFor(level).size >= 3)
        }
    }

    @Test
    fun `초등 문항은 중학생 문항과 다르다`() {
        content.questions.forEach { q ->
            val middle = content.text(q, Level.MIDDLE)
            assertTrue(content.text(q, Level.ELEMENTARY_LOW) != middle)
            assertTrue(content.text(q, Level.ELEMENTARY_HIGH) != middle)
            assertTrue(content.text(q, Level.ELEMENTARY_LOW) != content.text(q, Level.ELEMENTARY_HIGH))
        }
    }

    @Test
    fun `응답 문구를 그대로 말하면 그 번호`() {
        Level.entries.forEach { level ->
            content.questions.forEach { q ->
                val options = content.options(q, level)
                options.forEachIndexed { i, label ->
                    assertEquals("$level $label", VoiceAnswer.Scale(i + 1), parseVoiceAnswer(label, options))
                }
            }
        }
    }

    @Test
    fun `초등 적성·가치관 응답은 문구 없이 말로만 들어도 같은 번호`() {
        listOf(Level.ELEMENTARY_LOW, Level.ELEMENTARY_HIGH).forEach { level ->
            content.agreeLabels.getValue(level).forEachIndexed { i, label ->
                assertEquals("$level $label", VoiceAnswer.Scale(i + 1), parseVoiceAnswer(label))
            }
        }
    }
}
