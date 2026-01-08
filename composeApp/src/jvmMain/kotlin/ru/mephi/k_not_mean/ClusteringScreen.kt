package ru.mephi.k_not_mean

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.mephi.k_not_mean.core.*
import ru.mephi.k_not_mean.windows.Platform

// Импорты UI (Compose Runtime и Layout)
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

// Импорты ГРАФИКИ (Важно для генерации ImageBitmap)
// Обратите внимание: мы НЕ импортируем "java.awt.*"
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas     // Интерфейс Канваса
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap // Аналог BufferedImage
import androidx.compose.ui.graphics.Paint       // Кисть для рисования
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.sp

// Импорты для многопоточности
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

// Ваши модели
import ru.mephi.k_not_mean.core.Point
import ru.mephi.k_not_mean.core.Centroid

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

        Text(
            "K-Means с автоматическим подбором K",
            style = MaterialTheme.typography.titleMedium
        )

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
            modifier = Modifier
                .fillMaxSize()
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

@OptIn(ExperimentalComposeUiApi::class, ExperimentalComposeUiApi::class)
@Composable
fun PointsCanvas(
    points: List<Point>,
    centroids: List<Centroid>
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
    ) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }.toInt().coerceAtLeast(1)
        val heightPx = with(density) { maxHeight.toPx() }.toInt().coerceAtLeast(1)

        // === СОСТОЯНИЕ ТРАНСФОРМАЦИИ ===
        var zoom by remember { mutableStateOf(1f) }
        var pan by remember { mutableStateOf(Offset.Zero) }

        // Состояние картинки
        var bitmapState by remember { mutableStateOf<ImageBitmap?>(null) }
        var isGenerating by remember { mutableStateOf(false) }

        // Сброс при загрузке новых данных (опционально, можно убрать, если хотите сохранять позицию)
        LaunchedEffect(points) {
            zoom = 1f
            pan = Offset.Zero
        }

        // === ОБРАБОТЧИКИ МЫШИ (Desktop) ===
        val inputModifier = Modifier
            .pointerInput(Unit) {
                // Обработка перетаскивания (Pan)
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    pan += dragAmount
                }
            }
            .onPointerEvent(PointerEventType.Scroll) {
                // Обработка колесика (Zoom)
                val change = it.changes.firstOrNull() ?: return@onPointerEvent
                val scrollDelta = change.scrollDelta.y

                // Коэффициент зума (0.9 отдалить, 1.1 приблизить)
                val zoomFactor = if (scrollDelta > 0) 0.9f else 1.1f

                // Ограничиваем зум, чтобы не уйти в отрицательные значения
                val newZoom = (zoom * zoomFactor).coerceIn(0.1f, 500f)

                // *Продвинутая логика (Zoom to cursor)*
                // Чтобы зумить к курсору, нужно сместить Pan.
                // Формула: pan = pan + (cursor - pan) * (1 - factor)
                val cursorPosition = change.position
                val newPan = pan + (cursorPosition - pan) * (1 - zoomFactor)

                zoom = newZoom
                pan = newPan
            }

        // === ГЕНЕРАЦИЯ КАРТИНКИ ===
        LaunchedEffect(points, centroids, widthPx, heightPx, zoom, pan) {
            isGenerating = true
            val generatedBitmap = withContext(Dispatchers.Default) {
                generateClusterImage(
                    points = points,
                    centroids = centroids,
                    width = widthPx,
                    height = heightPx,
                    zoom = zoom,
                    pan = pan
                )
            }
            bitmapState = generatedBitmap
            isGenerating = false
        }

        // === ОТРИСОВКА ===
        Box(modifier = Modifier.fillMaxSize().then(inputModifier)) {
            bitmapState?.let { bmp ->
                Image(
                    bitmap = bmp,
                    contentDescription = "Visualization",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.None, // Важно: None, мы сами управляем координатами
                    filterQuality = FilterQuality.None // Для четкости пикселей
                )
            }

            // Инфо-панелька
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                Text(
                    text = "Zoom: ${"%.2f".format(zoom)}x",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                if (isGenerating) {
                    Text("Rendering...", fontSize = 10.sp, color = Color.Red)
                }
            }

            // Кнопка сброса вида
            Button(
                onClick = {
                    zoom = 1f
                    pan = Offset.Zero
                },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.LightGray)
            ) {
                Text("Reset View", color = Color.Black)
            }
        }
    }
}

/* ================= ГЕНЕРАЦИЯ (ФОНОВЫЙ ПОТОК) ================= */

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

    val pointPaint = Paint().apply {
        isAntiAlias = false
        style = PaintingStyle.Fill
    }

    val centroidPaint = Paint().apply {
        color = Color.Black
        isAntiAlias = true
        style = PaintingStyle.Fill
    }

    val borderPaint = Paint().apply {
        color = Color.White
        style = PaintingStyle.Stroke
        strokeWidth = 2f
    }

    val colors = listOf(
        Color.Red, Color.Blue, Color.Green,
        Color.Magenta, Color.Cyan, Color(0xFFFFA500) // Orange
    )

    // Исходные отступы данных (не зависят от зума)
    val basePadding = 24f
    val dataDrawWidth = width - 2 * basePadding
    val dataDrawHeight = height - 2 * basePadding

    // == ГЛАВНАЯ МАГИЯ КООРДИНАТ ==
    // 1. Нормализуем данные (0..1) -> (padding..width-padding)
    // 2. Умножаем на Zoom
    // 3. Добавляем Pan (смещение)

    fun mapX(x: Double): Float {
        val screenX = basePadding + x * dataDrawWidth
        return (screenX * zoom + pan.x).toFloat()
    }

    fun mapY(y: Double): Float {
        val screenY = basePadding + y * dataDrawHeight
        return (screenY * zoom + pan.y).toFloat()
    }

    // Простейшая проверка видимости (Culling), чтобы не рисовать то, что за экраном
    // Это ускоряет отрисовку при сильном приближении
    fun isVisible(x: Float, y: Float): Boolean {
        return x >= -10 && y >= -10 && x <= width + 10 && y <= height + 10
    }

    // Рисуем точки
    // Радиус точки НЕ умножаем на Zoom -> точки остаются маленькими, расстояние растет
    val pointRadius = 4f

    for (i in points.indices) {
        val p = points[i]
        if (p.dimension >= 2 && p.clusterId >= 0) {
            val px = mapX(p.coordinates[0])
            val py = mapY(p.coordinates[1])

            if (isVisible(px, py)) {
                pointPaint.color = colors[p.clusterId % colors.size]
                canvas.drawCircle(Offset(px, py), pointRadius, pointPaint)
            }
        }
    }

    // Рисуем центроиды
    val centroidRadius = 8f
    for (i in centroids.indices) {
        val c = centroids[i]
        val cx = mapX(c.coordinates[0])
        val cy = mapY(c.coordinates[1])

        if (isVisible(cx, cy)) {
            // Рисуем черную точку с белой обводкой для контраста
            canvas.drawCircle(Offset(cx, cy), centroidRadius, centroidPaint)
            canvas.drawCircle(Offset(cx, cy), centroidRadius, borderPaint)
        }
    }

    return imageBitmap
}