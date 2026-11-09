package com.btmicfix.miclab
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class SignalMetricsTest {
    private fun tone(hz: Double, gain: Double = 0.5) = ShortArray(4800) {
        (32767 * gain * sin(2 * PI * hz * it / 48000)).toInt().toShort()
    }
    @Test fun measuresKnownToneAndRejectsOffFrequency() {
        val target = SignalMetrics.measure(tone(997.0), 4800, 48000)
        assertEquals(-9.03, target.rmsDb, 0.15)
        assertEquals(-9.03, target.toneDb, 0.15)
        val other = SignalMetrics.measure(tone(440.0), 4800, 48000)
        assertTrue(other.toneDb < target.toneDb - 45)
    }
    @Test fun silenceAndGainAreWellDefined() {
        assertEquals(-120.0, SignalMetrics.measure(ShortArray(4800), 4800, 48000).rmsDb, 0.0)
        val loud = SignalMetrics.measure(tone(997.0), 4800, 48000)
        val quiet = SignalMetrics.measure(tone(997.0, 0.05), 4800, 48000)
        assertEquals(20.0, loud.toneDb - quiet.toneDb, 0.1)
    }
}
