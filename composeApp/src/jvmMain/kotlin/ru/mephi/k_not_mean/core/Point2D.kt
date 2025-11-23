package ru.mephi.k_not_mean.core

class Point2D(
    x: Double,
    y: Double,
    clusterId: Int = -1
) : Point(doubleArrayOf(x, y), clusterId) {
    val x: Double get() = coordinates[0]
    val y: Double get() = coordinates[1]

    override fun copy(): Point {
        return Point2D(
            x = this.x,
            y = this.y,
            clusterId = clusterId
        )
    }
}