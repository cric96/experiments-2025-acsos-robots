package it.unibo.alchemist.model.global

import it.unibo.alchemist.model.Environment
import it.unibo.alchemist.model.Position
import it.unibo.alchemist.model.TimeDistribution
import it.unibo.alchemist.model.implementations.reactions.AbstractGlobalReaction
import it.unibo.alchemist.model.molecules.SimpleMolecule
import org.apache.commons.math3.random.RandomGenerator

class RandomFail<T, P: Position<P>>(
    environment: Environment<T, P>,
    timeDistribution: TimeDistribution<T>,
    val randomGenerator: RandomGenerator,
    var howMany: Int = 1,
): AbstractGlobalReaction<T, P>(environment, timeDistribution) {
    override fun executeBeforeUpdateDistribution() {
        val nodesRemovable = nodes
            .filter { it.contents.containsKey(SimpleMolecule("agent")) }
            .filterNot { it.contents[SimpleMolecule("down")] == true }

        val randomNode = randomGenerator.nextInt(nodesRemovable.size)
        val toKill = nodesRemovable[randomNode]
        toKill.setConcentration(SimpleMolecule("down"), true as T)
    }

}