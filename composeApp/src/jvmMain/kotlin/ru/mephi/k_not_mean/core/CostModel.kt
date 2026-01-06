package ru.mephi.k_not_mean.core

/**
 * Модель затрат:
 * buildCost — стоимость постройки одного пункта (центроида)
 * transportCostPerUnit — стоимость перевозки за 1 единицу расстояния
 */
data class CostModel(
    val buildCost: Double,
    val transportCostPerUnit: Double
)
