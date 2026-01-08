package ru.mephi.k_not_mean.core

import kotlinx.coroutines.*
import kotlin.system.measureTimeMillis

class KMeans {

    companion object {

        /**
         * Автоматический подбор K с ранней остановкой
         *
         * Предполагается унимодальность функции стоимости:
         * при росте K сначала стоимость уменьшается,
         * затем начинает расти из-за стоимости строительства.
         */
        fun clusterWithAutoK(
            points: List<Point>,
            maxK: Int,
            costModel: CostModel,
            maxIterations: Int = 100,
            mode: ExecutionMode = ExecutionMode.SEQUENTIAL
        ): ClusteringResult {

            require(maxK > 0)

            var bestResult: ClusteringResult? = null
            var previousCost = Double.MAX_VALUE
            var growthCounter = 0

            for (k in 1..maxK) {

                val result = cluster(
                    points = points,
                    targetClusters = k,
                    costModel = costModel,
                    maxIterations = maxIterations,
                    mode = mode
                )

                val currentCost = result.totalCost

                if (bestResult == null || currentCost < bestResult.totalCost) {
                    bestResult = result
                }

                /**
                 * Ранняя остановка:
                 * если стоимость начала расти несколько раз подряд —
                 * дальнейший перебор нецелесообразен
                 */
                if (currentCost > previousCost) {
                    growthCounter++
                    if (growthCounter >= 2) {
                        break
                    }
                } else {
                    growthCounter = 0
                }

                previousCost = currentCost
            }

            return bestResult!!
        }

        /**
         * Классический K-Means для фиксированного K
         */
        fun cluster(
            points: List<Point>,
            targetClusters: Int,
            costModel: CostModel,
            maxIterations: Int = 100,
            mode: ExecutionMode = ExecutionMode.SEQUENTIAL
        ): ClusteringResult {

            if (points.isEmpty() || targetClusters <= 0) {
                return ClusteringResult(points, emptyList(), 0, 0.0)
            }

            if (points.size < targetClusters) {
                return ClusteringResult(points.map { it.copy() }, emptyList(), 0, 0.0)
            }

            val dimension = points.first().dimension

            var centroids = points
                .take(targetClusters)
                .mapIndexed { index, point ->
                    Centroid(point.coordinates.copyOf(), index)
                }

            var clusteredPoints = points.map { it.copy() }

            val elapsedTime = measureTimeMillis {

                repeat(maxIterations) {

                    val oldCentroids = centroids

                    val (newPoints, _) = when (mode) {
                        ExecutionMode.SEQUENTIAL ->
                            assignPointsSequential(clusteredPoints, centroids)

                        ExecutionMode.PARALLEL ->
                            runBlocking {
                                assignPointsParallel(clusteredPoints, centroids)
                            }
                    }

                    clusteredPoints = newPoints

                    val newCentroids =
                        updateCentroids(clusteredPoints, targetClusters, dimension)

                    if (hasConverged(oldCentroids, newCentroids)) {
                        return@repeat
                    }

                    centroids = newCentroids
                }
            }

            val totalCost = CostCalculator.calculateTotalCost(
                clusteredPoints,
                centroids,
                costModel
            )

            return ClusteringResult(
                points = clusteredPoints,
                centroids = centroids,
                timeMs = elapsedTime,
                totalCost = totalCost
            )
        }

        /* ================= ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ================= */

        fun euclideanDistanceSquared(a: DoubleArray, b: DoubleArray): Double {
            var sum = 0.0
            for (i in a.indices) {
                val d = a[i] - b[i]
                sum += d * d
            }
            return sum
        }

        fun assignPointsSequential(
            points: List<Point>,
            centroids: List<Centroid>
        ): Pair<List<Point>, Boolean> {

            var changed = false

            val result = points.map { oldPoint ->
                var minDist = Double.MAX_VALUE
                var bestCluster = oldPoint.clusterId

                for (centroid in centroids) {
                    val d = euclideanDistanceSquared(
                        oldPoint.coordinates,
                        centroid.coordinates
                    )
                    if (d < minDist) {
                        minDist = d
                        bestCluster = centroid.clusterId
                    }
                }

                if (bestCluster != oldPoint.clusterId) {
                    changed = true
                    when (oldPoint.dimension) {
                        2 -> Point2D(
                            oldPoint.coordinates[0],
                            oldPoint.coordinates[1],
                            bestCluster
                        )
                        else -> NDPoint(
                            oldPoint.coordinates.copyOf(),
                            bestCluster
                        )
                    }
                } else {
                    oldPoint.copy()
                }
            }

            return Pair(result, changed)
        }

        suspend fun assignPointsParallel(
            points: List<Point>,
            centroids: List<Centroid>
        ): Pair<List<Point>, Boolean> = coroutineScope {

            val cpuCount = Runtime.getRuntime().availableProcessors()
            val chunkSize = (points.size + cpuCount - 1) / cpuCount
            val chunks = points.chunked(chunkSize)

            var changed = false

            val deferred = chunks.map { chunk ->
                async(Dispatchers.Default) {
                    chunk.map { oldPoint ->
                        var minDist = Double.MAX_VALUE
                        var bestCluster = oldPoint.clusterId

                        for (centroid in centroids) {
                            val d = euclideanDistanceSquared(
                                oldPoint.coordinates,
                                centroid.coordinates
                            )
                            if (d < minDist) {
                                minDist = d
                                bestCluster = centroid.clusterId
                            }
                        }

                        if (bestCluster != oldPoint.clusterId) {
                            changed = true
                            when (oldPoint.dimension) {
                                2 -> Point2D(
                                    oldPoint.coordinates[0],
                                    oldPoint.coordinates[1],
                                    bestCluster
                                )
                                else -> NDPoint(
                                    oldPoint.coordinates.copyOf(),
                                    bestCluster
                                )
                            }
                        } else {
                            oldPoint.copy()
                        }
                    }
                }
            }

            Pair(deferred.awaitAll().flatten(), changed)
        }

        fun updateCentroids(
            points: List<Point>,
            targetClusters: Int,
            dimension: Int
        ): List<Centroid> {

            val grouped = points.groupBy { it.clusterId }
            val centroids = mutableListOf<Centroid>()

            for (k in 0 until targetClusters) {
                val clusterPoints = grouped[k]

                if (clusterPoints.isNullOrEmpty()) {
                    centroids.add(Centroid(DoubleArray(dimension), k))
                    continue
                }

                val center = DoubleArray(dimension)

                for (p in clusterPoints) {
                    for (i in 0 until dimension) {
                        center[i] += p.coordinates[i]
                    }
                }

                for (i in 0 until dimension) {
                    center[i] /= clusterPoints.size
                }

                centroids.add(Centroid(center, k))
            }

            return centroids
        }

        fun hasConverged(
            oldCentroids: List<Centroid>,
            newCentroids: List<Centroid>,
            tolerance: Double = 1e-4
        ): Boolean {

            for (i in oldCentroids.indices) {
                val dist = euclideanDistanceSquared(
                    oldCentroids[i].coordinates,
                    newCentroids[i].coordinates
                )
                if (dist > tolerance) return false
            }
            return true
        }
    }
}
