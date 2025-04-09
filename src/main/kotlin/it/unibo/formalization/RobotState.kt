package it.unibo.formalization

internal data class RobotAgentState(
    val robot: Node,
    val assignedTasks: MutableSet<Node> = mutableSetOf(),
    var route: MutableList<Node>, // Initialized with [start, end]
    var routeCost: Double                     // Initialized with cost(start, end)
)
