package it.unibo.alchemist.model.sensors

import it.unibo.alchemist.model.Environment
import it.unibo.alchemist.model.Node
import it.unibo.alchemist.model.NodeProperty
import it.unibo.alchemist.model.Position
import it.unibo.alchemist.model.global.toFormalizationNode
import it.unibo.alchemist.model.linkingrules.ConnectWithinDistance
import it.unibo.alchemist.model.molecules.SimpleMolecule
import it.unibo.formalization.Node as NodeFormalization

class DepotsSensorProperty<T : Any, P : Position<P>>(
    private val environment: Environment<T, P>,
    override val node: Node<T>,
) : DepotsSensor,
    NodeProperty<T> {
    private val lookup = ConnectWithinDistance<T, P>(0.01)
    override val sourceDepot: NodeFormalization
        get() = environment.nodes.first { it.contents[SimpleMolecule(SOURCE_DEPOT_MOLECULE)] == true }.let {
            it.toFormalizationNode(environment)
        }

    override val destinationDepot: NodeFormalization
        get() = environment.nodes.first { it.contents[SimpleMolecule(DESTINATION_DEPOT_MOLECULE)] == true }.let {
            it.toFormalizationNode(environment)
        }

    override val tasks: List<NodeFormalization>
        get() = environment.nodes.filter { it.contents[SimpleMolecule(TASK_MOLECULE)] == true }
            .map { it.toFormalizationNode(environment) }

    override fun toNodePath(node: NodeFormalization): Node<*> = environment.getNodeByID(node.id)!!

    override fun isAgent(): Boolean =
        node.contents[SimpleMolecule("agent")] as Boolean

    override fun alive(): Boolean =
        !(node.contents[SimpleMolecule("down")] as Boolean)


    override fun isTaskOver(task: NodeFormalization): Boolean {
        val task = environment.getNodeByID(task.id)
        // get my neighborhood
        val myNeighbours = lookup.computeNeighborhood(node,environment)
        // search if the task is in my neighborhood
        val isInMyNeighborhood = myNeighbours.any { it.id == task.id }
        // check if the task is done
        val isDone = if (task.contains(SimpleMolecule(DESTINATION_DEPOT_MOLECULE))) {
            false
        } else {
            task.contents[SimpleMolecule("isDone")] as Double == 1.0
        }
        return isInMyNeighborhood && isDone
    }
    companion object {
        const val SOURCE_DEPOT_MOLECULE = "source"
        const val DESTINATION_DEPOT_MOLECULE = "destination"
        const val TASK_MOLECULE = "task"
    }

    override fun cloneOnNewNode(node: Node<T>): NodeProperty<T> = DepotsSensorProperty(environment, node)
}