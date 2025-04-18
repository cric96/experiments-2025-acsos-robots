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

/**
 * A domain specific sensor used
 * to gather information about the tasks state.
 */
class DepotsSensorProperty<T : Any, P : Position<P>>(
    private val environment: Environment<T, P>,
    override val node: Node<T>,
    private val random: RandomGenerator,
) : DepotsSensor,
    NodeProperty<T> {
    private val lookup = ConnectWithinDistance<T, P>(MINIMUM_RADIUS)

    private fun uniformToExponential(lambda: Double): Double {
        require(lambda > 0) { "Lambda (rate parameter) must be greater than 0" }
        val uniformRandom = random.nextDouble()
        return -(1 / lambda) * ln(uniformRandom)
    }

    private val deathTime by lazy {
        if (node.contains(SimpleMolecule("deathRate"))) {
            val rate = node.contents[SimpleMolecule("deathRate")] as Double
            val result = uniformToExponential(1.0 / rate)
            node.setConcentration(SimpleMolecule("deathTime"), result as T)
            result
        } else {
            Double.POSITIVE_INFINITY
        }
    }
    override val sourceDepot: NodeFormalization
        get() =
            environment.nodes.first { it.contents[SimpleMolecule(SOURCE_DEPOT_MOLECULE)] == true }.let {
                it.toNodeFormalization(environment)
            }

    override val destinationDepot: NodeFormalization
        get() =
            environment.nodes.first { it.contents[SimpleMolecule(DESTINATION_DEPOT_MOLECULE)] == true }.let {
                it.toNodeFormalization(environment)
            }

    override val tasks: List<NodeFormalization>
        get() =
            environment.nodes
                .filter { it.contents[SimpleMolecule(TASK_MOLECULE)] == true }
                .map { it.toNodeFormalization(environment) }

    override fun isAgent(): Boolean = node.contents[SimpleMolecule("agent")] as Boolean

    override fun alive(): Boolean {
        val deathCondition = !iAmAlone() &&
                deathTime != Double.POSITIVE_INFINITY &&
                deathTime < environment.simulation.time.toDouble()
        if (deathCondition) {
            node.setConcentration(SimpleMolecule("down"), true as T)
        }
        return !(node.contents[SimpleMolecule("down")] as Boolean)
    }

    override fun isTaskOver(task: NodeFormalization): Boolean {
        val taskRaw = environment.getNodeByID(task.id)
        // get my neighborhood
        val myNeighbours = lookup.computeNeighborhood(node, environment)
        // search if the task is in my neighborhood
        val isInMyNeighborhood = myNeighbours.any { it.id == taskRaw.id }
        // check if the task is done
        val isDone =
            if (taskRaw.contains(SimpleMolecule(DESTINATION_DEPOT_MOLECULE)) ||
                taskRaw.contains(SimpleMolecule(SOURCE_DEPOT_MOLECULE))
            ) {
                false
            } else {
                taskRaw.contents[SimpleMolecule("isDone")] as Double == 1.0
            }
        return isInMyNeighborhood && isDone
    }

    private fun iAmAlone(): Boolean {
        val result =
            environment.nodes
                .filter { it.contents[SimpleMolecule("down")] == false }
                .filter { it.contents[SimpleMolecule("agent")] == true }
                .filter { it.id != node.id }
                .isEmpty()
        return result
    }

    override fun isReachLastTask(task: NodeFormalization): Boolean {
        // get my neighborhood
        val myNeighbours = lookup.computeNeighborhood(node, environment)
        // search if the task is in my neighborhood
        val isInMyNeighborhood = myNeighbours.any { it.id == task.id }
        // check if the task is done
        return isInMyNeighborhood && task.id == destinationDepot.id
    }

    /**
     * Constant used for the depots sensor.
     */
    companion object {
        /** Source depot molecule name. */
        const val SOURCE_DEPOT_MOLECULE = "source"
        /** Destination depot molecule name. */
        const val DESTINATION_DEPOT_MOLECULE = "destination"
        /** Task molecule name. */
        const val TASK_MOLECULE = "task"
        private const val MINIMUM_RADIUS = 0.01
    }

    override fun cloneOnNewNode(node: Node<T>): NodeProperty<T> = DepotsSensorProperty(environment, node, random)
}
