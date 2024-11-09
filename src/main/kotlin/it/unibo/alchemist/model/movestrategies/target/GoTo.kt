package it.unibo.alchemist.model.movestrategies.target

import it.unibo.alchemist.model.*
import it.unibo.alchemist.model.geometry.Vector
import it.unibo.alchemist.model.molecules.SimpleMolecule
import it.unibo.alchemist.model.movestrategies.TargetSelectionStrategy
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class GoTo<T, P>(
    val environment: Environment<T, P>,
    val node: Node<T>,
    val destination: P,
    val slices: Int = 20,
): TargetSelectionStrategy<T, P>

where P : Position<P>, P : Vector<P>  {

    constructor(
        environment: Environment<T, P>,
        node: Node<T>,
        vararg destination: Number,
    ): this(environment, node, environment.makePosition(*destination))

    private var obstacleAvoidanceDestination = destination

    override fun getTarget(): P = if (environment is EnvironmentWithObstacles<*, T, P>) {
        val currentPosition = environment.getPosition(node)
        val straightPath = environment.next(currentPosition, destination)
        if (straightPath != destination) {
            if (obstacleAvoidanceDestination == destination) {
                val maxDistance = currentPosition.distanceTo(destination)
                val segment = destination - currentPosition
                obstacleAvoidanceDestination = (0 until slices).asSequence()
                    .map { index ->
                        val angle = atan2(segment.getCoordinate(1), segment.getCoordinate(0)) + index * 2 * PI / slices
                        val newDestination = currentPosition + doubleArrayOf(maxDistance * cos(angle), maxDistance * sin(angle))
                        angle to environment.next(currentPosition, newDestination)
                    }.maxWith(
                        compareBy<Pair<Double, P>> { (_, it) -> currentPosition.distanceTo(it) }
                            .thenByDescending { (angle, _) -> abs(angle % PI) }
                    ).second
                obstacleAvoidanceDestination
            } else {
                obstacleAvoidanceDestination
            }
        } else {
            destination
        }
    } else {
        destination
    }

    override fun toString() = "${GoTo::class.simpleName}:$destination"
}
