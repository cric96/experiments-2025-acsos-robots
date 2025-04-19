package it.unibo.alchemist.model.global

import it.unibo.alchemist.model.Environment
import it.unibo.alchemist.model.Node
import it.unibo.alchemist.model.Position
import it.unibo.alchemist.model.TimeDistribution
import it.unibo.formalization.Node as NodeFormalization

/**
 * A conversion function that converts a [Node] to a [NodeFormalization].
 */
fun <T, P : Position<P>> Node<T>.toNodeFormalization(environment: Environment<T, P>): NodeFormalization {
    val position: Pair<Double, Double> = environment.getPosition(this).let { it.coordinates[0] to it.coordinates[1] }
    return NodeFormalization(position, id)
}

/**
 * A strategy for allocating tasks to robots based on their formalization (id, position).
 */
class AllocationBasedOnFormalization<T, P : Position<P>>(
    environment: Environment<T, P>,
    distribution: TimeDistribution<T>,
) : CacheBasedAllocationStrategy<T, P>(environment, distribution) {
    // This will use a cache based on the robots' positions
    override fun allocate(
        robots: List<Node<T>>,
        tasks: List<Node<T>>,
        sourceDepot: Node<T>,
        targetDepot: Node<T>,
    ): List<Allocation<T>> =
        updateCacheOrEmpty(
            robots,
            computeAllocator(robots, tasks, targetDepot),
        ) { cache.size != robots.size }
}
