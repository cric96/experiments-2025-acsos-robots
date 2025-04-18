package it.unibo.alchemist.model.actions

import it.unibo.alchemist.model.Action
import it.unibo.alchemist.model.Context
import it.unibo.alchemist.model.Environment
import it.unibo.alchemist.model.Node
import it.unibo.alchemist.model.Position2D
import it.unibo.alchemist.model.Reaction
import it.unibo.alchemist.model.Time
import it.unibo.alchemist.model.linkingrules.ConnectWithinDistance
import it.unibo.alchemist.model.molecules.SimpleMolecule
import it.unibo.alchemist.model.times.DoubleTime

class LookupTasksToExecute<P : Position2D<P>>(
    private val environment: Environment<Any, P>,
    private val node: Node<Any>,
) : AbstractAction<Any>(node) {
    private val amountToSolve = 60.0
    private var startTime: Time? = null

    override fun cloneAction(
        p0: Node<Any>,
        p1: Reaction<Any>,
    ): Action<Any> = LookupTasksToExecute(environment, node)

    private val lookup = ConnectWithinDistance<Any, P>(0.01)

    override fun execute() {
        val nearestAgent =
            lookup
                .computeNeighborhood(node, environment)
                .neighbors
                .filter { it.contents[SimpleMolecule("selected")] == node.id }
                .minByOrNull { environment.getPosition(it).distanceTo(environment.getPosition(node)) }
        val distance =
            nearestAgent?.let { environment.getPosition(it).distanceTo(environment.getPosition(node)) } ?: Double.POSITIVE_INFINITY

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
