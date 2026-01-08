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
            require(maxK > 0)

            var bestResult: ClusteringResult? = null
            var previousCost = Double.MAX_VALUE
            var growthCounter = 0

            // Оптимизация: работаем с одним и тем же набором данных,
            // меняя только id кластера внутри
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

                if (bestResult == null || currentCost < bestResult.totalCost) {
                    // Копируем результат для сохранения лучшего состояния
                    bestResult = result.copy(points = result.points.map { it.copy() })
                }

                if (currentCost > previousCost) {
                    growthCounter++
                    if (growthCounter >= 2) break
                } else {
                    growthCounter = 0
                }
                previousCost = currentCost
            }

            return bestResult!!
        }

        /**
         * Внутренний метод для работы с мутабельными точками
         */
        private fun clusterInternal(
            points: List<Point>,
            targetClusters: Int,
            costModel: CostModel,
            maxIterations: Int,
            mode: ExecutionMode
        ): ClusteringResult {
            if (points.isEmpty()) return ClusteringResult(points, emptyList(), 0, 0.0)

            val dimension = points.first().dimension

            // Инициализация центроидов (используем For-цикл вместо map для Desktop/JVM скорости)
            var centroids = Array(targetClusters) { i ->
                Centroid(points[i].coordinates.copyOf(), i)
            }

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

        /* ================= ОПТИМИЗИРОВАННЫЕ МЕТОДЫ ================= */

        /**
         * Изменяем clusterId прямо в существующих объектах Point.
         * Возвращаем true, если хотя бы одна точка сменила кластер.
         */
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

        /**
         * Уход от groupBy. Используем массивы-аккумуляторы.
         */
        private fun updateCentroidsOptimized(
            points: List<Point>,
            targetClusters: Int,
            dimension: Int
        ): List<Centroid> {
            val sums = Array(targetClusters) { DoubleArray(dimension) }
            val counts = IntArray(targetClusters)

            // Проход один раз по всем точкам (O(N))
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