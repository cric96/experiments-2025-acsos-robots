package it.unibo.alchemist.model.global

import it.unibo.alchemist.model.Environment
import it.unibo.alchemist.model.Node
import it.unibo.alchemist.model.Position
import it.unibo.alchemist.model.TimeDistribution

/**
 * A strategy for reallocating tasks to robots at the end of their routes.
 * This strategy is used when all robots are at the target depot or when no allocation is given.
 */
class ReallocatedAtTheEnd<T, P : Position<P>>(
    environment: Environment<T, P>,
    distribution: TimeDistribution<T>,
) : CacheBasedAllocationStrategy<T, P>(environment, distribution) {
    override fun allocate(
        robots: List<Node<T>>,
        tasks: List<Node<T>>,
        sourceDepot: Node<T>,
        targetDepot: Node<T>,
    ): List<Allocation<T>> {
        val robotsPosition =
            robots.map { it.toNodeFormalization(environment) }
        // get if every node are near to the target depot or no allocation is given
        val areAllAtTheEnd =
            robotsPosition.all {
                environment.getDistanceBetweenNodes(environment.getNodeByID(it.id), targetDepot) < RADIUS
            }
        return updateCacheOrEmpty(
            robots,
            computeAllocator(tasks, robots, targetDepot),
        ) { areAllAtTheEnd || allocationCache.isEmpty() }
    }

    private companion object {
        private const val RADIUS = 0.01
    }
}
