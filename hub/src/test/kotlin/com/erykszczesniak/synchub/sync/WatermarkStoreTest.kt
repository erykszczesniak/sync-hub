package com.erykszczesniak.synchub.sync

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.time.Duration
import java.time.Instant

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(WatermarkStore::class)
class WatermarkStoreTest {
    @Autowired
    private lateinit var store: WatermarkStore

    private val t0 = Instant.parse("2026-01-01T00:00:00Z")

    @org.junit.jupiter.api.BeforeEach
    fun clean() {
        store.reset()
    }

    @Test
    fun `first run has no watermark and reads everything`() {
        assertThat(store.read("customers")).isNull()
        assertThat(store.incrementalWindow("customers", Duration.ofSeconds(5)).since).isNull()
    }

    @Test
    fun `incremental window starts an overlap before the watermark`() {
        store.advance("customers", Watermark(t0, "cus_9"))

        val window = store.incrementalWindow("customers", Duration.ofSeconds(5))

        assertThat(window.since).isEqualTo(t0.minusSeconds(5))
        assertThat(window.until).isNull()
    }

    @Test
    fun `watermark only ever moves forward`() {
        store.advance("customers", Watermark(t0.plusSeconds(60), "cus_2"))

        val afterBackwardsAttempt = store.advance("customers", Watermark(t0, "cus_1"))
        assertThat(afterBackwardsAttempt.timestamp).isEqualTo(t0.plusSeconds(60))
        assertThat(store.read("customers")!!.key).isEqualTo("cus_2")

        val advanced = store.advance("customers", Watermark(t0.plusSeconds(120), "cus_3"))
        assertThat(advanced.timestamp).isEqualTo(t0.plusSeconds(120))
        assertThat(store.read("customers")!!.key).isEqualTo("cus_3")
    }

    @Test
    fun `watermarks are independent per feed`() {
        store.advance("customers", Watermark(t0, null))

        assertThat(store.read("orders")).isNull()
    }
}
