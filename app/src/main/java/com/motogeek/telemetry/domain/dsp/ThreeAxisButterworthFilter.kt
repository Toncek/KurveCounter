package com.motogeek.telemetry.domain.dsp

class ThreeAxisButterworthFilter(
    sampleRateHz: Double = 100.0,
    cutoffHz: Double = 3.5
) {
    private val filterX = BiquadButterworthFilter(sampleRateHz, cutoffHz)
    private val filterY = BiquadButterworthFilter(sampleRateHz, cutoffHz)
    private val filterZ = BiquadButterworthFilter(sampleRateHz, cutoffHz)

    fun process(x: Double, y: Double, z: Double): Triple<Double, Double, Double> {
        return Triple(
            filterX.process(x),
            filterY.process(y),
            filterZ.process(z)
        )
    }

    fun reset() {
        filterX.reset()
        filterY.reset()
        filterZ.reset()
    }
}
