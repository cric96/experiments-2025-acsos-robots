package it.unibo.alchemist.model.movestrategies.routing

import it.unibo.alchemist.model.Environment
import it.unibo.alchemist.model.Position
import it.unibo.alchemist.model.Route
import it.unibo.alchemist.model.movestrategies.RoutingStrategy
import it.unibo.alchemist.model.routes.PolygonalChain

data class Waypoints<T, P : Position<P>>(
    val waypoints: List<P>,
) : RoutingStrategy<T, P> {

    init {
        require(waypoints.isNotEmpty()) {
            "At least one waypoint must be provided"
        }
        require(
            // This check is necessary as via reflection any list could be fit in
            waypoints.all {
                @Suppress("USELESS_IS_CHECK")
                it is Position<*>
            }
        )
    }

    constructor(
        environment: Environment<T, P>,
        waypoints: List<List<Number>>,
    ): this(waypoints.map { environment.makePosition(*it.toTypedArray()) })

    override fun computeRoute(currentPos: P, finalPos: P): Route<P> = PolygonalChain(
        listOf(currentPos) + waypoints + finalPos
    )
}
