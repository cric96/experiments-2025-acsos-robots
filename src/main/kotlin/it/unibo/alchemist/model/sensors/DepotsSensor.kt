package it.unibo.alchemist.model.sensors

import it.unibo.alchemist.model.Node

interface DepotsSensor {
    val getSourceDepot: Pair<Double, Double>

    val getDestinationDepot: Pair<Double, Double>

    val tasks: List<Pair<Double, Double>>

    fun taskNode(position: Pair<Double, Double>): Node<*> // To fix

    fun isTask(): Boolean
}