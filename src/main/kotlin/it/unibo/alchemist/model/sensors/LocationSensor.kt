package it.unibo.alchemist.model.sensors

import it.unibo.formalization.Node

interface LocationSensor {
    /**
     * Returns the coordinates of the node's position inside the environment.
     */
    fun coordinates(): Pair<Double, Double>

    /**
     * Returns the coordinates of the neighborhood.
     */
    fun surroundings(): List<Pair<Double, Double>>

    /**
     * Estimate the coordinate of a remote node.
     */
    fun estimateCoordinates(node: Node): Iterable<Double>
}
