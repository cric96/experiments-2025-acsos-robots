package it.unibo.formalization

import it.unibo.formalization.GeometryUtils.travelCost
import kotlin.math.min

object RoutingHeuristics {
    /**
     * Computes the marginal cost of inserting a task into a robot's current route.
     * Uses a simple insertion heuristic that tries all possible insertion positions.
     */
    fun computeMarginalCost(route: List<Node>, task: Node): Double {
        if (route.size < 2) {
            throw IllegalArgumentException("Route must contain at least start and end nodes (size >= 2) to calculate insertion cost.")
        }

        var minDeltaCost = Double.POSITIVE_INFINITY

        for (i in 1 until route.size) {
            val prevNode = route[i - 1]
            val nextNode = route[i]
            val deltaCost = travelCost(prevNode, task) + travelCost(task, nextNode) - travelCost(prevNode, nextNode)
            minDeltaCost = min(minDeltaCost, deltaCost)
        }

        // minDeltaCost now holds the cost increase for the best insertion spot
        return minDeltaCost
    }

    /**
     * Solves the local routing problem for a robot with its assigned tasks.
     * Uses a simple greedy insertion heuristic.
     */
    fun solveLocalRouting(
        tasks: Collection<Node>,
        startDepot: Node,
        endDepot: Node
    ): List<Node> {
        if (tasks.isEmpty()) return listOf(startDepot, endDepot)

        val route = mutableListOf(startDepot, endDepot)
        val remainingTasks = tasks.toMutableList()

        while (remainingTasks.isNotEmpty()) {
            val (bestTask, bestPosition) = remainingTasks
                .asSequence()
                .flatMap { task ->
                    (1 until route.size).map { pos ->
                        val prev = route[pos - 1]
                        val next = route[pos]
                        val cost = travelCost(prev, task) + travelCost(task, next) - travelCost(prev, next)
                        Triple(task, pos, cost)
                    }
                }
                .minBy { it.third }
                .let { (task, pos, _) -> task to pos }
            route.add(bestPosition, bestTask)
            remainingTasks.remove(bestTask)
        }
        return route
    }
}
