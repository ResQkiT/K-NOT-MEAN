package ru.mephi.k_not_mean

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import ru.mephi.k_not_mean.core.*
import ru.mephi.k_not_mean.windows.Platform
import java.io.File

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    secondary = Color(0xFFCCC2DC),
    tertiary = Color(0xFFEFB8C8),
    surface = Color(0xFF1C1B1F),
    background = Color(0xFF121212),
    outline = Color(0xFF938F99)
)

@Composable
fun ClusteringScreen() {
    MaterialTheme(colorScheme = DarkColorScheme) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val scope = rememberCoroutineScope()
            var executionMode by remember { mutableStateOf(ExecutionMode.SEQUENTIAL) }
            var maxKText by remember { mutableStateOf("10") }
            var buildCostText by remember { mutableStateOf("1000") }
            var transportCostText by remember { mutableStateOf("1.0") }
            val points = remember { mutableStateListOf<Point>() }
            var centroids by remember { mutableStateOf<List<Centroid>>(emptyList()) }
            var status by remember { mutableStateOf("Ожидание файла") }
            var isBusy by remember { mutableStateOf(false) }


            fun loadFile() {
                isBusy = true
                status = "Загрузка..."
                scope.launch {
                    val loaded = withContext(Dispatchers.IO) { Platform.openFileDialogAndParse() }
                    if (loaded != null) {
                        points.clear()
                        points.addAll(loaded)
                        val restoredCentroids = withContext(Dispatchers.Default) {
                            restoreCentroidsFromPoints(loaded)
                        }
                        centroids = restoredCentroids

                        status = "Файл загружен (${restoredCentroids.size} кластеров)"
                    } else {
                        status = "Загрузка отменена"
                    }
                    isBusy = false
                }
            }

            fun runClustering() {
                val maxK = maxKText.toIntOrNull() ?: 10
                val costModel = CostModel(buildCostText.toDoubleOrNull() ?: 1000.0, transportCostText.toDoubleOrNull() ?: 1.0)

                if (points.isEmpty()) { status = "Загрузите данные!"; return }

                isBusy = true
                status = "Вычисление..."
                scope.launch(Dispatchers.Default) {
                    val result = KMeans.clusterWithAutoK(points, maxK, costModel, mode = executionMode)
                    withContext(Dispatchers.Main) {
                        points.clear()
                        points.addAll(result.points)
                        centroids = result.centroids
                        status = "Готово за ${result.timeMs}мс"
                        isBusy = false
                    }
                }
            }

            fun saveResults() {
                scope.launch(Dispatchers.IO) {
                    val content = points.joinToString("\n") { p ->
                        "${p.coordinates.joinToString(",")},${p.clusterId}"
                    }

                    val path = Platform.saveFileDialog("clusters.csv")
                    if (path != null) {
                        File(path).writeText("x,y,cluster_id\n$content")
                        withContext(Dispatchers.Main) { status = "Сохранено: $path" }
                    }
                }
            }

            Row(modifier = Modifier.fillMaxSize()) {
                Card(
                    modifier = Modifier.width(320.dp).fillMaxHeight().padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                        Text("Управление", style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(16.dp))
                        StatisticsBlock(points.size, centroids.size)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        Text("Режим вычислений", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(4.dp)
                        ) {
                            ExecutionModeOption(
                                label = "SEQ",
                                isSelected = executionMode == ExecutionMode.SEQUENTIAL,
                                modifier = Modifier.weight(1f),
                                onClick = { executionMode = ExecutionMode.SEQUENTIAL }
                            )
                            ExecutionModeOption(
                                label = "PAR",
                                isSelected = executionMode == ExecutionMode.PARALLEL,
                                modifier = Modifier.weight(1f),
                                onClick = { executionMode = ExecutionMode.PARALLEL }
                            )
                        }

                        Spacer(Modifier.height(16.dp))

                        // Настройки параметров
                        Text("Параметры", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        StyledTextField("Max K", maxKText, Icons.Default.FormatListNumbered) { if (it.all(Char::isDigit)) maxKText = it }
                        Spacer(Modifier.height(8.dp))
                        StyledTextField("Цена постройки", buildCostText, Icons.Default.AccountBalance) { buildCostText = it }
                        Spacer(Modifier.height(8.dp))
                        StyledTextField("Цена перевозки", transportCostText, Icons.Default.LocalShipping) { transportCostText = it }

                        Spacer(Modifier.height(24.dp))

                        // КНОПКИ ДЕЙСТВИЙ
                        Button(
                            onClick = { runClustering() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isBusy && points.isNotEmpty(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Рассчитать")
                        }

                        Spacer(Modifier.height(8.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { loadFile() },
                                modifier = Modifier.weight(1f),
                                enabled = !isBusy,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.FileUpload, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Файл")
                            }
                            OutlinedButton(
                                onClick = { saveResults() },
                                modifier = Modifier.weight(1f),
                                enabled = !isBusy && centroids.isNotEmpty(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Экспорт")
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // Статус-бар
                        StatusInfo(status, isBusy)
                    }
                }

                // КАНВАС
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight().padding(16.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF0A0A0A))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                ) {
                    if (points.isNotEmpty()) {
                        PointsCanvas(points, centroids)
                    } else {
                        EmptyStatePlaceholder()
                    }
                }
            }
        }
    }
}

@Composable
fun ExecutionModeOption(
    label: String,
    isSelected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val background = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
    val textColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = textColor
        )
    }
}

@Composable
fun StatusInfo(status: String, isBusy: Boolean) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (isBusy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(if (isBusy) 8.dp else 0.dp))
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun StatisticsBlock(pointCount: Int, clusterCount: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Text("Статистика данных", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            StatItem("Точек", pointCount.toString(), Icons.Default.Grain)
            StatItem("Кластеров", if (clusterCount > 0) clusterCount.toString() else "—", Icons.Default.Hub)
        }
    }
}

@Composable
fun StatItem(label: String, value: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(4.dp))
        Column {
            Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(label, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }
}

@Composable
fun EmptyStatePlaceholder() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Analytics,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = Color.DarkGray
        )
        Spacer(Modifier.height(16.dp))
        Text("Загрузите CSV файл для начала работы", color = Color.Gray)
    }
}

@Composable
fun StyledTextField(label: String, value: String, icon: ImageVector, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = { Icon(icon, null, modifier = Modifier.size(18.dp)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(8.dp),
        textStyle = MaterialTheme.typography.bodyMedium
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PointsCanvas(points: List<Point>, centroids: List<Centroid>) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }.toInt().coerceAtLeast(1)
        val heightPx = with(density) { maxHeight.toPx() }.toInt().coerceAtLeast(1)

        var zoom by remember { mutableStateOf(1f) }
        var pan by remember { mutableStateOf(Offset.Zero) }
        var bitmapState by remember { mutableStateOf<ImageBitmap?>(null) }
        var isGenerating by remember { mutableStateOf(false) }

        LaunchedEffect(points, centroids, widthPx, heightPx, zoom, pan) {
            isGenerating = true
            val generatedBitmap = withContext(Dispatchers.Default) {
                generateClusterImage(points, centroids, widthPx, heightPx, zoom, pan)
            }
            bitmapState = generatedBitmap
            isGenerating = false
        }

        val inputModifier = Modifier
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    pan += dragAmount
                }
            }
            .onPointerEvent(PointerEventType.Scroll) {
                val change = it.changes.firstOrNull() ?: return@onPointerEvent
                val zoomFactor = if (change.scrollDelta.y > 0) 0.9f else 1.1f
                zoom = (zoom * zoomFactor).coerceIn(0.1f, 500f)
                pan += (change.position - pan) * (1 - zoomFactor)
            }

        Box(modifier = Modifier.fillMaxSize().then(inputModifier)) {
            bitmapState?.let { bmp ->
                Image(
                    bitmap = bmp,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.None,
                    filterQuality = FilterQuality.None
                )
            }


            Column(modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
                Surface(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "Zoom: ${"%.2f".format(zoom)}x",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = Color.White,
                        fontSize = 12.sp
                    )
                }
                if (isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(top = 8.dp).size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            FilledTonalButton(
                onClick = { zoom = 1f; pan = Offset.Zero },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Сброс вида")
            }
        }
    }
}

fun generateClusterImage(
    points: List<Point>,
    centroids: List<Centroid>,
    width: Int,
    height: Int,
    zoom: Float,
    pan: Offset
): ImageBitmap {
    val imageBitmap = ImageBitmap(width, height)
    val canvas = Canvas(imageBitmap)

    val colors = listOf(
        Color(0xFF00FFCC), Color(0xFFFF3366), Color(0xFF3399FF),
        Color(0xFFCCFF00), Color(0xFFFF9900), Color(0xFF9933FF),
        Color(0xFF00FF00), Color(0xFFFF00FF)
    )

    val pointPaint = Paint().apply { isAntiAlias = false }
    val centroidPaint = Paint().apply {
        color = Color.White
        isAntiAlias = true
    }
    val borderPaint = Paint().apply {
        color = Color.Black
        style = PaintingStyle.Stroke
        strokeWidth = 1.5f
    }

    val basePadding = 40f
    val dataDrawWidth = width - 2 * basePadding
    val dataDrawHeight = height - 2 * basePadding

    fun mapX(x: Double) = (basePadding + x * dataDrawWidth) * zoom + pan.x
    fun mapY(y: Double) = (basePadding + y * dataDrawHeight) * zoom + pan.y

    points.forEach { p ->
        if (p.dimension >= 2) {
            val px = mapX(p.coordinates[0]).toFloat()
            val py = mapY(p.coordinates[1]).toFloat()
            if (px in -10f..(width + 10f) && py in -10f..(height + 10f)) {
                pointPaint.color = if (p.clusterId >= 0) colors[p.clusterId % colors.size] else Color.Gray
                canvas.drawCircle(Offset(px, py), 3f * zoom.coerceIn(0.5f, 2f), pointPaint)
            }
        }
    }

    centroids.forEach { c ->
        val cx = mapX(c.coordinates[0]).toFloat()
        val cy = mapY(c.coordinates[1]).toFloat()
        if (cx in -20f..(width + 20f) && cy in -20f..(height + 20f)) {
            val r = 7f * zoom.coerceIn(0.8f, 1.5f)
            canvas.drawCircle(Offset(cx, cy), r, centroidPaint)
            canvas.drawCircle(Offset(cx, cy), r, borderPaint)
        }
    }

    return imageBitmap
}

fun restoreCentroidsFromPoints(points: List<Point>): List<Centroid> {
    val clusters = points.filter { it.clusterId != -1 }.groupBy { it.clusterId }

    return clusters.map { (id, clusterPoints) ->
        val dimension = clusterPoints.first().dimension
        val coords = DoubleArray(dimension)
        for (point in clusterPoints) {
            for (i in 0 until dimension) {
                coords[i] += point.coordinates[i]
            }
        }

        for (i in 0 until dimension) {
            coords[i] /= clusterPoints.size.toDouble()
        }
        Centroid(coords, id)
    }
}