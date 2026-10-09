package com.cadernoreceitas.testutil

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.time.Duration
import kotlin.time.toJavaDuration

/**
 * Relogio controlavel para os testes (o repositorio recebe um [Clock]).
 * Fuso UTC por padrao, entao "o dia" (`LocalDate.now(clock)`) e deterministico.
 */
class MutableClock(
    start: Instant = DEFAULT_START,
    private val zone: ZoneId = ZoneOffset.UTC,
) : Clock() {

    @Volatile private var current: Instant = start

    override fun getZone(): ZoneId = zone

    override fun withZone(zone: ZoneId): Clock = MutableClock(current, zone)

    override fun instant(): Instant = current

    fun advance(duration: Duration) {
        current = current.plus(duration.toJavaDuration())
    }

    fun set(instant: Instant) {
        current = instant
    }

    companion object {
        /** 2026-03-10 12:00 UTC (meio do dia, longe da virada de data). */
        val DEFAULT_START: Instant = Instant.parse("2026-03-10T12:00:00Z")
    }
}
