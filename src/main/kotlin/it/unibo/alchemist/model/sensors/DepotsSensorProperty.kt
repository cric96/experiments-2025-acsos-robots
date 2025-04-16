package it.unibo.alchemist.model.sensors

import it.unibo.alchemist.model.Environment
import it.unibo.alchemist.model.Node
import it.unibo.alchemist.model.NodeProperty
import it.unibo.alchemist.model.Position
import it.unibo.alchemist.model.global.toNodeFormalization
import it.unibo.alchemist.model.linkingrules.ConnectWithinDistance
import it.unibo.alchemist.model.molecules.SimpleMolecule
import org.apache.commons.math3.random.RandomGenerator
import kotlin.math.ln
import it.unibo.formalization.Node as NodeFormalization

class DepotsSensorProperty<T : Any, P : Position<P>>(
    private val environment: Environment<T, P>,
    override val node: Node<T>,
    val random: RandomGenerator
) : DepotsSensor,
    NodeProperty<T>
{
    fun uniformToExponential(lambda: Double): Double {
        require(lambda > 0) { "Lambda (rate parameter) must be greater than 0" }
        val uniformRandom = random.nextDouble()
        return - (1 / lambda) * ln(uniformRandom)
    }

    val deathTime by lazy {
        if (node.contains(SimpleMolecule("deathRate"))) {
            val rate = node.contents[SimpleMolecule("deathRate")] as Double
            val result = uniformToExponential(1.0 / rate)
            node.setConcentration(SimpleMolecule("deathTime"), result as T)
            result
        } else {
            Double.POSITIVE_INFINITY
        }
    }
    private val lookup = ConnectWithinDistance<T, P>(0.01)
    override val sourceDepot: NodeFormalization
        get() = environment.nodes.first { it.contents[SimpleMolecule(SOURCE_DEPOT_MOLECULE)] == true }.let {
            it.toNodeFormalization(environment)
        }

    override val destinationDepot: NodeFormalization
        get() = environment.nodes.first { it.contents[SimpleMolecule(DESTINATION_DEPOT_MOLECULE)] == true }.let {
            it.toNodeFormalization(environment)
        }

    override val tasks: List<NodeFormalization>
        get() = environment.nodes.filter { it.contents[SimpleMolecule(TASK_MOLECULE)] == true }
            .map { it.toNodeFormalization(environment) }

    override fun isAgent(): Boolean =
        node.contents[SimpleMolecule("agent")] as Boolean

    override fun alive(): Boolean {
        if(!iAmAlone() && deathTime != Double.POSITIVE_INFINITY && deathTime < environment.simulation.time.toDouble()) {
            node.setConcentration(SimpleMolecule("down"), true as T)
        }
        return !(node.contents[SimpleMolecule("down")] as Boolean)
    }

    override fun isTaskOver(task: NodeFormalization): Boolean {
        val task = environment.getNodeByID(task.id)
        // get my neighborhood
        val myNeighbours = lookup.computeNeighborhood(node,environment)
        // search if the task is in my neighborhood
        val isInMyNeighborhood = myNeighbours.any { it.id == task.id }
        // check if the task is done
        val isDone = if (task.contains(SimpleMolecule(DESTINATION_DEPOT_MOLECULE)) || task.contains(SimpleMolecule(SOURCE_DEPOT_MOLECULE))) {
            false
        } else {
            task.contents[SimpleMolecule("isDone")] as Double == 1.0
        }
        return isInMyNeighborhood && isDone
    }

    private fun iAmAlone(): Boolean {

        val result = environment.nodes
            .filter { it.contents[SimpleMolecule("down")] == false }
            .filter { it.contents[SimpleMolecule("agent")] == true }
            .filter { it.id != node.id }
            .isEmpty()
        return result
    }

    override fun isReachLastTask(task: NodeFormalization): Boolean {
        // get my neighborhood
        val myNeighbours = lookup.computeNeighborhood(node,environment)
        // search if the task is in my neighborhood
        val isInMyNeighborhood = myNeighbours.any { it.id == task.id }
        // check if the task is done
        return isInMyNeighborhood && task.id == destinationDepot.id
    }

    companion object {
        const val SOURCE_DEPOT_MOLECULE = "source"
        const val DESTINATION_DEPOT_MOLECULE = "destination"
        const val TASK_MOLECULE = "task"
    }

    override fun cloneOnNewNode(node: Node<T>): NodeProperty<T> = DepotsSensorProperty(environment, node, random)
}