package it.unibo.alchemist.model.actions

import it.unibo.alchemist.model.*
import it.unibo.alchemist.model.environments.Environment2DWithObstacles
import it.unibo.alchemist.model.movestrategies.target.FollowTarget
import it.unibo.alchemist.model.positions.Euclidean2DPosition
import org.apache.commons.math3.util.FastMath

class MoveToTargetsWithObstaclesAvoidance<W : Obstacle2D<Euclidean2DPosition>>(
    private val environment: Environment2DWithObstacles<W, Any>,
    private val node: Node<Any>,
    private val reaction: Reaction<Any>,
    private val speed: Double,
    private val proximityRange: Double,
    vararg trackedMolecules: Molecule,
) : AbstractMoveNode<Any, Euclidean2DPosition>(environment, node) {

    private var currentTarget: Int = 0
    private val initialTargets: List<Molecule> = trackedMolecules.toList()

    override fun cloneAction(p0: Node<Any>, p1: Reaction<Any>): Action<Any> {
        return MoveToTargetsWithObstaclesAvoidance(
            environment,
            node,
            reaction,
            speed,
            proximityRange,
            *initialTargets.toTypedArray()
        )
    }

    override fun getNextPosition(): Euclidean2DPosition {
        val target = FollowTarget(getEnvironment(), getNode(), initialTargets[currentTarget]).target
        val current = environment.getPosition(node)
        val vector = target.minus(current.coordinates)
        val angle = FastMath.atan2(vector.y, vector.x)
        val maxWalk = speed / reaction.rate
        val deltaMovement = getEnvironment().makePosition(maxWalk * FastMath.cos(angle), maxWalk * FastMath.sin(angle))

        return environment.getObstaclesInRange(currentPosition, proximityRange)
            .asSequence()
            .map { obstacle: W -> obstacle.nearestIntersection(currentPosition, target) to obstacle.bounds2D }
            .minByOrNull { (intersection, _) -> currentPosition.distanceTo(intersection) }
            ?.let { (intersection, bound) -> intersection to environment.makePosition(bound.minX, bound.minY) }
            ?.let { (intersection, center) -> (intersection - center).coerceAtMost(maxWalk) }
        /*
         * Otherwise we just don't apply any repulsion force.
         */
            ?: let {
                if (current.distanceTo(target) < 0.1 && currentTarget+1<initialTargets.size) {
                    currentTarget++
                }
                if (current.distanceTo(target) < maxWalk) {
                    return vector
                }

                return deltaMovement
            }

    }

}