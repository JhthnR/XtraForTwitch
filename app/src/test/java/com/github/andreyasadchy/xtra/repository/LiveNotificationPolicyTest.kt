package com.github.andreyasadchy.xtra.repository

import org.junit.Assert.*
import org.junit.Test

class LiveNotificationPolicyTest {
    @Test fun `fractional timestamps retain milliseconds on pre Oreo compatible parser`() {
        val base = liveNotificationStartedAt("2026-10-04T10:00:00Z")!!
        assertEquals(base + 123, liveNotificationStartedAt("2026-10-04T10:00:00.123456Z"))
        assertEquals(base + 100, liveNotificationStartedAt("2026-10-04T10:00:00.1Z"))
        assertEquals(base + 123, liveNotificationStartedAt("2026-10-04T12:00:00.123456789+02:00"))
        assertEquals(base, liveNotificationStartedAt("2026-10-04T05:00:00-05:00"))
    }

    @Test fun `malformed impossible missing and nonpositive timestamps cannot change dedup history`() {
        listOf(null, "", "bad", "2026-02-30T10:00:00Z", "2026-10-04T25:00:00Z",
            "2026-10-04T10:00:00Zextra", "1970-01-01T00:00:00Z", "1969-01-01T00:00:00Z")
            .forEach { assertNull(it, liveNotificationStartedAt(it)) }
    }
}
