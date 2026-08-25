package com.softdread.widgets.core.time

import java.time.Instant
import java.time.ZoneId

/**
 * Indirection over "now" so every date calculation in the app is testable
 * without Robolectric or a device. Production code uses [SystemClock];
 * tests substitute a fixed instant and an explicit zone.
 */
interface Clock {
    fun now(): Instant
    fun zone(): ZoneId
}

object SystemClock : Clock {
    override fun now(): Instant = Instant.now()
    override fun zone(): ZoneId = ZoneId.systemDefault()
}

class FixedClock(private val instant: Instant, private val zone: ZoneId) : Clock {
    override fun now(): Instant = instant
    override fun zone(): ZoneId = zone
}
