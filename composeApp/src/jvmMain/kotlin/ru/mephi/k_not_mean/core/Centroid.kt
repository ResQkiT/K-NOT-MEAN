package ru.mephi.k_not_mean.core

data class Centroid(val coordinates: DoubleArray, val clusterId: Int) {
    val dimension: Int get() = coordinates.size
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Centroid) return false
        if (!coordinates.contentEquals(other.coordinates)) return false
        return clusterId == other.clusterId
    }

    override fun hashCode(): Int {
        var result = coordinates.contentHashCode()
        result = 31 * result + clusterId
        return result
    }
}