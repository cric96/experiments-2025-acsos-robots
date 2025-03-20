package it.unibo.alchemist.model.implementations.reactions

import it.unibo.alchemist.model.Action
import it.unibo.alchemist.model.Actionable
import it.unibo.alchemist.model.Condition
import it.unibo.alchemist.model.Dependency
import it.unibo.alchemist.model.Environment
import it.unibo.alchemist.model.GlobalReaction
import it.unibo.alchemist.model.Position
import it.unibo.alchemist.model.Time
import it.unibo.alchemist.model.TimeDistribution
import it.unibo.alchemist.model.Node
import org.danilopianini.util.ListSet
import org.danilopianini.util.ListSets
import kotlin.collections.List

abstract class AbstractGlobalReaction<T, P : Position<P>>(
    protected val environment: Environment<T, P>,
    protected val distribution: TimeDistribution<T>
) : GlobalReaction<T> {
    override var actions: List<Action<T>> = mutableListOf()
        set(value) {
            field = listOf(*value.toTypedArray())
        }

    override var conditions: List<Condition<T>> = mutableListOf()
        set (value) {
            field = listOf(*value.toTypedArray())
        }

    override val rate: Double
        get() = distribution.getRate()

    override val tau: Time
        get() = distribution.nextOccurence

    override val inboundDependencies: ListSet<out Dependency> = ListSets.emptyListSet()

    override val outboundDependencies: ListSet<out Dependency> = ListSets.emptyListSet()

    override val timeDistribution: TimeDistribution<T> = distribution

    override fun execute() {
        executeBeforeUpdateDistribution()
        distribution.update(timeDistribution.getNextOccurence(), true, rate, environment)
    }

    protected abstract fun executeBeforeUpdateDistribution()

    override fun canExecute(): Boolean = true

    override fun initializationComplete(atTime: Time, environment: Environment<T, *>) {}

    override fun update(currentTime: Time, hasBeenExecuted: Boolean, environment: Environment<T, *>) {}

    override fun compareTo(other: Actionable<T>): Int = tau.compareTo(other.tau)

    // Utility methods
    val nodes: List<Node<T>>
        get() = environment.nodes.iterator().asSequence().toList()
}