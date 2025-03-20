package it.unibo.alchemist.model.global

import it.unibo.alchemist.model.Environment
import it.unibo.alchemist.model.Node
import it.unibo.alchemist.model.Position
import it.unibo.alchemist.model.TimeDistribution
import it.unibo.alchemist.model.implementations.reactions.AbstractGlobalReaction
import it.unibo.alchemist.model.molecules.SimpleMolecule

private const val TASKS_MOLECULE = "tasks"

data class Allocation<T>(val robot: Node<T>, val task: List<Node<T>>)

abstract class InitialAllocationStrategy<T, P: Position<P>>(
    environment: Environment<T, P>,
    timeDistribution: TimeDistribution<T>
): AbstractGlobalReaction<T, P>(environment, timeDistribution) {
    override fun executeBeforeUpdateDistribution() {
        val allocations = allocate(nodes, nodes)
        // todo -- do next
        allocations.forEach { allocation ->
            val node = allocation.robot
            // convert tasks to positions
            val tasks = allocation.task.map { environment.getPosition(it) }
            // put in the corresponding molecule
            node.setConcentration(SimpleMolecule(TASKS_MOLECULE), tasks as T)
        }
    }
    protected abstract fun allocate(robots: List<Node<T>>, tasks: List<Node<T>>): List<Allocation<T>>
}