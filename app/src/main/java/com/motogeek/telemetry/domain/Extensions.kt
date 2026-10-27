package com.motogeek.telemetry.domain

fun String?.toSafeDouble(default: Double = 0.0): Double {
    if (this == null) return default
    val clean = this.trim().replace(',', '.')
    return clean.toDoubleOrNull()?.takeIf { it.isFinite() } ?: default
}

fun String?.toSafeInt(default: Int = 0): Int {
    if (this == null) return default
    val clean = this.trim()
    return clean.toIntOrNull() ?: default
}
