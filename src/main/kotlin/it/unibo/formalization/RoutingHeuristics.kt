package it.unibo.formalization

import it.unibo.formalization.GeometryUtils.travelCost

object RoutingHeuristics {
    /**
     * Computes the marginal cost of inserting a task into a robot's current route.
     * Uses a simple insertion heuristic that tries all possible insertion positions.
     */
    fun computeMarginalCost(route: List<Node>, task: Node): Double {
        if (route.size < 2) {
            throw IllegalArgumentException("Route must contain at least start and end depots")
        }
        // compute the cost of the route before inserting the task
        val initialCost = GeometryUtils.calculateRouteCost(route)
        // best insert the task
        val newPath = solveLocalRouting(route.toSet() + setOf(task), route.first(), route.last())
        val newCost = GeometryUtils.calculateRouteCost(newPath)
        return newCost - initialCost
    }

    /**
     * Solves the local routing problem for a robot with its assigned tasks.
     * Uses a simple greedy insertion heuristic.
     */
    fun solveLocalRouting(
        tasks: Set<Node>,
        startDepot: Node,
        endDepot: Node
    ): List<Node> {
        if (tasks.isEmpty()) return listOf(startDepot, endDepot)

        val route = mutableListOf(startDepot, endDepot)
        val remainingTasks = tasks.toMutableSet()

        while (remainingTasks.isNotEmpty()) {
            val (bestTask, bestPosition) = remainingTasks.flatMap { task ->
                (1 until route.size).map { pos ->
                    val prev = route[pos - 1]
                    val next = route[pos]
                    val cost = travelCost(prev, task) + travelCost(task, next) - travelCost(prev, next)
                    Triple(task, pos, cost)
                }
            }.minBy { it.third }.let { (task, pos, _) -> task to pos }
            route.add(bestPosition, bestTask)
            remainingTasks.remove(bestTask)
        }

        return route
    }
}
