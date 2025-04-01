package it.unibo.formalization

import it.unibo.alchemist.model.positions.Euclidean2DPosition

data class RobotAllocationResult(
    val robot: Pair<Double, Double>,
    val route: List<Pair<Double, Double>>, // e.g., [startDepot, task1, task3, endDepot]
    val routeCost: Double
)