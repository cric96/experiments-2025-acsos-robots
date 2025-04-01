package it.unibo.formalization

import it.unibo.alchemist.model.positions.Euclidean2DPosition

data class Bid(
    val task: Pair<Double, Double>,
    val robot: Pair<Double, Double>,
    val cost: Double // Can be marginal cost or adjusted cost
)