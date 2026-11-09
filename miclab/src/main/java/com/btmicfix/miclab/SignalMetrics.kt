// Copyright 2026 BTMicFix Contributors. SPDX-License-Identifier: Apache-2.0
package com.btmicfix.miclab

import kotlin.math.*

data class Levels(val rmsDb: Double, val toneDb: Double, val clipped: Int)

/** Calibrated digital levels, not sound-pressure decibels. No samples are retained. */
object SignalMetrics {
    fun measure(samples: ShortArray, count: Int, rate: Int, hz: Double = 997.0): Levels {
        require(count in 1..samples.size)
        require(rate > 2 * hz)
        var energy = 0.0
        var real = 0.0
        var imaginary = 0.0
        var windowSum = 0.0
        var clipped = 0
        for (i in 0 until count) {
            val sample = samples[i] / 32768.0
            energy += sample * sample
            if (abs(samples[i].toInt()) >= 32760) clipped++
            val window = if (count == 1) 1.0 else 0.5 - 0.5 * cos(2 * PI * i / (count - 1))
            val angle = 2 * PI * hz * i / rate
            real += sample * window * cos(angle)
            imaginary += sample * window * sin(angle)
            windowSum += window
        }
        val rms = sqrt(energy / count)
        val toneRms = sqrt(2.0) * hypot(real, imaginary) / windowSum.coerceAtLeast(1e-12)
        fun db(value: Double) = 20 * log10(value.coerceAtLeast(1e-6))
        return Levels(db(rms), db(toneRms), clipped)
    }
}
