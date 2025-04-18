package it.unibo.alchemist.model.sensors

import it.unibo.formalization.Node as NodeFormalization

interface DepotsSensor {
    /**
     * The depot where the agent is located.
     */
    val sourceDepot: NodeFormalization

    /**
     * The depot where the agent is going.
     */
    val destinationDepot: NodeFormalization

    /**
     * All the tasks in the environment.
     */
    val tasks: List<NodeFormalization>

    /**
     * Check if the node is an agent or a depot.
     */
    fun isAgent(): Boolean

    /**
     * Check if the node is alive or not.
     */
    fun alive(): Boolean

    /**
     * Check if the task is over.
     */
    fun isTaskOver(task: NodeFormalization): Boolean

    /**
     * Check if the task is the last one.
     */
    fun isReachLastTask(task: NodeFormalization): Boolean
}
