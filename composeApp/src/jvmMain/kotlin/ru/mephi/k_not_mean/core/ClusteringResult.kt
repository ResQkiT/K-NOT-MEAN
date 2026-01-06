package ru.mephi.k_not_mean.core

/**
 * Результат кластеризации с учётом стоимости
 */
data class ClusteringResult(
    val points: List<Point>,
    val centroids: List<Centroid>,
    val timeMs: Long,
    val totalCost: Double
)
