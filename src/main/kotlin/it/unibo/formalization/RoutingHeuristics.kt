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

        return route.windowed(size = 2, step = 1)
            .map { (prevNode, nextNode) -> travelCost(prevNode, nextNode) + travelCost(prevNode, task) + travelCost(task, nextNode) }
            .minOrNull() ?: Double.MAX_VALUE
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
        if (tasks.isEmpty()) {
            return listOf(startDepot, endDepot)
        }

        // Start with a route containing only the depots
        val route = mutableListOf(startDepot, endDepot)
        val remainingTasks = tasks.toMutableSet()

        // Greedy insertion: repeatedly insert the task at its best position
        while (remainingTasks.isNotEmpty()) {
            var bestTask: Node? = null
            var bestPosition = -1
            var bestCost = Double.MAX_VALUE

            // Find the best task and position to insert next
            for (task in remainingTasks) {
                for (pos in 1 until route.size) {
                    val prevNode = route[pos - 1]
                    val nextNode = route[pos]

                    val insertionCost = travelCost(prevNode, task) +
                            travelCost(task, nextNode) -
                            travelCost(prevNode, nextNode)

                    if (insertionCost < bestCost) {
                        bestCost = insertionCost
                        bestTask = task
                        bestPosition = pos
                    }
                }
            }

            // Insert the best task at its best position
            bestTask?.let {
                route.add(bestPosition, it)
                remainingTasks.remove(it)
            }
        }

        return route
    }
}
