package it.unibo.formalization

/**
 * Represents the state of an agent (robot) in a task allocation and route planning system.
 * The state includes the robot's identifier, a set of tasks assigned to it, the current
 * planned route it should take, and the cost associated with that route.
 *
 * @property robot The robot represented as a Node, which identifies the agent and its position.
 * @property assignedTasks A set of tasks currently assigned to the robot. Tasks are
 *                         represented as nodes in the system.
 * @property route The current planned route for the robot. It includes the start and end depots,
 *                 as well as intermediate tasks assigned to the robot.
 * @property routeCost The cost associated with the current route, typically computed as the total
 *                     travel distance for the route.
 */
internal data class RobotAgentState(
    val robot: Node,
    val assignedTasks: MutableSet<Node> = mutableSetOf(),
    var route: MutableList<Node>, // Initialized with [start, end]
    var routeCost: Double, // Initialized with cost(start, end)
)
