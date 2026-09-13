package com.erykszczesniak.synchub.repository

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * The migrations are written to be portable, but only a real PostgreSQL proves that. This test boots
 * the hub against a throwaway PostgreSQL 16 container and checks that Flyway applied the baseline and
 * that Hibernate's schema validation (ddl-auto=validate) accepted the entity mapping.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class PostgresMigrationTest {
    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @Test
    fun `flyway baseline is applied on postgres and the database is utf-8`() {
        val versions =
            jdbc.queryForList(
                "select version from flyway_schema_history order by installed_rank",
                String::class.java,
            )
        assertThat(versions).containsExactly("1", "2", "3")

        val tables =
            jdbc.queryForList(
                "select table_name from information_schema.tables where table_schema = 'public'",
                String::class.java,
            )
        assertThat(
            tables,
        ).contains(
            "feed_watermark",
            "sync_run",
            "quarantined_record",
            "drift_event",
            "b_customer",
            "b_order",
            "change_event_outbox",
        )

        val encoding = jdbc.queryForObject("show server_encoding", String::class.java)
        assertThat(encoding).isEqualTo("UTF8")
    }

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")
    }
}
