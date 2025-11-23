package ru.mephi.k_not_mean.core

class NDPoint(
    coordinates: DoubleArray,
    clusterId: Int = -1
) : Point(coordinates, clusterId) {
    constructor(vararg values: Double) : this(values, -1)

    override fun copy(): Point {
        return NDPoint(
            coordinates = this.coordinates.copyOf(),
            clusterId = clusterId
        )
    }
}