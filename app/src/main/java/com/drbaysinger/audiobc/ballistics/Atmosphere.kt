package com.drbaysinger.audiobc.ballistics

import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Air density of a dry-air / water-vapor mixture and the speed of sound.
 *
 * Saturation vapor pressure uses the Magnus formula with the Alduchov & Eskridge (1996)
 * coefficients: es = 6.1094 * exp(17.625 * T / (T + 243.04)), T in °C, es in hPa.
 * Partial vapor pressure is e = (RH/100) * es. Dry-air pressure is p − e.
 * rho = pd / (Rd * T) + e / (Rv * T), with pressures in pascals.
 */
object Atmosphere {
    private const val RD = 287.058
    private const val RV = 461.495

    fun speedOfSound(temperatureC: Double): Double {
        val kelvin = temperatureC + 273.15
        return 20.05 * sqrt(kelvin)
    }

    fun density(temperatureC: Double, pressureHpa: Double, humidityPercent: Double): Double {
        val kelvin = temperatureC + 273.15
        require(kelvin > 0.0)
        val saturation = 6.1094 * exp(17.625 * temperatureC / (temperatureC + 243.04))
        val vapor = (humidityPercent / 100.0).coerceIn(0.0, 1.0) * saturation
        val dry = (pressureHpa - vapor).coerceAtLeast(0.0)
        return dry * 100.0 / (RD * kelvin) + vapor * 100.0 / (RV * kelvin)
    }
}
