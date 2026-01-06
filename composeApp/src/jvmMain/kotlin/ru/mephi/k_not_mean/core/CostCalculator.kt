package ru.mephi.k_not_mean.core

import kotlin.math.sqrt

object CostCalculator {

    fun calculateTotalCost(
        points: List<Point>,
        centroids: List<Centroid>,
        costModel: CostModel
    ): Double {

        val transportCost = points.sumOf { point ->
            val centroid = centroids.first { it.clusterId == point.clusterId }
            val distance = sqrt(
                KMeans.euclideanDistanceSquared(
                    point.coordinates,
                    centroid.coordinates
                )
            )
            distance * costModel.transportCostPerUnit
        }

        val buildCost = centroids.size * costModel.buildCost

        return buildCost + transportCost
    }
}
