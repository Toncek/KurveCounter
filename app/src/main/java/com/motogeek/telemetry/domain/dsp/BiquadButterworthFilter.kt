package com.motogeek.telemetry.domain.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

class BiquadButterworthFilter(
    sampleRateHz: Double = 100.0,
    cutoffHz: Double = 3.5
) {
    private var a0: Double = 0.0
    private var a1: Double = 0.0
    private var a2: Double = 0.0
    private var b1: Double = 0.0
    private var b2: Double = 0.0

    private var x1: Double = 0.0
    private var x2: Double = 0.0
    private var y1: Double = 0.0
    private var y2: Double = 0.0

    init {
        configure(sampleRateHz, cutoffHz)
    }

    private fun configure(sampleRateHz: Double, cutoffHz: Double) {
        val nyquist = sampleRateHz / 2.0
        val omega = 2.0 * PI * (cutoffHz / nyquist)
        val sn = sin(omega)
        val cs = cos(omega)
        val alpha = sn / (2.0 * 1.0 / sqrt(2.0))

        val b0 = (1.0 - cs) / 2.0
        val a0Temp = 1.0 + alpha

        a0 = b0 / a0Temp
        a1 = (1.0 - cs) / a0Temp
        a2 = b0 / a0Temp
        b1 = (-2.0 * cs) / a0Temp
        b2 = (1.0 - alpha) / a0Temp
    }

    fun process(sample: Double): Double {
        val y = a0 * sample + a1 * x1 + a2 * x2 - b1 * y1 - b2 * y2
        x2 = x1
        x1 = sample
        y2 = y1
        y1 = y
        return y
    }

    fun reset() {
        x1 = 0.0
        x2 = 0.0
        y1 = 0.0
        y2 = 0.0
    }
}
