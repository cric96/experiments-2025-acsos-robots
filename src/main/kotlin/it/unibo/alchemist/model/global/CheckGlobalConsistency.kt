package it.unibo.alchemist.model.global

import it.unibo.alchemist.model.Environment
import it.unibo.alchemist.model.Position
import it.unibo.alchemist.model.TimeDistribution
import it.unibo.alchemist.model.implementations.reactions.AbstractGlobalReaction
import it.unibo.alchemist.model.molecules.SimpleMolecule
import it.unibo.alchemist.model.times.DoubleTime
import it.unibo.formalization.Node
import org.apache.commons.math3.random.RandomGenerator

class CheckGlobalConsistency<T, P: Position<P>>(
    environment: Environment<T, P>,
    timeDistribution: TimeDistribution<T>,
    val randomGenerator: RandomGenerator,
): AbstractGlobalReaction<T, P>(environment, timeDistribution) {
    override fun executeBeforeUpdateDistribution() {
        if (environment.simulation.time < DoubleTime(10.0)) return
        val allAgents = nodes.filter { it.contents.containsKey(SimpleMolecule("agent")) }
        // get robots
        val alive = allAgents.filterNot { it.contents[SimpleMolecule("down")] as Boolean? ?: false }
        val robots = alive.map { it.contents[SimpleMolecule("allRobots")] as List<Node> }
        // print if it is the same in the whole system
        val allRobots = robots.all { it == robots[0] }

        // extract just the id
        val ids = robots.map { it.map { it.id } }
        // get differences in ids
        val idsDiff = ids.map { it.toSet() }
        // get differences with respect to the first one
        val allIdsSame = ids.all { it == ids[0] }
        println("All robots are the same: $allRobots")
        // get the differences with respect to 0
        for (i in 1 until ids.size) {
            if(robots[0] != robots[i]) {
                // print the ids of the differences
                val diff = robots[i] - robots[0]
                println("Differences with respect to $i: ${diff.map { it.id }}")
            }
        }

    }

}