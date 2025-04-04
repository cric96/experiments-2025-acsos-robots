package it.unibo.formalization

import it.unibo.alchemist.model.positions.Euclidean2DPosition

data class RobotAllocationResult(
    val robot: Node,
    val route: List<Node>, // e.g., [startDepot, task1, task3, endDepot]
    val routeCost: Double
)