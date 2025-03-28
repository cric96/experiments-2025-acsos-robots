package it.unibo.alchemist.model.actions

import it.unibo.alchemist.model.*
import it.unibo.alchemist.model.environments.Environment2DWithObstacles
import it.unibo.alchemist.model.molecules.SimpleMolecule
import it.unibo.alchemist.model.positions.Euclidean2DPosition

class LookupTasksToExecute<W : Obstacle2D<Euclidean2DPosition>>(
    private val environment: Environment2DWithObstacles<W, Any>,
    private val node: Node<Any>
): AbstractAction<Any>(node) {
    override fun cloneAction(p0: Node<Any>, p1: Reaction<Any>): Action<Any> =
        LookupTasksToExecute(environment, node)

    override fun execute() {
        environment.getNeighborhood(node).neighbors.forEach {
            val isDone = it.contents[SimpleMolecule("isDone")]
            if (isDone != null && isDone == 0.0 && environment.getPosition(it).distanceTo(environment.getPosition(node)) < 0.05) {
                it.setConcentration(SimpleMolecule("isDone"), 1.0)
            }
        }
    }

    override fun getContext(): Context = Context.NEIGHBORHOOD
}