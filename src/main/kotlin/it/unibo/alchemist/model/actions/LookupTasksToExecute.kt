package it.unibo.alchemist.model.actions

import it.unibo.alchemist.model.*
import it.unibo.alchemist.model.environments.Environment2DWithObstacles
import it.unibo.alchemist.model.molecules.SimpleMolecule
import it.unibo.alchemist.model.positions.Euclidean2DPosition
import it.unibo.alchemist.model.times.DoubleTime

class LookupTasksToExecute<W : Obstacle2D<Euclidean2DPosition>>(
    private val environment: Environment2DWithObstacles<W, Any>,
    private val node: Node<Any>,
): AbstractAction<Any>(node) {
    private val amountToSolve = 300.0
    private var startTime: Time? = null
    override fun cloneAction(p0: Node<Any>, p1: Reaction<Any>): Action<Any> =
        LookupTasksToExecute(environment, node)

    override fun execute() {
            val nearestAgent = environment.getNeighborhood(node)
                .neighbors
                .filter { it.contents[SimpleMolecule("selected")] == node.id }
                .minByOrNull { environment.getPosition(it).distanceTo(environment.getPosition(node)) }

            val distance = nearestAgent?.let { environment.getPosition(it).distanceTo(environment.getPosition(node)) } ?: Double.POSITIVE_INFINITY

            if (startTime == null && distance < 0.005) {
                startTime = environment.simulation.time
            } else if (startTime != null && distance > 0.005) {
                startTime = null
            }

            val isDone = (node.contents[SimpleMolecule("isDone")] as Double) != 0.0
            val timeWaiting = environment.simulation.time - (startTime ?: environment.simulation.time)
            if (!isDone && timeWaiting > DoubleTime(amountToSolve)) {
                node.setConcentration(SimpleMolecule("isDone"), 1.0)
            }
        }

    override fun getContext(): Context = Context.NEIGHBORHOOD
}