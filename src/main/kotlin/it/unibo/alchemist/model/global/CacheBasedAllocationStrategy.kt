package it.unibo.alchemist.model.global

import it.unibo.alchemist.model.Environment
import it.unibo.alchemist.model.Node
import it.unibo.alchemist.model.Position
import it.unibo.alchemist.model.TimeDistribution
import it.unibo.formalization.GreedyAllocationStrategy
import it.unibo.formalization.Node as NodeFormalization
import it.unibo.formalization.RobotAllocationResult

/**
 * A strategy for allocating tasks to robots based on their formalization (id, position).
 * It has a cache to store the last allocation results.
 */
abstract class CacheBasedAllocationStrategy <T, P : Position<P>>(
    environment: Environment<T, P>,
    timeDistribution: TimeDistribution<T>,
) : InitialAllocationStrategy<T, P>(environment, timeDistribution) {
    protected var cache: List<NodeFormalization> = emptyList()
    protected var allocationCache: List<RobotAllocationResult> = emptyList()

    protected fun List<RobotAllocationResult>.toAllocations(
        robots: List<Node<T>>,
    ): List<Allocation<T>> {
        return this.mapIndexed { index, allocation ->
            val robot: Node<T> = robots[index]
            val tasks: List<Node<T>> = allocation.route
                .toList()
                .drop(1)
                .map { environment.getNodeByID(it.id) }
            Allocation(robot, tasks)
        }
    }
    protected fun computeAllocator(
        robots: List<Node<T>>,
        tasks: List<Node<T>>,
        targetDepot: Node<T>,
    ): GreedyAllocationStrategy {
        val robotsPosition =
            robots.map { it.toNodeFormalization(environment) }
        val tasksPosition = tasks.map { it.toNodeFormalization(environment) }
        val targetDepotPosition = targetDepot.toNodeFormalization(environment)
        return GreedyAllocationStrategy(
            robotsPosition,
            tasksPosition,
            targetDepotPosition,
        )
    }

    protected fun updateCacheOrEmpty(
        robots: List<Node<T>>,
        allocator: GreedyAllocationStrategy,
        predicate: () -> Boolean
    ): List<Allocation<T>> {
        if (predicate()) {
            cache = robots.map { it.toNodeFormalization(environment) }
            val (_, result) = allocator.execute()
            allocationCache = result
            return allocationCache.toAllocations(robots)
        } else {
            return listOf()
        }
    }
}
