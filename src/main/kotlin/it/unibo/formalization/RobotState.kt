package it.unibo.formalization

import it.unibo.alchemist.model.positions.Euclidean2DPosition

internal data class RobotAgentState(
    val robot: Node,
    val assignedTasks: MutableSet<Node> = mutableSetOf(),
    var route: MutableList<Node>, // Initialized with [start, end]
    var routeCost: Double                     // Initialized with cost(start, end)
)
