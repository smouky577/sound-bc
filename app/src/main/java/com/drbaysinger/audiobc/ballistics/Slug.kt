package com.drbaysinger.audiobc.ballistics

/** 1 grain in grams. */
const val GRAIN_G = 0.06479891

/** Kinetic energy in joules for a slug of [grams] at [speed] m/s: E = ½·m·v². */
fun energyJoules(grams: Double, speed: Double): Double = 0.5 * (grams / 1000.0) * speed * speed

/** Sectional density in lb/in²: (weight in grains / 7000) / d². */
fun sectionalDensity(grams: Double, caliberMm: Double): Double {
    val inches = caliberMm / 25.4
    return grams / GRAIN_G / 7000.0 / (inches * inches)
}

/** Form factor relative to the drag model: i = SD / BC. */
fun formFactor(sd: Double, bc: Double): Double = sd / bc

/** Drag coefficient of the projectile itself at [mach]: Cd = i · Cd_ref(M). */
fun projectileCd(i: Double, table: DragTable, mach: Double): Double = i * table.cd(mach)
