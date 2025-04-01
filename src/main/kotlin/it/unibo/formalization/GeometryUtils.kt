package it.unibo.formalization

import kotlin.math.pow

object GeometryUtils {
    /** Calculates Euclidean distance between two nodes. */
    fun travelCost(start: Pair<Double, Double>, end: Pair<Double, Double>): Double {
        // Ensure the underlying Euclidean2DPosition has distanceTo or implement here
        return Math.sqrt((end.first - start.first).pow(2) + (end.second - start.second).pow(2))
    }

    /** Calculates the total cost of traversing a given route (sequence of nodes). */
    fun calculateRouteCost(route: List<Pair<Double, Double>>): Double {
        return route.windowed(size = 2, step = 1)
            .sumOf { (a, b) -> travelCost(a, b) }
    }
}