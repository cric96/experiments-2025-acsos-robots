package it.unibo.alchemist.model.global

import it.unibo.alchemist.model.Environment
import it.unibo.alchemist.model.Position
import it.unibo.alchemist.model.TimeDistribution
import it.unibo.alchemist.model.implementations.reactions.AbstractGlobalReaction

class DumbAllocationStrategy<T, P : Position<P>>(
    environment: Environment<T, P>,
    distribution: TimeDistribution<T>
): AbstractGlobalReaction<T, P>(environment, distribution), InitialAllocationStrategy {
    override fun executeBeforeUpdateDistribution() {
        TODO("Not yet implemented")
    }
}