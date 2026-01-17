package ru.mephi.k_not_mean.windows

import ru.mephi.k_not_mean.core.NDPoint
import ru.mephi.k_not_mean.core.Point
import ru.mephi.k_not_mean.core.Point2D
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import kotlinx.coroutines.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.system.measureTimeMillis

class Platform {
    companion object {
        lateinit var delimiter: String
        private val CLUSTER_NAMES_SET = setOf("cluster", "label", "id", "class")
        private val dispatcher = Dispatchers.Default
        private var debugEnabled = true
        private val logFile = File("parallel_debug.log")

        init {
            if (debugEnabled) {
                logFile.writeText("=== PARALLEL DATA LOADING ===\n")
                logFile.appendText("Start: ${LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}\n\n")
            }
        }

        suspend fun openFileDialogAndParse(): List<Point>? = withContext(Dispatchers.Main) {
            val dialog = FileDialog(null as Frame?, "Select CSV file", FileDialog.LOAD)
            dialog.file = "*.csv;*.txt"
            dialog.isVisible = true
            val fileName = dialog.file
            val directory = dialog.directory

            if (fileName == null || directory == null) return@withContext null

            val file = File(directory, fileName)

            logDebug("=== FILE LOADING START ===")
            logDebug("File: ${file.name}")
            logDebug("Size: ${file.length() / 1024} KB")
            logDebug("Available processors: ${Runtime.getRuntime().availableProcessors()}")
            logDebug("Max memory: ${Runtime.getRuntime().maxMemory() / (1024*1024)} MB")
            logDebug("Free memory: ${Runtime.getRuntime().freeMemory() / (1024*1024)} MB")

            val rawPoints: List<Point>
            val parsingTime = measureTimeMillis {
                rawPoints = withContext(dispatcher) {
                    parseCsvFile(file)
                }
            }
            logDebug("CSV parsing took: ${parsingTime} ms")

            return@withContext if (rawPoints.isNotEmpty()) {
                val normalizedPoints: List<Point>
                val normalizationTime = measureTimeMillis {
                    normalizedPoints = normalizePoints(rawPoints)
                }
                logDebug("Normalization took: ${normalizationTime} ms")
                logDebug("=== LOADING COMPLETED ===\n")
                normalizedPoints
            } else {
                logDebug("File contains no data")
                emptyList()
            }
        }

        fun normalizePoints(points: List<Point>): List<Point> = runBlocking {
            if (points.isEmpty()) return@runBlocking emptyList()

            logDebug("\n=== NORMALIZATION START ===")
            logDebug("Number of points: ${points.size}")
            logDebug("Dimension: ${points.first().dimension}")

            val dimension = points.first().dimension
            val availableProcessors = Runtime.getRuntime().availableProcessors()

            var minCoords: DoubleArray
            var maxCoords: DoubleArray
            val minMaxTime = measureTimeMillis {
                val result = withContext(dispatcher) {
                    val mins = DoubleArray(dimension) { Double.POSITIVE_INFINITY }
                    val maxs = DoubleArray(dimension) { Double.NEGATIVE_INFINITY }

                    val segmentCount = minOf(points.size, availableProcessors)
                    val segmentSize = (points.size + segmentCount - 1) / segmentCount // ceil division

                    logDebug("Segments: $segmentCount")
                    logDebug("Segment size: $segmentSize")

                    val segments = points.chunked(segmentSize)

                    val segmentResults = segments.mapIndexed { segmentIndex, segment ->
                        async {
                            logDebug("Segment $segmentIndex: processing ${segment.size} points started")
                            val localMins = DoubleArray(dimension) { Double.POSITIVE_INFINITY }
                            val localMaxs = DoubleArray(dimension) { Double.NEGATIVE_INFINITY }

                            for (point in segment) {
                                for (i in 0 until dimension) {
                                    val coord = point.coordinates[i]
                                    if (coord < localMins[i]) localMins[i] = coord
                                    if (coord > localMaxs[i]) localMaxs[i] = coord
                                }
                            }

                            logDebug("Segment $segmentIndex: processing completed")
                            Pair(localMins, localMaxs)
                        }
                    }

                    segmentResults.awaitAll().fold(Pair(mins, maxs)) { acc, segmentResult ->
                        val (segmentMins, segmentMaxs) = segmentResult
                        for (i in 0 until dimension) {
                            if (segmentMins[i] < acc.first[i]) acc.first[i] = segmentMins[i]
                            if (segmentMaxs[i] > acc.second[i]) acc.second[i] = segmentMaxs[i]
                        }
                        acc
                    }
                }
                minCoords = result.first
                maxCoords = result.second
            }

            logDebug("Min values: ${minCoords.joinToString(", ", limit = 5)}")
            logDebug("Max values: ${maxCoords.joinToString(", ", limit = 5)}")

            val normalizedPoints = withContext(dispatcher) {
                val segmentCount = minOf(points.size, availableProcessors)
                val segmentSize = (points.size + segmentCount - 1) / segmentCount // ceil division
                val segments = points.chunked(segmentSize)

                logDebug("Normalization threads: ${segments.size}")

                segments.mapIndexed { segmentIndex, segment ->
                    async {
                        logDebug("Normalization thread $segmentIndex: processing ${segment.size} points")
                        segment.map { oldPoint ->
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
                    }
                }.awaitAll().flatten()
            }

            logDebug("Min/Max calculation time: ${minMaxTime} ms")

            return@runBlocking normalizedPoints
        }

        private suspend fun parseCsvFile(file: File): List<Point> = withContext(dispatcher) {
            val lines = file.readLines().filter { it.isNotBlank() }
            if (lines.isEmpty()) return@withContext emptyList()

            logDebug("\n=== CSV PARSING ===")
            logDebug("Total lines in file: ${lines.size}")

            val headerRow = lines.first()
            delimiter = if (headerRow.contains(';')) ";" else ","
            logDebug("Delimiter: '$delimiter'")

            val headers = headerRow.split(delimiter).map { it.trim().lowercase() }
            logDebug("Headers (${headers.size}): ${headers.joinToString(", ")}")

            val clusterIndex = headers.indexOfFirst { CLUSTER_NAMES_SET.contains(it) }
            logDebug("Cluster index: $clusterIndex")

            val coordinateIndices = headers.indices.filter { it != clusterIndex }
            logDebug("Coordinate indices: ${coordinateIndices.joinToString(", ")}")

            val dataLines = lines.drop(1)
            logDebug("Data lines: ${dataLines.size}")

            val availableProcessors = Runtime.getRuntime().availableProcessors()
            val chunkSize = (dataLines.size / availableProcessors).coerceAtLeast(1)

            logDebug("Available processors: $availableProcessors")
            logDebug("Chunk size: $chunkSize")

            val points = if (dataLines.size < 1000) {
                logDebug("Using sequential parsing (small dataset)")
                parseLinesSequentially(dataLines, headers, clusterIndex, coordinateIndices)
            } else {
                logDebug("Using parallel parsing")
                parseLinesInParallel(dataLines, chunkSize, headers, clusterIndex, coordinateIndices)
            }

            logDebug("Loaded points: ${points.size}")
            return@withContext points
        }

        private fun parseLinesSequentially(
            lines: List<String>,
            headers: List<String>,
            clusterIndex: Int,
            coordinateIndices: List<Int>
        ): List<Point> {
            val points = mutableListOf<Point>()
            var parsedCount = 0
            var errorCount = 0

            for (line in lines) {
                val point = parseLine(line, headers, clusterIndex, coordinateIndices)
                if (point != null) {
                    points.add(point)
                    parsedCount++
                } else {
                    errorCount++
                }
            }

            logDebug("Successfully parsed: $parsedCount lines")
            logDebug("Parsing errors: $errorCount lines")
            return points
        }

        private suspend fun parseLinesInParallel(
            lines: List<String>,
            chunkSize: Int,
            headers: List<String>,
            clusterIndex: Int,
            coordinateIndices: List<Int>
        ): List<Point> = withContext(dispatcher) {
            val chunks = lines.chunked(chunkSize)
            logDebug("Created chunks: ${chunks.size}")

            val startTime = System.currentTimeMillis()

            val chunkResults = chunks.mapIndexed { chunkIndex, chunk ->
                async {
                    val threadName = Thread.currentThread().name
                    logDebug("Chunk $chunkIndex started in thread: $threadName")

                    val chunkStartTime = System.currentTimeMillis()
                    val localPoints = mutableListOf<Point>()
                    var parsedCount = 0
                    var errorCount = 0

                    for (line in chunk) {
                        val point = parseLine(line, headers, clusterIndex, coordinateIndices)
                        if (point != null) {
                            localPoints.add(point)
                            parsedCount++
                        } else {
                            errorCount++
                        }
                    }

                    val chunkTime = System.currentTimeMillis() - chunkStartTime
                    logDebug("Chunk $chunkIndex completed: $parsedCount points, $errorCount errors, time: ${chunkTime}ms")

                    localPoints
                }
            }

            val allPoints = chunkResults.awaitAll().flatten()
            val totalTime = System.currentTimeMillis() - startTime

            logDebug("All chunks completed in ${totalTime}ms")
            logDebug("Total points loaded: ${allPoints.size}")

            return@withContext allPoints
        }

        private fun parseLine(
            line: String,
            headers: List<String>,
            clusterIndex: Int,
            coordinateIndices: List<Int>
        ): Point? {
            return try {
                val parts = line.split(delimiter).map { it.trim() }

                if (parts.size != headers.size) return null

                val clusterId = if (clusterIndex != -1) {
                    parts[clusterIndex].toIntOrNull() ?: -1
                } else {
                    -1
                }

                val coords = coordinateIndices.mapNotNull { index ->
                    parts.getOrNull(index)?.toDoubleOrNull()
                }.toDoubleArray()

                if (coords.size < 2) return null

                val point: Point = when (coords.size) {
                    2 -> Point2D(coords[0], coords[1], clusterId)
                    else -> NDPoint(coords, clusterId)
                }

                point
            } catch (e: Exception) {
                null
            }
        }

        private fun logDebug(message: String) {
            if (debugEnabled) {
                val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"))
                val logMessage = "[$timestamp] $message"
                println(logMessage)
                try {
                    logFile.appendText("$logMessage\n")
                } catch (e: Exception) {
                    // Ignore file write errors
                }
            }
        }

        fun enableDebug(enable: Boolean) {
            debugEnabled = enable
        }

        fun getDebugLog(): String {
            return try {
                logFile.readText()
            } catch (e: Exception) {
                "Error reading log file: ${e.message}"
            }
        }

        fun clearDebugLog() {
            try {
                logFile.writeText("=== PARALLEL DATA LOADING ===\n")
                logFile.appendText("Start: ${LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}\n\n")
            } catch (e: Exception) {
                println("Error clearing log: ${e.message}")
            }
        }
    }
}