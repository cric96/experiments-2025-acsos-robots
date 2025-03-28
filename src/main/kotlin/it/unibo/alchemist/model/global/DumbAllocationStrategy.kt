package it.unibo.alchemist.model.global

import it.unibo.alchemist.model.Environment
import it.unibo.alchemist.model.Node
import it.unibo.alchemist.model.Position
import it.unibo.alchemist.model.TimeDistribution

class DumbAllocationStrategy<T, P : Position<P>>(
    environment: Environment<T, P>,
    distribution: TimeDistribution<T>
): InitialAllocationStrategy<T, P>(environment, distribution) {
    override fun allocate(
        robots: List<Node<T>>,
        tasks: List<Node<T>>,
        sourceDepot: Node<T>,
        targetDepot: Node<T>
    ): List<Allocation<T>> {
        // divide the tasks among the robots equally
        val tasksPerRobot = tasks.size / robots.size
        return robots.mapIndexed { index, robot ->
            val start = index * tasksPerRobot
            val end = (index + 1) * tasksPerRobot
            Allocation(robot, tasks.subList(start, end) + listOf(targetDepot))
        }
    }
}