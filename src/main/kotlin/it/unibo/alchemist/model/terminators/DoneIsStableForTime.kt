package it.unibo.alchemist.model.terminators

import it.unibo.alchemist.model.Environment
import it.unibo.alchemist.model.Position
import it.unibo.alchemist.model.TerminationPredicate
import it.unibo.alchemist.model.Time
import it.unibo.alchemist.model.Time.Companion.ZERO
import it.unibo.alchemist.model.molecules.SimpleMolecule

/**
 * [stableForTime] for how much time the [metricsToCheck] should be stable.
 * [timeIntervalToCheck] every time-step it should check, if zero it checks at every invocation.
 * [equalTimes] how many times it should be stable.
 */
class DoneIsStableForTime<T>
@JvmOverloads
constructor(
    private val stableForTime: Time,
    private val timeIntervalToCheck: Time = ZERO,
    private val equalTimes: Long,
): TerminationPredicate<T, Position<*>> {
    private var timeStabilitySuccess: Time = ZERO
    private var lastChecked: Time = ZERO
    private var equalSuccess: Long = 0
    private var lastUpdatedMetrics: Map<String, T> = emptyMap()

    init {
        require(stableForTime > ZERO) {
            "The amount of time to check the stability should be more than zero."
        }
    }

    override fun invoke(environment: Environment<T, Position<*>>): Boolean {
        val simulationTime = environment.simulation.time
        val checkedInterval = simulationTime - lastChecked
        // count task dones
        val doneMolecule = SimpleMolecule("isDone")
        val dones = environment.nodes
            .filter { it.contains(doneMolecule) }
            .map { it.contents[doneMolecule] as Double }
            .sum()
        return when {
            checkedInterval >= timeIntervalToCheck -> {
                val metrics: Map<String, T> = mapOf("isDone" to dones as T)
                require(metrics.isNotEmpty()) {
                    "There should be at least one metric to check."
                }
                lastChecked = simulationTime
                when {
                    lastUpdatedMetrics == metrics -> {
                        timeStabilitySuccess += checkedInterval
                        if(timeStabilitySuccess >= stableForTime) {
                            timeStabilitySuccess = ZERO
                            ++equalSuccess >= equalTimes
                        } else false
                    }
                    else -> {
                        timeStabilitySuccess = ZERO
                        equalSuccess = 0
                        lastUpdatedMetrics = metrics
                        false
                    }
                }
            }
            else -> false
        }
    }
}