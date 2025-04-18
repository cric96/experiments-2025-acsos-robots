package it.unibo.alchemist.model.global

import it.unibo.alchemist.model.Environment
import it.unibo.alchemist.model.Position
import it.unibo.alchemist.model.TimeDistribution
import it.unibo.alchemist.model.molecules.SimpleMolecule
import it.unibo.alchemist.util.Iterables.shuffled
import org.apache.commons.math3.random.RandomGenerator

/**
 * A [GlobalReaction] that randomly fails a node by setting its concentration to "down".
 */
class RandomFail<T, P : Position<P>>(
    environment: Environment<T, P>,
    timeDistribution: TimeDistribution<T>,
    private val randomGenerator: RandomGenerator,
    private var howMany: Int = 1,
) : AbstractGlobalReaction<T, P>(environment, timeDistribution) {
    override fun executeBeforeUpdateDistribution() {
        val nodesRemovable =
            nodes
                .filter { it.contents.containsKey(SimpleMolecule("agent")) }
                .filterNot { it.contents[SimpleMolecule("down")] == true }

        nodesRemovable.shuffled(randomGenerator).take(howMany).forEach { toKill ->
            toKill.setConcentration(SimpleMolecule("down"), true as T)
        }
    }

}
