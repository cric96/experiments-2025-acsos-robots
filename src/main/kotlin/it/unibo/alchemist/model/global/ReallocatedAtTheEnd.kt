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
    private fun areAllAtTheEnd(
        robots: List<Node<T>>,
        targetDepot: Node<T>,
    ): Boolean =
        robots.all {
            environment.getDistanceBetweenNodes(it, targetDepot) < RADIUS
        }

    override fun allocate(
        robots: List<Node<T>>,
        tasks: List<Node<T>>,
        sourceDepot: Node<T>,
        targetDepot: Node<T>,
    ): List<Allocation<T>> =
        updateCacheOrEmpty(
            robots,
            computeAllocator(robots, tasks, targetDepot),
        ) { areAllAtTheEnd(robots, targetDepot) || allocationCache.isEmpty() }

    private companion object {
        private const val RADIUS = 0.01
    }
}
