package it.unibo.formalization

/**
 * Result of the robot allocation process.
 * Contains the robot assigned to a task, the route it will take,
 * and the cost of that route.
 *
 * @property robot The robot assigned to the task.
 * @property route The route the robot will take, including the start depot, tasks, and end depot.
 * @property routeCost The cost of the route, which can be used for optimization or analysis.
 */
data class RobotAllocationResult(
    val robot: Node,
    val route: List<Node>, // e.g., [startDepot, task1, task3, endDepot]
    val routeCost: Double,
)
