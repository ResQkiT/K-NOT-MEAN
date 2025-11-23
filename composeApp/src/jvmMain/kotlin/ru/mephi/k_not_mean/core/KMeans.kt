package ru.mephi.k_not_mean.core

import androidx.compose.ui.input.pointer.PointerType

class KMeans {

    companion object{

        /**
         * Основная функция K-Means кластеризации.
         * * @param points Исходный список точек.
         * @param targetClusters Целевое количество кластеров (K).
         * @param maxIterations Максимальное количество итераций для сходимости.
         * @return Список точек с присвоенными новыми clusterId.
         */
        fun cluster(points: List<Point>, targetClusters: Int?, maxIterations: Int = 100): List<Point> {
            targetClusters!!
            if (points.isEmpty() || targetClusters <= 0) return points
            if (points.size < targetClusters) return points.map { it.copy() }

            val dimension = points.first().dimension

            var centroids = points.take(targetClusters).mapIndexed { index, point ->
                Centroid(point.coordinates, index)
            }
            var clusteredPoints = points.toList()

            for (i in 0 until maxIterations step 5) {
                val oldCentroids = centroids

                val assignments = assignPointsToCentroids(clusteredPoints, centroids)
                clusteredPoints = assignments.first

                val newCentroids = updateCentroids(clusteredPoints, targetClusters, dimension)

                if (hasConverged(oldCentroids, newCentroids)) {
                    println("K-Means завершился после $maxIterations итераций (не сошелся).")
                    clusteredPoints.forEach { println(it.clusterId) }
                    println()
                    println("K-Means сошелся за $i итераций.")
                    return clusteredPoints.toList()
                }

                centroids = newCentroids
            }

            println("K-Means завершился после $maxIterations итераций (не сошелся).")
            clusteredPoints.forEach { println(it.clusterId) }
            println()
            return clusteredPoints
        }


        /**
         * Вычисляет квадрат Евклидова расстояния между двумя точками.
         * Используем квадрат, чтобы избежать дорогостоящей операции квадратного корня.
         */
        fun euclideanDistanceSquared(p1: DoubleArray, p2: DoubleArray): Double {
            var sumOfSquares = 0.0
            for (i in p1.indices) {
                val diff = p1[i] - p2[i]
                sumOfSquares += diff * diff
            }
            return sumOfSquares
        }

        /**
         * Шаг 2: Назначает каждую точку ближайшему центроиду.
         * @return Pair<List<Point>, Boolean> - обновленные точки и флаг, были ли изменения.
         */
        fun assignPointsToCentroids(points: List<Point>, centroids: List<Centroid>): Pair<List<Point>, Boolean> {
            var pointsChanged = false

            val newPoints = points.map { oldPoint ->
                var minDistance = Double.MAX_VALUE
                var closestCentroidId = oldPoint.clusterId

                for (centroid in centroids) {
                    val dist = euclideanDistanceSquared(oldPoint.coordinates, centroid.coordinates)
                    if (dist < minDistance) {
                        minDistance = dist
                        closestCentroidId = centroid.clusterId
                    }
                }

                if (closestCentroidId != oldPoint.clusterId) {
                    pointsChanged = true
                    return@map when (oldPoint.dimension) {
                        2 -> Point2D(oldPoint.coordinates[0], oldPoint.coordinates[1], closestCentroidId)
                        else -> NDPoint(oldPoint.coordinates, closestCentroidId)
                    }
                }
                return@map oldPoint.copy()
            }
            return Pair(newPoints, pointsChanged)
        }

        /**
         * Шаг 3: Вычисляет новые центроиды как среднее арифметическое координат всех точек в кластере.
         */
        fun updateCentroids(points: List<Point>, targetClusters: Int, dimension: Int): List<Centroid> {
            // Группируем точки по присвоенному clusterId
            val grouped = points.groupBy { it.clusterId }

            val newCentroids = mutableListOf<Centroid>()

            for (k in 0 until targetClusters) {
                val clusterPoints = grouped[k]

                if (clusterPoints.isNullOrEmpty()) {
                    newCentroids.add(Centroid(DoubleArray(dimension) { 0.0 }, k))
                    continue
                }

                val newCenter = DoubleArray(dimension) { 0.0 }


                for (point in clusterPoints) {
                    for (i in 0 until dimension) {
                        newCenter[i] += point.coordinates[i]
                    }
                }

                val count = clusterPoints.size.toDouble()
                for (i in 0 until dimension) {
                    newCenter[i] /= count
                }

                newCentroids.add(Centroid(newCenter, k))
            }

            return newCentroids
        }

        /**
         * Шаг 4: Проверяет, сошлись ли центроиды (изменились ли они незначительно).
         */
        fun hasConverged(oldCentroids: List<Centroid>, newCentroids: List<Centroid>, tolerance: Double = 1e-4): Boolean {
            if (oldCentroids.size != newCentroids.size) return false

            for (i in oldCentroids.indices) {
                val distSquared = euclideanDistanceSquared(oldCentroids[i].coordinates, newCentroids[i].coordinates)
                if (distSquared > tolerance) {
                    return false
                }
            }
            return true
        }
    }
}