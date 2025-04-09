package it.unibo.formalization

import it.unibo.alchemist.model.positions.Euclidean2DPosition

data class Node(
    val position: Pair<Double, Double>,
    val id: Int,
) {
    fun distance(other: Node): Double {
        val dx = position.first - other.position.first
        val dy = position.second - other.position.second
        return Math.sqrt(dx * dx + dy * dy)
    }
}


data class Bid(
    val task: Node,
    val robot: Int,
    val cost: Double // Can be marginal cost or adjusted cost
)