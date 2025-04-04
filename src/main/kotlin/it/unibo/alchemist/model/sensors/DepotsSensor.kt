package it.unibo.alchemist.model.sensors

import it.unibo.alchemist.model.Node
import it.unibo.formalization.Node as NodeFormalization

interface DepotsSensor {
    val sourceDepot: NodeFormalization

    val destinationDepot: NodeFormalization

    val tasks: List<NodeFormalization>

    fun toNodePath(node: NodeFormalization): Node<*> // To fix

    fun isAgent(): Boolean

    fun alive(): Boolean

    fun isTaskOver(task: NodeFormalization): Boolean
}