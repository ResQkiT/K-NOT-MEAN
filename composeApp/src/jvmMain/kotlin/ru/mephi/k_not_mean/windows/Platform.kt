package ru.mephi.k_not_mean.windows

import ru.mephi.k_not_mean.core.NDPoint
import ru.mephi.k_not_mean.core.Point
import ru.mephi.k_not_mean.core.Point2D
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

class Platform {
    companion object{

        lateinit var delimiter : String
        private val CLUSTER_NAMES_SET = setOf("cluster", "label", "id", "class")

        fun openFileDialogAndParse(): List<Point>? {
            val dialog = FileDialog(null as Frame?, "Выберите CSV файл", FileDialog.LOAD)
            dialog.file = "*.csv;*.txt"
            dialog.isVisible = true
            val fileName = dialog.file
            val directory = dialog.directory

            if (fileName == null || directory == null) return null

            val file = File(directory, fileName)

            val rawPoints = parseCsvFile(file)

            return if (rawPoints.isNotEmpty()) {
                normalizePoints(rawPoints)
            } else {
                emptyList()
            }
        }

        /**
         * 📐 Нормализует координаты всех точек в списке в диапазон [0, 1]
         * с помощью метода Min-Max Scaling.
         * * Это необходимо для корректной визуализации на Canvas.
         */
        fun normalizePoints(points: List<Point>): List<Point> {
            if (points.isEmpty()) return emptyList()

            val dimension = points.first().dimension

            val minCoords = DoubleArray(dimension) { i ->
                points.minOf { it.coordinates[i] }
            }
            val maxCoords = DoubleArray(dimension) { i ->
                points.maxOf { it.coordinates[i] }
            }

            val normalizedPoints = points.map { oldPoint ->
                val newCoords = DoubleArray(dimension) { i ->
                    val range = maxCoords[i] - minCoords[i]

                    if (range == 0.0) {
                        0.5
                    } else {
                        (oldPoint.coordinates[i] - minCoords[i]) / range
                    }
                }

                when (dimension) {
                    2 -> Point2D(newCoords[0], newCoords[1], oldPoint.clusterId)
                    else -> NDPoint(newCoords, oldPoint.clusterId)
                }
            }

            return normalizedPoints
        }

        private fun parseCsvFile(file: File): List<Point> {
            val lines = file.readLines().filter { it.isNotBlank() }
            if (lines.isEmpty()) return emptyList()

            val headerRow = lines.first()

            delimiter = if (headerRow.contains(';')) ";" else ","

            val headers = headerRow.split(delimiter).map { it.trim().lowercase() }

            val clusterIndex = headers.indexOfFirst { CLUSTER_NAMES_SET.contains(it) }

            val coordinateIndices = headers.indices.filter { it != clusterIndex }

            val points = lines.drop(1).mapNotNull { line ->
                try {
                    val parts = line.split(delimiter).map { it.trim() }

                    if (parts.size != headers.size) return@mapNotNull null

                    val clusterId = if (clusterIndex != -1) {
                        parts[clusterIndex].toIntOrNull() ?: -1
                    } else {
                        -1
                    }

                    val coords = coordinateIndices.mapNotNull { index ->
                        parts.getOrNull(index)?.toDoubleOrNull()
                    }.toDoubleArray()

                    if (coords.size < 2) return@mapNotNull null

                    val point: Point = when (coords.size) {
                        2 -> Point2D(coords[0], coords[1], clusterId)
                        else -> NDPoint(coords, clusterId)
                    }

                    return@mapNotNull point

                } catch (e: Exception) {
                    println("Ошибка парсинга строки: $line (${e.message})")
                    null
                }
            }
            return points
        }
    }
}