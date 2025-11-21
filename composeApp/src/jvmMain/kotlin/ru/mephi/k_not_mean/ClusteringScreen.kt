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
import ru.mephi.k_not_mean.core.Point
import ru.mephi.k_not_mean.core.Point2D
import ru.mephi.k_not_mean.core.TaskDimension
import ru.mephi.k_not_mean.windows.Platform

@Composable
fun ClusteringScreen(

) {
    var selectedDimension by remember { mutableStateOf(TaskDimension.DIMENSION_2D) }
    var dimensionCount by remember { mutableStateOf("4") }
    var points by remember { mutableStateOf<List<Point2D>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Готов к работе") }

    fun performLoad() {
        isLoading = true
        statusMessage = "Выбор файла..."

        try {
            val loadedPoints = Platform.openFileDialogAndParse()
            if (loadedPoints != null) {
                points = loadedPoints.filterIsInstance<Point2D>()
                statusMessage = "Загружено ${loadedPoints.size} строк. Визуализируется ${points.size} точек (2D)."
            } else {
                statusMessage = "Загрузка отменена"
            }
        } catch (e: Exception) {
            statusMessage = "Ошибка: ${e.message}"
        } finally {
            isLoading = false
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
            onDimensionChange = { selectedDimension = it },
            onCountChange = { dimensionCount = it },
            onLoadClick = { performLoad() },
            isLoading = isLoading
        )

        Text(
            text = statusMessage,
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        Divider(modifier = Modifier.padding(vertical = 8.dp))

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
                if (selectedDimension == TaskDimension.DIMENSION_2D) {
                    ClusterVisualizer(points)
                } else {
                    Text("График доступен только в 2D", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlPanel(
    selectedDimension: TaskDimension,
    dimensionCount: String,
    onDimensionChange: (TaskDimension) -> Unit,
    onCountChange: (String) -> Unit,
    onLoadClick: () -> Unit,
    isLoading: Boolean
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
                TaskDimension.values().forEach { dim ->
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


        Button(
            onClick = onLoadClick,
            enabled = !isLoading,
            modifier = Modifier.height(50.dp),
            shape = MaterialTheme.shapes.small
        ) {
            if (isLoading) {
                CircularProgressIndicator(strokeWidth = 2.dp, color = Color.White)
            } else {
                Text("Файл", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
fun ClusterVisualizer(points: List<Point2D>) {
    val colors = listOf(Color.Red, Color.Blue, Color(0xFF008000), Color.Magenta, Color.Cyan)

    Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        val w = size.width
        val h = size.height

        points.forEach { point ->
            drawCircle(
                color = colors[point.clusterId % colors.size],
                center = Offset(point.x * w, point.y * h),
                radius = 3.dp.toPx()
            )
        }
    }
}