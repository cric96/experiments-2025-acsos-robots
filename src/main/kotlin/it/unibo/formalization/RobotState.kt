package it.unibo.formalization

import it.unibo.alchemist.model.positions.Euclidean2DPosition

internal data class RobotAgentState(
    val robot: Pair<Double, Double>,
    val assignedTasks: MutableSet<Pair<Double, Double>> = mutableSetOf(),
    var route: MutableList<Pair<Double, Double>>, // Initialized with [start, end]
    var routeCost: Double                     // Initialized with cost(start, end)
)
