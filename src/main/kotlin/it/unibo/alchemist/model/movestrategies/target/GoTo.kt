package it.unibo.alchemist.model.movestrategies.target

import it.unibo.alchemist.model.Environment
import it.unibo.alchemist.model.EnvironmentWithObstacles
import it.unibo.alchemist.model.Node
import it.unibo.alchemist.model.Position
import it.unibo.alchemist.model.geometry.Vector
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
    val repulsionToObstacles: Double = 0.5,
): TargetSelectionStrategy<T, P>

where P : Position<P>, P : Vector<P>  {

    constructor(
        environment: Environment<T, P>,
        node: Node<T>,
        vararg destination: Number,
    ): this(environment, node, environment.makePosition(*destination))

    override fun getTarget(): P = if (environment is EnvironmentWithObstacles<*, T, P>) {
        val currentPosition = environment.getPosition(node)
        val straightPath = environment.next(currentPosition, destination)
        if (straightPath != destination) {
            if (currentPosition.distanceTo(straightPath) > repulsionToObstacles) { // Da sistemare questa condizione
                val maxDistance = currentPosition.distanceTo(destination)
                val segment = destination - currentPosition
                (0 until slices).asSequence()
                    .map { index ->
                        val angle = atan2(segment.getCoordinate(1), segment.getCoordinate(0)) + index * 2 * PI / slices
                        angle to currentPosition + doubleArrayOf(maxDistance * cos(angle), maxDistance * sin(angle))
                    }
                    .maxWith(
                        compareBy<Pair<Double, P>> { (_, it) -> currentPosition.distanceTo(it) }
                            .thenByDescending { (angle, _) -> abs(angle % PI) }
                    ).second
            } else {
                destination
            }
        } else {
            destination
        }
    } else {
        destination
    }

    override fun toString() = "${GoTo::class.simpleName}:$destination"
}
