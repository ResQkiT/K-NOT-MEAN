package ru.mephi.k_not_mean.core

class Point2D(
    x: Double,
    y: Double,
    clusterId: Int = -1
) : Point(doubleArrayOf(x, y), clusterId) {
    val x: Float get() = coordinates[0].toFloat()
    val y: Float get() = coordinates[1].toFloat()
}