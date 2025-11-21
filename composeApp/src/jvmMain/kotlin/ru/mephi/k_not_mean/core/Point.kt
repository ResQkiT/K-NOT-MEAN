package ru.mephi.k_not_mean.core

import kotlin.math.sqrt

/**
 * Абстрактная точка в N-мерном пространстве.
 * @property coordinates Массив координат (x1, x2, ..., xn)
 * @property clusterId Идентификатор кластера, к которому принадлежит точка
 */
abstract class Point(
    val coordinates: DoubleArray,
    var clusterId: Int = -1
) {

    val dimension: Int get() = coordinates.size

    /**
     * 1. Евклидово расстояние между двумя точками.
     * Формула: sqrt(sum((a[i] - b[i])^2))
     */
    fun distanceTo(other: Point): Double {
        requireDimension(other)
        var sum = 0.0
        for (i in coordinates.indices) {
            val diff = coordinates[i] - other.coordinates[i]
            sum += diff * diff // или diff.pow(2)
        }
        return sqrt(sum)
    }

    /**
     * 2. Сложение двух точек (векторное сложение).
     * Используется для накопления суммы координат при расчете центроида.
     */
    operator fun plus(other: Point): Point {
        requireDimension(other)
        val newCoords = DoubleArray(dimension) { i ->
            this.coordinates[i] + other.coordinates[i]
        }
        // Возвращаем универсальную реализацию точки
        return NDPoint(newCoords)
    }

    /**
     * 3. Деление на число (скаляр).
     * Используется для вычисления среднего значения (Center = Sum / Count).
     */
    operator fun div(scalar: Double): Point {
        if (scalar == 0.0) throw ArithmeticException("Division by zero")
        val newCoords = DoubleArray(dimension) { i ->
            this.coordinates[i] / scalar
        }
        return NDPoint(newCoords)
    }

    protected fun requireDimension(other: Point) {
        if (this.dimension != other.dimension) {
            throw IllegalArgumentException(
                "Points must have same dimension. Left: $dimension, Right: ${other.dimension}"
            )
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Point) return false
        if (dimension != other.dimension) return false
        return coordinates.contentEquals(other.coordinates)
    }

    override fun hashCode(): Int {
        return coordinates.contentHashCode()
    }

    override fun toString(): String {
        return "Point(dim=$dimension, coords=${coordinates.joinToString(", ")}, cluster=$clusterId)"
    }
}