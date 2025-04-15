package it.unibo.alchemist.model.global

import it.unibo.alchemist.model.Environment
import it.unibo.alchemist.model.Node
import it.unibo.alchemist.model.Position
import it.unibo.alchemist.model.TimeDistribution
import it.unibo.alchemist.model.molecules.SimpleMolecule
import it.unibo.formalization.GreedyAllocationStrategy
import it.unibo.formalization.RobotAllocationResult
import it.unibo.formalization.Node as NodeFormalization

fun <T, P : Position<P>> Node<T>.toNodeFormalization(environment: Environment<T, P>): NodeFormalization {
    val position: Pair<Double, Double> = environment.getPosition(this).let { it.coordinates[0] to it.coordinates[1] }
    return NodeFormalization(position, id)
}
class AllocationBasedOnFormalization<T, P : Position<P>>(
    environment: Environment<T, P>,
    distribution: TimeDistribution<T>
): InitialAllocationStrategy<T, P>(environment, distribution) {
    var cache: List<NodeFormalization> = emptyList()
    var allocationCache: List<RobotAllocationResult> = emptyList()
    override fun allocate(
        robots: List<Node<T>>,
        tasks: List<Node<T>>,
        sourceDepot: Node<T>,
        targetDepot: Node<T>
    ): List<Allocation<T>> {
        val robotsPosition = robots
            .filter { it.contents[SimpleMolecule("down")] == false }
            .map { it.toNodeFormalization(environment)}
        val tasksPosition = tasks.map { it.toNodeFormalization(environment)}
        val tasksDone = environment.nodes.filter { it.contents[SimpleMolecule("isDone")] == 1.0 }.map { it.id }.toSet()

        val sourceDepotPosition = sourceDepot.toNodeFormalization(environment)
        val targetDepotPosition = targetDepot.toNodeFormalization(environment)
        val allocator = GreedyAllocationStrategy(
            robotsPosition,
            tasksPosition.filter { it.id !in tasksDone },
            targetDepotPosition,
        )
        if (cache.size != robotsPosition.size) {
            println("wewe")
            cache = robotsPosition
            val (_, result) = allocator.execute()
            allocationCache = result
            return allocationCache.mapIndexed { index, allocation ->
                val robot: Node<T> = robots[index]
                val tasks: List<Node<T>> = allocation.route.toList().drop(1).map { environment.getNodeByID(it.id) }
                Allocation(robot, tasks)
            }
        } else {
            return listOf()
        }

    }
}