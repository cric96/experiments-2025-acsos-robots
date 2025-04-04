package it.unibo.formalization

object GeometryUtils {
    /** Calculates Euclidean distance between two nodes. */
    fun travelCost(start: Node, end: Node): Double {
        // Ensure the underlying Euclidean2DPosition has distanceTo or implement here
        return start.distance(end)
    }

    /** Calculates the total cost of traversing a given route (sequence of nodes). */
    fun calculateRouteCost(route: List<Node>): Double {
        return route.windowed(size = 2, step = 1)
            .sumOf { (a, b) -> travelCost(a, b) }
    }
}