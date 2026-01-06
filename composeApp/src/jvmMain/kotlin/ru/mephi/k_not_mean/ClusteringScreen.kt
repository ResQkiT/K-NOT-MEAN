package ru.mephi.k_not_mean

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.mephi.k_not_mean.core.*
import ru.mephi.k_not_mean.windows.Platform

@Composable
fun ClusteringScreen() {

    val scope = rememberCoroutineScope()

    var executionMode by remember { mutableStateOf(ExecutionMode.SEQUENTIAL) }

    var maxKText by remember { mutableStateOf("10") }
    var buildCostText by remember { mutableStateOf("1000") }
    var transportCostText by remember { mutableStateOf("1.0") }

    val points = remember { mutableStateListOf<Point>() }
    var centroids by remember { mutableStateOf<List<Centroid>>(emptyList()) }

    var status by remember { mutableStateOf("Готово") }
    var isBusy by remember { mutableStateOf(false) }

    /* ================= ЗАГРУЗКА ФАЙЛА ================= */

    fun loadFile() {
        isBusy = true
        status = "Загрузка файла..."

        scope.launch {
            val loaded = withContext(Dispatchers.IO) {
                Platform.openFileDialogAndParse()
            }

            if (loaded != null) {
                points.clear()
                points.addAll(loaded)
                centroids = emptyList()
                status = "Загружено ${loaded.size} точек"
            } else {
                status = "Загрузка отменена"
            }

            isBusy = false
        }
    }

    /* ================= КЛАСТЕРИЗАЦИЯ ================= */

    fun runClustering() {

        val maxK = maxKText.toIntOrNull()
        val buildCost = buildCostText.toDoubleOrNull()
        val transportCost = transportCostText.toDoubleOrNull()

        if (points.isEmpty()) {
            status = "Нет данных"
            return
        }

        if (maxK == null || maxK <= 0) {
            status = "Некорректный Max K"
            return
        }

        if (buildCost == null || transportCost == null) {
            status = "Некорректные стоимости"
            return
        }

        val costModel = CostModel(buildCost, transportCost)

        isBusy = true
        status = "Поиск оптимального K..."

        scope.launch(Dispatchers.Default) {

            val result = KMeans.clusterWithAutoK(
                points = points,
                maxK = maxK,
                costModel = costModel,
                mode = executionMode
            )

            withContext(Dispatchers.Main) {
                points.clear()
                points.addAll(result.points)
                centroids = result.centroids

                status =
                    "Оптимальный K=${centroids.size} | " +
                            "Стоимость=${"%.2f".format(result.totalCost)} | " +
                            "${result.timeMs} мс"

                isBusy = false
            }
        }
    }

    /* ================= UI ================= */

    Column(
        modifier = Modifier.fillMaxSize().padding(12.dp)
    ) {

        Text("K-Means с автоматическим подбором K", style = MaterialTheme.typography.titleMedium)

        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Режим:")
            Spacer(Modifier.width(8.dp))
            RadioButton(
                selected = executionMode == ExecutionMode.SEQUENTIAL,
                onClick = { executionMode = ExecutionMode.SEQUENTIAL }
            )
            Text("SEQ")
            Spacer(Modifier.width(8.dp))
            RadioButton(
                selected = executionMode == ExecutionMode.PARALLEL,
                onClick = { executionMode = ExecutionMode.PARALLEL }
            )
            Text("PAR")
        }

        Spacer(Modifier.height(8.dp))

        Row {
            OutlinedTextField(
                value = maxKText,
                onValueChange = { if (it.all(Char::isDigit)) maxKText = it },
                label = { Text("Max K") },
                modifier = Modifier.width(100.dp),
                singleLine = true
            )

            Spacer(Modifier.width(8.dp))

            OutlinedTextField(
                value = buildCostText,
                onValueChange = { buildCostText = it },
                label = { Text("Цена постройки") },
                modifier = Modifier.width(160.dp),
                singleLine = true
            )

            Spacer(Modifier.width(8.dp))

            OutlinedTextField(
                value = transportCostText,
                onValueChange = { transportCostText = it },
                label = { Text("Цена перевозки") },
                modifier = Modifier.width(160.dp),
                singleLine = true
            )
        }

        Spacer(Modifier.height(8.dp))

        Row {
            Button(onClick = ::runClustering, enabled = !isBusy) {
                Text("Старт")
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = ::loadFile, enabled = !isBusy) {
                Text("Файл")
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(status, color = Color.Gray)

        Spacer(Modifier.height(8.dp))

        Box(
            modifier = Modifier.fillMaxSize()
                .border(1.dp, Color.LightGray)
                .background(Color(0xFFF5F5F5))
        ) {
            if (points.isNotEmpty()) {
                PointsCanvas(points, centroids)
            }
        }
    }
}

/* ================= CANVAS ================= */

@Composable
fun PointsCanvas(
    points: List<Point>,
    centroids: List<Centroid>
) {
    val colors = listOf(
        Color.Red, Color.Blue, Color.Green,
        Color.Magenta, Color.Cyan, Color.Black
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        points.forEach { p ->
            if (p.dimension >= 2 && p.clusterId >= 0) {
                drawCircle(
                    color = colors[p.clusterId % colors.size],
                    radius = 4f,
                    center = Offset(
                        (p.coordinates[0] * w).toFloat(),
                        (p.coordinates[1] * h).toFloat()
                    )
                )
            }
        }

        centroids.forEach { c ->
            drawCircle(
                color = Color.Black,
                radius = 8f,
                center = Offset(
                    (c.coordinates[0] * w).toFloat(),
                    (c.coordinates[1] * h).toFloat()
                )
            )
        }
    }
}
