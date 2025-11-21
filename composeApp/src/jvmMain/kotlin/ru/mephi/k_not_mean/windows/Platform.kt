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
        fun openFileDialogAndParse(): List<Point>? {
            val dialog = FileDialog(null as Frame?, "Выберите CSV файл", FileDialog.LOAD)
            dialog.file = "*.csv;*.txt"
            dialog.isVisible = true
            val fileName = dialog.file
            val directory = dialog.directory
            if (fileName == null || directory == null) return null
            val file = File(directory, fileName)
            return parseCsvFile(file)
        }

        private val CLUSTER_NAMES_SET = setOf("cluster", "label", "id", "class")

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

