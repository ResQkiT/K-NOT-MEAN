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
import ru.mephi.k_not_mean.core.KMeans
import ru.mephi.k_not_mean.core.Point
import ru.mephi.k_not_mean.core.TaskDimension
import ru.mephi.k_not_mean.windows.Platform

@Composable
fun ClusteringScreen() {
    val coroutineScope = rememberCoroutineScope()

    var selectedDimension by remember { mutableStateOf(TaskDimension.DIMENSION_2D) }
    var dimensionCount by remember { mutableStateOf("4") }

    val points = remember { mutableStateListOf<Point>() }
    var isLoading by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Готов к работе") }

    var targetClustersCount by remember { mutableStateOf("3") }


    val performLoad: () -> Unit = {
        isLoading = true
        statusMessage = "Выбор файла..."

        coroutineScope.launch {
            try {
                val loadedPoints = withContext(Dispatchers.IO) {
                    Platform.openFileDialogAndParse()
                }

                if (loadedPoints != null) {
                    points.clear()
                    points.addAll(loadedPoints)
                    val visualizableCount = loadedPoints.count { it.dimension == 2 }
                    statusMessage = "Загружено ${loadedPoints.size} строк. Визуализируется ${visualizableCount} точек."
                } else {
                    statusMessage = "Загрузка отменена"
                }
            } catch (e: Exception) {
                statusMessage = "Ошибка: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }


    val performClustering: () -> Unit = fun() {

        if (points.isEmpty()) {
            statusMessage = "Ошибка: Сначала загрузите данные."
            return
        }

        val k = targetClustersCount.toIntOrNull()
        val maxK = points.size.toString()

        if (k == null || k < 1 || k > points.size) {
            statusMessage = "Ошибка: Неверное K. K должно быть от 1 до $maxK."
            return
        }

        isProcessing = true
        statusMessage = "Кластеризация (K=$k)..."

        coroutineScope.launch(Dispatchers.Default) {
            try {
                val clusteredResult = KMeans.cluster(points, k)
                points.clear()
                points.addAll(clusteredResult)
                statusMessage = "Кластеризация успешно завершена. Найдено $k кластеров."
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    statusMessage = "Ошибка кластеризации: ${e.message}"
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isProcessing = false
                }
            }
        }
    }


    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Text(
            text = "Кластеризация Данных",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        ControlPanel(
            selectedDimension = selectedDimension,
            dimensionCount = dimensionCount,
            targetClustersCount = targetClustersCount,
            onDimensionChange = { selectedDimension = it },
            onCountChange = { dimensionCount = it },
            onClusterCountChange = { targetClustersCount = it },
            onLoadClick = performLoad,
            onProcessDots = performClustering,
            isLoading = isLoading,
            isProcessing = isProcessing
        )

        Text(
            text = statusMessage,
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .border(1.dp, Color.LightGray, MaterialTheme.shapes.small)
                .background(Color(0xFFF5F5F5)),
            contentAlignment = Alignment.Center
        ) {
            if (points.isEmpty()) {
                Text("Нет данных", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            } else {
                ClusterVisualizer(points)
            }
        }
    }
}

// -----------------------------------------------------------
// КОМПОНЕНТ ПАНЕЛИ УПРАВЛЕНИЯ
// -----------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlPanel(
    selectedDimension: TaskDimension,
    dimensionCount: String,
    targetClustersCount: String,
    onDimensionChange: (TaskDimension) -> Unit,
    onCountChange: (String) -> Unit,
    onClusterCountChange: (String) -> Unit,
    onLoadClick: () -> Unit,
    onProcessDots: () -> Unit,
    isLoading: Boolean,
    isProcessing: Boolean
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.weight(1f)
        ) {
            OutlinedTextField(
                value = selectedDimension.label,
                onValueChange = {},
                readOnly = true,
                label = { Text("Тип задачи", style = MaterialTheme.typography.labelSmall) },
                textStyle = MaterialTheme.typography.bodySmall,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                TaskDimension.entries.forEach { dim ->
                    DropdownMenuItem(
                        text = { Text(dim.label, style = MaterialTheme.typography.bodySmall) },
                        onClick = {
                            onDimensionChange(dim)
                            expanded = false
                        }
                    )
                }
            }
        }

        if (selectedDimension == TaskDimension.MULTI_DIM) {
            OutlinedTextField(
                value = dimensionCount,
                onValueChange = { if (it.all { char -> char.isDigit() }) onCountChange(it) },
                label = { Text("N", style = MaterialTheme.typography.labelSmall) },
                textStyle = MaterialTheme.typography.bodySmall,
                modifier = Modifier.width(60.dp),
                singleLine = true
            )
        }

        OutlinedTextField(
            value = targetClustersCount,
            onValueChange = { if (it.all { char -> char.isDigit() } && it.isNotEmpty()) onClusterCountChange(it) },
            label = { Text("K", style = MaterialTheme.typography.labelSmall) },
            textStyle = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(60.dp),
            singleLine = true
        )


        Button(
            onClick = onProcessDots,
            enabled = !isProcessing && !isLoading,
            modifier = Modifier.height(50.dp),
            shape = MaterialTheme.shapes.small
        ) {
            if (isProcessing) {
                CircularProgressIndicator(strokeWidth = 2.dp, color = Color.White)
            } else {
                Text("Кластеризовать!", style = MaterialTheme.typography.labelMedium)
            }
        }
        Button(
            onClick = onLoadClick,
            enabled = !isLoading && !isProcessing,
            modifier = Modifier.height(50.dp),
            shape = MaterialTheme.shapes.small
        ){
            if (isLoading) {
                CircularProgressIndicator(strokeWidth = 2.dp, color = Color.White)
            } else {
                Text("Файл", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

// -----------------------------------------------------------
// КОМПОНЕНТ ВИЗУАЛИЗАЦИИ
// -----------------------------------------------------------

@Composable
fun ClusterVisualizer(points: List<Point>) {
    val colors = remember {
        listOf(
            Color.Red, Color.Blue, Color(0xFF008000), Color.Magenta, Color.Cyan,
            Color.Yellow, Color.Green, Color.Gray, Color(0xFFFFA500), Color(0xFF800080)
        )
    }

    Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        val w = size.width
        val h = size.height

        points.forEach { point ->
            val x = point.coordinates.getOrNull(0) ?: 0.0
            val y = point.coordinates.getOrNull(1) ?: 0.0

            val color = if (point.clusterId < 0) {
                Color.Black
            } else {
                colors[point.clusterId % colors.size]
            }

            drawCircle(
                color = color,
                center = Offset(x.toFloat() * w, y.toFloat() * h),
                radius = 3.dp.toPx()
            )
        }
    }
}