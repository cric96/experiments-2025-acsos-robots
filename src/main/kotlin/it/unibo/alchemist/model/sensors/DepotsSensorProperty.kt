package it.unibo.alchemist.model.sensors

import it.unibo.alchemist.model.Environment
import it.unibo.alchemist.model.Node
import it.unibo.alchemist.model.NodeProperty
import it.unibo.alchemist.model.Position
import it.unibo.alchemist.model.molecules.SimpleMolecule

class DepotsSensorProperty<T : Any, P : Position<P>>(
    private val environment: Environment<T, P>,
    override val node: Node<T>,
) : DepotsSensor,
    NodeProperty<T> {
    private val positionToTaskMap: Map<Pair<Double, Double>, Node<T>> by lazy {
        val tasks = environment.nodes.filter { it.contents[SimpleMolecule(TASK_MOLECULE)] == true }
        val sourceDepot = environment.nodes.first { it.contents[SimpleMolecule(SOURCE_DEPOT_MOLECULE)] == true }
        val destinationDepot = environment.nodes.first { it.contents[SimpleMolecule(DESTINATION_DEPOT_MOLECULE)] == true }
        tasks.map {
            val position = environment.getPosition(it).coordinates
            Pair(position[0], position[1]) to it
        }.toMap() + mapOf(
            Pair(environment.getPosition(sourceDepot).coordinates[0], environment.getPosition(sourceDepot).coordinates[1]) to sourceDepot,
            Pair(environment.getPosition(destinationDepot).coordinates[0], environment.getPosition(destinationDepot).coordinates[1]) to destinationDepot
        )
    }
    override val getSourceDepot: Pair<Double, Double>
        get() = environment.nodes.first { it.contents[SimpleMolecule(SOURCE_DEPOT_MOLECULE)] == true }.let {
            val position = environment.getPosition(it).coordinates
            Pair(position[0], position[1])
        }

    override val getDestinationDepot: Pair<Double, Double>
        get() = environment.nodes.first { it.contents[SimpleMolecule(DESTINATION_DEPOT_MOLECULE)] == true }.let {
            val position = environment.getPosition(it).coordinates
            Pair(position[0], position[1])
        }

    override val tasks: List<Pair<Double, Double>>
        get() = positionToTaskMap.keys.toList()

    override fun taskNode(position: Pair<Double, Double>): Node<*> = positionToTaskMap[position]!!

    override fun isTask(): Boolean = positionToTaskMap.containsKey(Pair(environment.getPosition(node).coordinates[0], environment.getPosition(node).coordinates[1]))
    companion object {
        const val SOURCE_DEPOT_MOLECULE = "source"
        const val DESTINATION_DEPOT_MOLECULE = "destination"
        const val TASK_MOLECULE = "task"
    }

    override fun cloneOnNewNode(node: Node<T>): NodeProperty<T> = DepotsSensorProperty(environment, node)
}