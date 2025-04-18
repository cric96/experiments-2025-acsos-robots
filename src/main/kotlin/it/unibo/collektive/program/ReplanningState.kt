package it.unibo.collektive.program

import it.unibo.alchemist.model.sensors.DepotsSensor
import it.unibo.formalization.Node
import it.unibo.formalization.RobotAllocationResult

/**
 * A data class that represents the state of the replanning process.
 * It contains the following properties:
 * - `dones`: a map that associates each task to a boolean value indicating whether it is done or not.
 * - `path`: a list of nodes representing the path to follow.
 * - `allocations`: a list of robot allocation results.
 */
data class ReplanningState(
    /** the task dones perceived in the system. */
    val dones: Map<Node, Boolean>,
    /** the path to follow. */
    val path: List<Node>,
    /** the allocations of the robots. */
    val allocations: List<RobotAllocationResult> = emptyList(),
) {
    /**
     * Utility used to create the replanning state.
     */
    companion object {
        /**
         * Creates a new instance of [ReplanningState] with the given tasks and depots sensor.
         * @param tasks a list of tasks to be done.
         * @param depotsSensor a [DepotsSensor] object that provides information about the depots.
         * @return a new instance of [ReplanningState].
         */
        fun createFrom(
            tasks: List<Node>,
            depotsSensor: DepotsSensor,
        ): ReplanningState {
            val path = listOf(depotsSensor.sourceDepot, depotsSensor.destinationDepot)
            return ReplanningState(
                dones = tasks.associate { it to false }.toMap(),
                path = path,
                allocations = emptyList(),
            )
        }
    }
}
