package it.unibo.alchemist.model.movestrategies.routing

import it.unibo.alchemist.model.EnvironmentWithObstacles
import it.unibo.alchemist.model.Obstacle
import it.unibo.alchemist.model.Position2D
import it.unibo.alchemist.model.Route
import it.unibo.alchemist.model.geometry.Vector
import it.unibo.alchemist.model.movestrategies.RoutingStrategy
import it.unibo.alchemist.model.routes.PolygonalChain
import org.apache.commons.math3.random.RandomGenerator
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * A [StraightLine] [RoutingStrategy] that avoids obstacles in the environment.
 *
 */
data class ObstacleAvoidance<W, T, P>(
    private val environment: EnvironmentWithObstacles<W, T, P>,
    private val randomGenerator: RandomGenerator,
) : RoutingStrategy<T, P> where P : Position2D<P>, P : Vector<P>, W : Obstacle<P> {
    override fun computeRoute(
        currentPos: P,
        finalPos: P,
    ): Route<P> {
        val straightPath = environment.next(currentPos, finalPos)
        if (straightPath != finalPos) {
            val maxDistance = currentPos.distanceTo(finalPos)
            val segment = finalPos - currentPos
            val obstacleAvoidanceDestination =
                (0 until SLICES)
                    .asSequence()
                    .map { index ->
                        var finalIndex = index
                        val randomDirection = randomGenerator.nextInt(2)
                        if (randomDirection == 0) finalIndex = -index
                        val angle = atan2(segment.x, segment.y) + finalIndex * 2 * PI / SLICES
                        val newDestination =
                            currentPos +
                                doubleArrayOf(
                                    maxDistance * cos(angle),
                                    maxDistance * sin(angle),
                                )
                        angle to environment.next(currentPos, newDestination)
                    }.maxWith(
                        compareBy<Pair<Double, P>> { (_, it) -> currentPos.distanceTo(it) }
                            .thenByDescending { (angle, _) -> abs(angle % PI) },
                    ).second
            return PolygonalChain(currentPos, obstacleAvoidanceDestination)
        } else {
            return PolygonalChain(currentPos, finalPos)
        }
    }

    private companion object {
        private const val SLICES = 20
    }
}
