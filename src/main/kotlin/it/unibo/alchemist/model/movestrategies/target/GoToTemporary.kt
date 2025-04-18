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
import kotlin.math.sin

/**
 *
 * IMPORTANT: This is not the final implementation of the GoToTemporary TargetSelectionStrategy.
 * This class' final implementation will hold only the final destination of the agent,
 * which is represented as a P in the Environment<*, P>.
 *
 * The obstacle avoidance part will be implemented as a custom routing strategy.
 *
 * When the agents will have a program, they will follow a target molecule, as the MoveToTarget TargetSelectionStrategy,
 * but with a custom RoutingStrategy for ObstacleAvoidance.
 */
class GoToTemporary<T, P>(
    private val environment: Environment<T, P>,
    private val node: Node<T>,
    private val destination: P,
    private val slices: Int = 20,
) : TargetSelectionStrategy<T, P> where P : Position<P>, P : Vector<P> {
    constructor(
        environment: Environment<T, P>,
        node: Node<T>,
        vararg destination: Number,
    ) : this(environment, node, environment.makePosition(*destination))

    private var obstacleAvoidanceDestination = destination

    override fun getTarget(): P =
        if (environment is EnvironmentWithObstacles<*, T, P>) {
            val currentPosition = environment.getPosition(node)
            val straightPath = environment.next(currentPosition, destination)
            if (straightPath != destination) {
                if (obstacleAvoidanceDestination == destination) {
                    val maxDistance = currentPosition.distanceTo(destination)
                    val segment = destination - currentPosition
                    obstacleAvoidanceDestination =
                        (0 until slices)
                            .asSequence()
                            .map { index ->
                                val angle = atan2(
                                    segment.getCoordinate(1),
                                    segment.getCoordinate(0)
                                ) + index * 2 * PI / slices
                                val newDestination = currentPosition + doubleArrayOf(
                                    maxDistance * cos(angle),
                                    maxDistance * sin(angle)
                                )
                                angle to environment.next(currentPosition, newDestination)
                            }.maxWith(
                                compareBy<Pair<Double, P>> { (_, it) -> currentPosition.distanceTo(it) }
                                    .thenByDescending { (angle, _) -> abs(angle % PI) },
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

    override fun toString() = "${GoToTemporary::class.simpleName}:$destination"
}
