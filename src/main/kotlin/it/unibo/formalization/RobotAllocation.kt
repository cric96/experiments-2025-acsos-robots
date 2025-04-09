package it.unibo.formalization

data class RobotAllocationResult(
    val robot: Node,
    val route: List<Node>, // e.g., [startDepot, task1, task3, endDepot]
    val routeCost: Double
)