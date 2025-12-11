import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import kotlinx.coroutines.*
import ru.mephi.k_not_mean.core.KMeans
import ru.mephi.k_not_mean.core.Point
import ru.mephi.k_not_mean.core.TaskDimension
import ru.mephi.k_not_mean.windows.Platform
import java.awt.Color as AwtColor
import java.awt.image.BufferedImage
import kotlin.math.roundToInt

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
            when {
                points.isEmpty() -> {
                    Text("Нет данных", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
                else -> SmartDecimatedVisualizer(points)
            }
        }
    }
}

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

@Composable
fun SimpleClusterVisualizer(points: List<Point>) {
    // Фильтруем только 2D точки
    val visualPoints = remember(points) {
        points.filter { it.dimension >= 2 }
    }

    // Кэшируем цвета для кластеров
    val colorCache = remember(visualPoints.map { it.clusterId }.distinct().sorted()) {
        mutableMapOf<Int, Color>()
    }

    Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        val w = size.width
        val h = size.height

        visualPoints.forEach { point ->
            val x = point.coordinates[0]
            val y = point.coordinates[1]

            val color = if (point.clusterId < 0) {
                Color.Black
            } else {
                colorCache.getOrPut(point.clusterId) {
                    generateColorForCluster(point.clusterId)
                }
            }

            drawCircle(
                color = color,
                center = Offset(x.toFloat() * w, y.toFloat() * h),
                radius = 3f
            )
        }
    }
}

@Composable
fun BufferedClusterVisualizer(points: List<Point>) {
    var canvasSize by remember { mutableStateOf(Size.Zero) }
    var bufferedImage by remember { mutableStateOf<BufferedImage?>(null) }

    // Создаем/обновляем изображение при изменении точек или размера
    LaunchedEffect(points, canvasSize) {
        if (canvasSize.width > 0 && canvasSize.height > 0) {
            bufferedImage = createBufferedImage(points, canvasSize)
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                canvasSize = size.toSize()
            }
    ) {
        bufferedImage?.let { image ->
            // Рисуем буферизированное изображение напрямую через drawIntoCanvas
            drawIntoCanvas { canvas ->
                drawBufferedImage(canvas, image, size)
            }
        }
    }
}

private fun createBufferedImage(points: List<Point>, size: Size): BufferedImage {
    val width = size.width.roundToInt().coerceAtLeast(1)
    val height = size.height.roundToInt().coerceAtLeast(1)

    // Создаем BufferedImage с альфа-каналом
    val bufferedImage = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val graphics = bufferedImage.createGraphics()

    try {
        // Очищаем фон
        graphics.color = AwtColor(0xF5, 0xF5, 0xF5)
        graphics.fillRect(0, 0, width, height)

        // Фильтруем только 2D точки
        val visualPoints = points.filter { it.dimension >= 2 }
        if (visualPoints.isEmpty()) return bufferedImage

        // Группируем точки по кластерам для эффективной отрисовки
        val pointsByCluster = visualPoints.groupBy {
            if (it.clusterId < 0) -1 else it.clusterId
        }

        // Рисуем точки для каждого кластера
        pointsByCluster.forEach { (clusterId, clusterPoints) ->
            val color = if (clusterId < 0) {
                AwtColor.BLACK
            } else {
                getAwtColorForCluster(clusterId)
            }

            graphics.color = color

            clusterPoints.forEach { point ->
                val x = (point.coordinates[0] * width).toInt().coerceIn(0, width - 1)
                val y = (point.coordinates[1] * height).toInt().coerceIn(0, height - 1)

                // Рисуем точку как маленький круг
                graphics.fillOval(x - 1, y - 1, 3, 3)
            }
        }
    } finally {
        graphics.dispose()
    }

    return bufferedImage
}

private fun drawBufferedImage(canvas: androidx.compose.ui.graphics.Canvas, image: BufferedImage, size: Size) {
    // Получаем NativeCanvas для низкоуровневого рисования
    val nativeCanvas = canvas.nativeCanvas

    // Преобразуем BufferedImage в java.awt.Image и рисуем его
    val g2d = nativeCanvas as? java.awt.Graphics2D
    g2d?.drawImage(image, 0, 0, size.width.roundToInt(), size.height.roundToInt(), null)
}

private fun getAwtColorForCluster(clusterId: Int): AwtColor {
    val colors = arrayOf(
        AwtColor.RED,
        AwtColor.BLUE,
        AwtColor.GREEN,
        AwtColor.MAGENTA,
        AwtColor.CYAN,
        AwtColor.YELLOW,
        AwtColor(128, 0, 128),    // Purple
        AwtColor(255, 165, 0),    // Orange
        AwtColor(0, 206, 209),    // Dark Turquoise
        AwtColor(139, 69, 19)     // Saddle Brown
    )

    return if (clusterId < colors.size) {
        colors[clusterId]
    } else {
        // Генерируем цвет на основе ID кластера
        val hue = (clusterId * 137.508f) % 360f
        AwtColor.getHSBColor(hue / 360f, 0.8f, 0.9f)
    }
}

// Оптимизированный вариант с использованием drawPoints
@Composable
fun OptimizedClusterVisualizer(points: List<Point>) {
    // Фильтруем только 2D точки
    val visualPoints = remember(points) {
        points.filter { it.dimension >= 2 }
    }

    // Группируем по кластерам для batch рендеринга
    val pointsByCluster = remember(visualPoints) {
        visualPoints.groupBy {
            if (it.clusterId < 0) -1 else it.clusterId
        }
    }

    // Кэшируем цвета для кластеров
    val colorCache = remember(pointsByCluster.keys) {
        mutableMapOf<Int, Color>()
    }

    Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        val w = size.width
        val h = size.height

        pointsByCluster.forEach { (clusterId, clusterPoints) ->
            val color = if (clusterId < 0) {
                Color.Black
            } else {
                colorCache.getOrPut(clusterId) {
                    generateColorForCluster(clusterId)
                }
            }

            // Создаем массив смещений для всех точек кластера
            val offsets = clusterPoints.map { point ->
                Offset(
                    point.coordinates[0].toFloat() * w,
                    point.coordinates[1].toFloat() * h
                )
            }

            // Рисуем все точки кластера за один вызов
            if (offsets.isNotEmpty()) {
                drawPoints(
                    points = offsets,
                    pointMode = PointMode.Points,
                    color = color,
                    strokeWidth = 3f,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}
@Composable
fun SmartDecimatedVisualizer(points: List<Point>) {
    var canvasSize by remember { mutableStateOf(Size.Zero) }
    // Храним уже готовые к отрисовке данные: Map<Цвет, Список координат>
    var renderData by remember { mutableStateOf<Map<Color, List<Offset>>>(emptyMap()) }

    // Статистика для отладки (можно убрать в продакшене)
    var pointsStats by remember { mutableStateOf("") }

    LaunchedEffect(points, canvasSize) {
        if (canvasSize.width > 0 && points.isNotEmpty()) {
            val startTime = System.currentTimeMillis()

            // Тяжелая работа по фильтрации и маппингу
            val (data, totalPoints, visiblePoints) = withContext(Dispatchers.Default) {
                prepareDecimatedData(points, canvasSize)
            }

            renderData = data

            val time = System.currentTimeMillis() - startTime
            pointsStats = "Отрисовка: ${time}мс. Точек: $totalPoints -> $visiblePoints"
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { size -> canvasSize = size.toSize() }
        ) {
            // Отрисовка происходит моментально, так как массив renderData уже оптимизирован
            renderData.forEach { (color, offsets) ->
                drawPoints(
                    points = offsets,
                    pointMode = PointMode.Points,
                    color = color,
                    strokeWidth = 3f, // Размер точки
                    cap = StrokeCap.Round
                )
            }
        }

        // Отображение статистики (опционально)
        if (pointsStats.isNotEmpty()) {
            Text(
                text = pointsStats,
                style = MaterialTheme.typography.labelSmall,
                color = Color.Black.copy(alpha = 0.5f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .background(Color.White.copy(alpha = 0.7f), MaterialTheme.shapes.extraSmall)
                    .padding(horizontal = 4.dp)
            )
        }
    }
}

private data class ProcessResult(
    val data: Map<Color, List<Offset>>,
    val total: Int,
    val visible: Int
)

private fun prepareDecimatedData(points: List<Point>, size: Size): ProcessResult {
    val w = size.width
    val h = size.height

    // Grid Size: размер ячейки группировки в пикселях.
    // 1.0 = точность до пикселя (максимальное качество).
    // 2.0 = группировка 2x2 пикселя (еще быстрее, визуально почти не заметно).
    val pixelGridSize = 1.5f

    val visualPoints = points.filter { it.dimension >= 2 }

    // Используем parallelStream или просто группировку, но важно оптимизировать фильтрацию
    val grouped = visualPoints.groupBy { if (it.clusterId < 0) -1 else it.clusterId }

    var visibleCount = 0

    val result = grouped.mapKeys { (id, _) ->
        if (id < 0) Color.Black else generateColorForCluster(id)
    }.mapValues { (_, clusterPoints) ->

        // HashSet для хранения занятых ячеек сетки.
        // Используем Long, где старшие 32 бита - Y, младшие 32 бита - X.
        // Это избегает создания объектов для ключей Map.
        val occupiedCells = HashSet<Long>(clusterPoints.size / 2) // capacity hint
        val filteredOffsets = ArrayList<Offset>(clusterPoints.size / 2)

        for (point in clusterPoints) {
            // Переводим нормализованные координаты (0..1) в координаты экрана
            val screenX = point.coordinates[0] * w
            val screenY = point.coordinates[1] * h

            // Вычисляем индекс ячейки сетки
            val gridX = (screenX / pixelGridSize).toInt()
            val gridY = (screenY / pixelGridSize).toInt()

            // Создаем уникальный ключ ячейки (bit packing)
            // Работает корректно для экранов до 65535x65535 пикселей
            val cellKey = (gridY.toLong() shl 32) or (gridX.toLong() and 0xFFFFFFFFL)

            // Если ячейка еще не занята ЭТИМ кластером, добавляем точку
            if (occupiedCells.add(cellKey)) {
                filteredOffsets.add(Offset(screenX.toFloat(), screenY.toFloat()))
            }
        }

        visibleCount += filteredOffsets.size
        filteredOffsets
    }

    return ProcessResult(result, visualPoints.size, visibleCount)
}

// Вспомогательная функция цветов (та же самая)
private fun generateColorForCluster(clusterId: Int): Color {
    val baseColors = listOf(
        Color.Red, Color.Blue, Color.Green, Color.Magenta, Color.Cyan,
        Color.Yellow, Color(0xFF800080), Color(0xFFFFA500), Color(0xFF00CED1),
        Color(0xFF8B4513)
    )

    return if (clusterId < baseColors.size) {
        baseColors[clusterId]
    } else {
        val hue = (clusterId * 137.5f) % 360f
        Color.hsv(hue, 0.7f, 0.9f)
    }
}