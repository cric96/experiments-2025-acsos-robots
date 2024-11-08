package it.unibo.alchemist.model.movestrategies.routing

import it.unibo.alchemist.model.Position
import it.unibo.alchemist.model.Route
import it.unibo.alchemist.model.movestrategies.RoutingStrategy
import it.unibo.alchemist.model.routes.PolygonalChain

class StraightLine<T, P: Position<P>> : RoutingStrategy<T, P> {
    override fun computeRoute(currentPos: P, finalPos: P): Route<P> = PolygonalChain(currentPos, finalPos)
}