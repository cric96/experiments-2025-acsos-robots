package it.unibo.alchemist.model.sensors

import it.unibo.alchemist.model.Environment
import it.unibo.alchemist.model.Node
import it.unibo.alchemist.model.NodeProperty
import it.unibo.alchemist.model.Position
import org.apache.commons.math3.random.RandomGenerator
import it.unibo.formalization.Node as NodeFormalization
class LocationSensorProperty<T : Any, P : Position<P>>(
    private val environment: Environment<T, P>,
    override val node: Node<T>,
    val randomGenerator: RandomGenerator,
) : LocationSensor,
    NodeProperty<T>
{
    override fun cloneOnNewNode(node: Node<T>): NodeProperty<T> = LocationSensorProperty(environment, node, randomGenerator)

    override fun coordinates(): Pair<Double, Double> {
        val position = environment.getPosition(node).coordinates
        return Pair(position[0], position[1])
    }

    override fun surroundings(): List<Pair<Double, Double>> =
        environment.getNeighborhood(node).map { node ->
            environment.getPosition(node).coordinates.let { Pair(it[0], it[1]) }
        }

    // TODO better cast for this (also consider to add task sensor
    override fun estimateCoordinates(node: NodeFormalization): Iterable<Double> {
        val nodeAlchemist = environment.getNodeByID(node.id)
        // val gaussianNoise = randomGenerator.nextGaussian()
        // add the gaussian noise to the coordinates

        val position = environment.getPosition(nodeAlchemist).coordinates
        // add a gaussian noise for each coordinate:
        //for (i in position.indices) {
        //    position[i] += (randomGenerator.nextGaussian() * chaosAmount)
        //}
        return position.toList()
    }
}