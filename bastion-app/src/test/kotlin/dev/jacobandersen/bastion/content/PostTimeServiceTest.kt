package dev.jacobandersen.bastion.content

import dev.jacobandersen.microformats2.Mf2Object
import dev.jacobandersen.microformats2.Mf2Value
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.ZoneId

class PostTimeServiceTest {
    private val service = PostTimeService(ZoneId.of("Asia/Manila"))

    private fun postOf(vararg entries: Pair<String, Mf2Value>) =
        Mf2Object(
            type = listOf("h-entry"),
            properties = entries.associate { (key, value) -> key to listOf(value) },
        )

    @Test
    fun `supplies published and updated on create when published is missing`() {
        val stamped = service.applyCreateTimestamps(postOf())

        assertTrue(stamped.getProperty("published").isNotEmpty())
        assertTrue(stamped.getProperty("updated").isNotEmpty())
    }

    @Test
    fun `normalizes an offset datetime on create`() {
        val stamped = service.applyCreateTimestamps(postOf("published" to Mf2Value.String("2019-06-01T10:00:00+05:00")))

        assertEquals("2019-06-01T10:00:00+05:00", (stamped.getFirstProperty("published") as Mf2Value.String).value)
    }

    @Test
    fun `normalizes an instant to utc on create`() {
        val stamped = service.applyCreateTimestamps(postOf("published" to Mf2Value.String("2019-06-01T02:00:00Z")))

        assertEquals("2019-06-01T02:00:00Z", (stamped.getFirstProperty("published") as Mf2Value.String).value)
    }

    @Test
    fun `applies the configured zone to naive local datetimes`() {
        val stamped = service.applyCreateTimestamps(postOf("published" to Mf2Value.String("2019-06-01T10:00:00")))

        assertEquals("2019-06-01T10:00:00+08:00", (stamped.getFirstProperty("published") as Mf2Value.String).value)
    }

    @Test
    fun `applies the configured zone to date-only values`() {
        val stamped = service.applyCreateTimestamps(postOf("published" to Mf2Value.String("2019-06-01")))

        assertEquals("2019-06-01T00:00:00+08:00", (stamped.getFirstProperty("published") as Mf2Value.String).value)
    }

    @Test
    fun `rejects invalid published values on create`() {
        assertThrows(IllegalArgumentException::class.java) {
            service.applyCreateTimestamps(postOf("published" to Mf2Value.String("not-a-date")))
        }
    }

    @Test
    fun `rejects non-string published values on create`() {
        assertThrows(IllegalArgumentException::class.java) {
            service.applyCreateTimestamps(postOf("published" to Mf2Value.Number(42)))
        }
    }

    @Test
    fun `leaves published alone on update when it is missing`() {
        val stamped = service.applyUpdateTimestamps(postOf("content" to Mf2Value.String("x")))

        assertEquals(emptyList<Mf2Value>(), stamped.getProperty("published"))
        assertTrue(stamped.getProperty("updated").isNotEmpty())
    }

    @Test
    fun `normalizes published on update`() {
        val stamped = service.applyUpdateTimestamps(postOf("published" to Mf2Value.String("2019-06-01T10:00:00")))

        assertEquals("2019-06-01T10:00:00+08:00", (stamped.getFirstProperty("published") as Mf2Value.String).value)
    }
}
