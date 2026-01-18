package ru.mephi.k_not_mean.core

import kotlinx.coroutines.*
import kotlin.system.measureTimeMillis

class KMeans {

    companion object {
        fun clusterWithAutoK(
            points: List<Point>,
            maxK: Int,
            costModel: CostModel,
            maxIterations: Int = 100,
            mode: ExecutionMode = ExecutionMode.SEQUENTIAL
        ): ClusteringResult {
            System.gc()
            require(maxK > 0)

            val bestClusterIds = IntArray(points.size)
            var bestCentroids: List<Centroid> = emptyList()
            var bestTotalCost = Double.MAX_VALUE
            var bestTimeMs = 0L

            val totalSearchTime = measureTimeMillis {
                var previousCost = Double.MAX_VALUE
                var growthCounter = 0

                val workingPoints = points.map { it.copy() }

                for (k in 1..maxK) {
                    val result = clusterInternal(
                        points = workingPoints,
                        targetClusters = k,
                        costModel = costModel,
                        maxIterations = maxIterations,
                        mode = mode
                    )
                    val currentCost = result.totalCost

                    if (currentCost < bestTotalCost) {
                        bestTotalCost = currentCost
                        bestCentroids = result.centroids
                        bestTimeMs = result.timeMs

                        for (i in workingPoints.indices) {
                            bestClusterIds[i] = workingPoints[i].clusterId
                        }
                    }


                    if (currentCost > previousCost) {
                        growthCounter++
                        if (growthCounter >= 2) break
                    } else {
                        growthCounter = 0
                    }
                    previousCost = currentCost
                }
            }

            val finalPoints = points.mapIndexed { index, point ->
                val newPoint = point.copy()
                newPoint.clusterId = bestClusterIds[index]
                newPoint
            }

            return ClusteringResult(
                points = finalPoints,
                centroids = bestCentroids,
                timeMs = totalSearchTime,
                totalCost = bestTotalCost
            )
        }

        private fun initializeCentroidsKMeansPP(points: List<Point>, k: Int): Array<Centroid> {
            val random = java.util.Random()
            val centroids = ArrayList<Centroid>()
            val n = points.size
            val firstIndex = random.nextInt(n)

            centroids.add(Centroid(points[firstIndex].coordinates.copyOf(), 0))

            val minDistsSq = DoubleArray(n) { Double.MAX_VALUE }

            for (clusterIndex in 1 until k) {
                val lastCentroid = centroids.last()
                var sumDistsSq = 0.0

                for (i in 0 until n) {
                    val distSq = euclideanDistanceSquared(points[i].coordinates, lastCentroid.coordinates)
                    if (distSq < minDistsSq[i]) {
                        minDistsSq[i] = distSq
                    }
                    sumDistsSq += minDistsSq[i]
                }
                var target = random.nextDouble() * sumDistsSq
                var nextCentroidIndex = 0

                for (i in 0 until n) {
                    target -= minDistsSq[i]
                    if (target <= 0) {
                        nextCentroidIndex = i
                        break
                    }
                }

                if (target > 0) nextCentroidIndex = n - 1

                centroids.add(Centroid(points[nextCentroidIndex].coordinates.copyOf(), clusterIndex))
            }

            return centroids.toTypedArray()
        }

        private fun clusterInternal(
            points: List<Point>,
            targetClusters: Int,
            costModel: CostModel,
            maxIterations: Int,
            mode: ExecutionMode
        ): ClusteringResult {
            if (points.isEmpty()) return ClusteringResult(points, emptyList(), 0, 0.0)

            val dimension = points.first().dimension

            var centroids = initializeCentroidsKMeansPP(points, targetClusters)

            val elapsedTime = measureTimeMillis {
                repeat(maxIterations) {
                    val changed = when (mode) {
                        ExecutionMode.SEQUENTIAL -> assignPointsSequential(points, centroids)
                        ExecutionMode.PARALLEL -> runBlocking { assignPointsParallel(points, centroids) }
                    }

                    val newCentroids = updateCentroidsOptimized(points, targetClusters, dimension)
                    if (!changed && hasConverged(centroids.toList(), newCentroids)) return@repeat
                    centroids = newCentroids.toTypedArray()
                }
            }
            val totalCost = CostCalculator.calculateTotalCost(points, centroids.toList(), costModel)
            return ClusteringResult(points, centroids.toList(), elapsedTime, totalCost)
        }

        private fun assignPointsSequential(points: List<Point>, centroids: Array<Centroid>): Boolean {
            var anyChanged = false
            for (point in points) {
                val oldId = point.clusterId
                var minDist = Double.MAX_VALUE
                var bestCluster = oldId

                for (centroid in centroids) {
                    val d = euclideanDistanceSquared(point.coordinates, centroid.coordinates)
                    if (d < minDist) {
                        minDist = d
                        bestCluster = centroid.clusterId
                    }
                }

                if (oldId != bestCluster) {
                    point.clusterId = bestCluster
                    anyChanged = true
                }
            }
            return anyChanged
        }

        private suspend fun assignPointsParallel(points: List<Point>, centroids: Array<Centroid>): Boolean = coroutineScope {
            val cpuCount = Runtime.getRuntime().availableProcessors()
            val chunkSize = (points.size + cpuCount - 1) / cpuCount

            val results = points.chunked(chunkSize).map { chunk ->
                async(Dispatchers.Default) {
                    var localChanged = false
                    for (point in chunk) {
                        val oldId = point.clusterId
                        var minDist = Double.MAX_VALUE
                        var bestId = oldId

                        for (centroid in centroids) {
                            val d = euclideanDistanceSquared(point.coordinates, centroid.coordinates)
                            if (d < minDist) {
                                minDist = d
                                bestId = centroid.clusterId
                            }
                        }
                        if (oldId != bestId) {
                            point.clusterId = bestId
                            localChanged = true
                        }
                    }
                    localChanged
                }
            }
            results.awaitAll().any { it }
        }

        private fun updateCentroidsOptimized(
            points: List<Point>,
            targetClusters: Int,
            dimension: Int
        ): List<Centroid> {
            val sums = Array(targetClusters) { DoubleArray(dimension) }
            val counts = IntArray(targetClusters)

            for (p in points) {
                val cId = p.clusterId
                if (cId == -1) continue
                counts[cId]++
                val sumArr = sums[cId]
                val coords = p.coordinates
                for (i in 0 until dimension) {
                    sumArr[i] += coords[i]
                }
            }

            return List(targetClusters) { i ->
                val count = counts[i]
                if (count > 0) {
                    for (j in 0 until dimension) {
                        sums[i][j] /= count
                    }
                }
                Centroid(sums[i], i)
            }
        }

        fun euclideanDistanceSquared(a: DoubleArray, b: DoubleArray): Double {
            var sum = 0.0
            for (i in a.indices) {
                val d = a[i] - b[i]
                sum += d * d
            }
            return sum
        }

        private fun hasConverged(
            oldCentroids: List<Centroid>,
            newCentroids: List<Centroid>,
            tolerance: Double = 1e-4
        ): Boolean {
            for (i in oldCentroids.indices) {
                if (euclideanDistanceSquared(oldCentroids[i].coordinates, newCentroids[i].coordinates) > tolerance)
                    return false
            }
            return true
        }
    }
}