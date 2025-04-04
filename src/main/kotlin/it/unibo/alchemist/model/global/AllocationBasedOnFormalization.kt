package it.unibo.alchemist.model.global

import it.unibo.alchemist.model.Environment
import it.unibo.alchemist.model.Node
import it.unibo.alchemist.model.Position
import it.unibo.alchemist.model.TimeDistribution
import it.unibo.formalization.GreedyAllocationStrategy
import it.unibo.formalization.Node as FormalizationNode

fun <T, P : Position<P>> Node<T>.toFormalizationNode(environment: Environment<T, P>): FormalizationNode {
    val position: Pair<Double, Double> = environment.getPosition(this).let { it.coordinates[0] to it.coordinates[1] }
    return FormalizationNode(position, id)
}
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
        val robotsPosition = robots.map { it.toFormalizationNode(environment)}
        val tasksPosition = tasks.map { it.toFormalizationNode(environment)}
        val sourceDepotPosition = sourceDepot.toFormalizationNode(environment)
        val targetDepotPosition = targetDepot.toFormalizationNode(environment)
        val allocator = GreedyAllocationStrategy(robotsPosition, tasksPosition, sourceDepotPosition, targetDepotPosition)

        val result = allocator.execute()
        return result.mapIndexed { index, allocation ->
            val robot: Node<T> = robots[index]
            val tasks: List<Node<T>> = allocation.route.drop(1).map { environment.getNodeByID(it.id) }
            Allocation(robot, tasks)
        }
    }
}