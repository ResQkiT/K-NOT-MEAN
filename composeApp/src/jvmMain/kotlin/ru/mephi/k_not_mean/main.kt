package ru.mephi.k_not_mean

import ClusteringScreen
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import ru.mephi.k_not_mean.core.Point2D
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Cluster App",
    ) {
        MaterialTheme {
            // Запускаем наш экран и передаем ему функцию-реализацию загрузки
            ClusteringScreen(
                fileLoader = { openFileDialogAndParse() }
            )
        }
    }
}

// Функция для открытия окна выбора файла (Только для Desktop)
fun openFileDialogAndParse(): List<Point2D>? {
    // 1. Открываем нативное окно выбора файла
    val dialog = FileDialog(null as Frame?, "Выберите CSV файл", FileDialog.LOAD)
    dialog.isVisible = true

    val fileName = dialog.file
    val directory = dialog.directory

    // Если пользователь нажал "Отмена"
    if (fileName == null || directory == null) return null

    val file = File(directory, fileName)

    // 2. Парсинг файла (Простая логика для примера)
    // Ожидаем формат: x,y,clusterId (через запятую или пробел)
    return try {
        file.readLines()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                // Разбиваем строку на части
                val parts = line.split(",", ";", " ").filter { it.isNotBlank() }
                if (parts.size >= 2) {
                    val x = parts[0].toFloatOrNull() ?: 0f
                    val y = parts[1].toFloatOrNull() ?: 0f
                    val id = if (parts.size > 2) parts[2].toIntOrNull() ?: 0 else 0

                    // Важно: нормализуем данные, если они > 1.0
                    // Здесь для простоты делим на 100, если данные большие,
                    // или оставляем как есть, если это 0..1
                    val normX = if (x > 1.0) x / 100f else x
                    val normY = if (y > 1.0) y / 100f else y

                    Point2D(normX, normY, id)
                } else {
                    null
                }
            }
    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    }
}