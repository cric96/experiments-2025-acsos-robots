package it.unibo.alchemist.model.movestrategies.target

import it.unibo.alchemist.model.Environment
import it.unibo.alchemist.model.Position
import it.unibo.alchemist.model.movestrategies.TargetSelectionStrategy

/**
 * TargetSelectionStrategy that has the objective to go towards a fixed destination in the Environment.
 *
 * @param <T> Concentration type
 * @param <P> position type
 */
class GoTo<T, P: Position<P>>(
    private val destination: P,
): TargetSelectionStrategy<T, P> {

    /**
     * @param environment: the environment executing the simulation,
     * @param destination: an indefinite number of [Number] values that indicates the coordinates of the destination.
     * @returns a [TargetSelectionStrategy] which aim to go towards the fixed destination in the environment.
     */
    constructor(
        environment: Environment<T, P>,
        vararg destination: Number,
    ): this(environment.makePosition(*destination))

    override fun getTarget(): P = destination

    override fun toString() = "${GoTo::class.simpleName}:$destination"

}