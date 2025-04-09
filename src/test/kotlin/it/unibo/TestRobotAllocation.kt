package it.unibo

import it.unibo.formalization.Node
import it.unibo.formalization.RoutingHeuristics
import kotlin.test.Test
import kotlin.test.assertTrue

class TestRobotAllocation {
    @Test
    fun putATaskBetweenToNodeShouldIncreaseTheMarginalCost() {
        val start = Node(0.0 to 0.0, 1)
        val end = Node(1.0 to 1.0, 2)
        val task = Node(0.9 to 0.9, 3)
        val route = listOf(start, end)
        val marginalCost = RoutingHeuristics.computeMarginalCost(route, task)
        println(marginalCost)
        assertTrue { marginalCost > 0.0 }
    }
}