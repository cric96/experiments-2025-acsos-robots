package it.unibo.alchemist.model.global

import it.unibo.alchemist.model.Environment
import it.unibo.alchemist.model.Node
import it.unibo.alchemist.model.Position
import it.unibo.alchemist.model.TimeDistribution
import it.unibo.alchemist.model.positions.Euclidean2DPosition
import it.unibo.formalization.GreedyAllocationStrategy

class AllocationBasedOnFormalization<T, P : Position<P>>(
    environment: Environment<T, P>,
    distribution: TimeDistribution<T>
): InitialAllocationStrategy<T, P>(environment, distribution) {
    override fun allocate(
        robots: List<Node<T>>,
        tasks: List<Node<T>>,
        sourceDepot: Node<T>,
        targetDepot: Node<T>
    ): List<Allocation<T>> {
        val robotsPosition = robots.map { environment.getPosition(it) }.map { it.coordinates[0] to it.coordinates[1] }
        val tasksPosition = tasks.map { environment.getPosition(it) }.map { it.coordinates[0] to it.coordinates[1] }
        val sourceDepotPosition = environment.getPosition(sourceDepot).let { it.coordinates[0] to it.coordinates[1] }
        val targetDepotPosition = environment.getPosition(targetDepot).let { it.coordinates[0] to it.coordinates[1] }
        val allocator = GreedyAllocationStrategy(robotsPosition, tasksPosition, sourceDepotPosition, targetDepotPosition)
        // divide the tasks among the robots equally
        val allNodesButRobots = tasks + listOf(sourceDepot, targetDepot)
        val positionToNode: Map<Pair<Double, Double>, Node<T>> = allNodesButRobots
            .associateBy { environment.getPosition(it) }
            .mapKeys {  it.key.coordinates[0] to it.key.coordinates[1] }
        val result = allocator.execute()
        return result.mapIndexed { index, allocation ->
            val robot: Node<T> = robots[index]
            val tasks: List<Node<T>> = allocation.route.drop(1).map { positionToNode.getOrDefault(it, robot) }
            Allocation(robot, tasks)
        }
    }
}