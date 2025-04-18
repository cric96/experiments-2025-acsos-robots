/*
 * Copyright (C) 2010-2023, Danilo Pianini and contributors
 * listed, for each module, in the respective subproject's build.gradle.kts file.
 *
 * This file is part of Alchemist, and is distributed under the terms of the
 * GNU General Public License, with a linking exception,
 * as described in the file LICENSE in the Alchemist distribution's top directory.
 */
package it.unibo.alchemist.model.movestrategies.target

import it.unibo.alchemist.model.Environment
import it.unibo.alchemist.model.Molecule
import it.unibo.alchemist.model.Node
import it.unibo.alchemist.model.Position
import it.unibo.alchemist.model.Reaction
import it.unibo.alchemist.model.movestrategies.TargetSelectionStrategy
import kotlin.CharSequence
import kotlin.Number

/**
 * This strategy reads the value of a "target" molecule and tries to interpret
 * it as a coordinate.
 *
 * @param environment the environment
 * @param node the node
 * @param targetMolecule the target molecule
 * @param <T> Concentration type
</T> */
open class FollowTarget<T, P : Position<P>>(
    protected val environment: Environment<T, P>,
    private val node: Node<T>,
    val targetMolecule: Molecule,
) : TargetSelectionStrategy<T, P> {
    /**
     * @param x first coordinate extracted from the target concentration
     * @param y second coordinate extracted from the target concentration
     * @return a [Position] built using such parameters
     */
    protected open fun createPosition(
        x: Double,
        y: Double,
    ): P = environment.makePosition(x, y)

    /**
     * the current position.
     */
    protected val currentPosition: P get() = environment.getPosition(node)

    override fun getTarget(): P {
        val conc = node.getConcentration(targetMolecule) ?: return currentPosition
        @Suppress("UNCHECKED_CAST")
        return when (conc) {
            is Position<*> -> conc as P
            is CharSequence, is Iterable<*> -> conc.extractCoordinates() ?: currentPosition
            else -> conc.toString().extractCoordinates() ?: currentPosition
        }
    }

    /**
     * Extracts a pair of coordinates from an iterable or a string representation.
     */
    private fun Any.extractCoordinates(): P? {
        val values: Sequence<Number> =
            when (this) {
                // is CharSequence -> Regex(Patterns.FLOAT).findAll(this).map { it.value.toNumber() }
                is Iterable<*> -> asSequence().map { it.toNumber() }
                else -> emptySequence()
            }
        val coords = values.toList()
        return when (coords.size) {
            environment.dimensions -> environment.makePosition(coords)
            in 0..<environment.dimensions -> {
                null
            }
            else -> {
                val trimmed = coords.take(environment.dimensions)

                environment.makePosition(trimmed)
            }
        }
    }

    private fun conversionError(value: Any?): Nothing =
        error(
            "${this::class.simpleName} tried to convert " +
                "$this (${value?.let { it::class.simpleName}}) to a Number, but failed",
        )

    /**
     * Tries to convert an object to Double, handling exceptions safely.
     */
    private fun Any?.toNumber(): Number =
        when (this) {
            is Number -> this
            is CharSequence -> toString().toDoubleOrNull()
            else -> null
        } ?: conversionError(this)

    override fun cloneIfNeeded(
        destination: Node<T>,
        reaction: Reaction<T>,
    ): FollowTarget<T, P> = FollowTarget(environment, destination, this.targetMolecule)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FollowTarget<*, *>) return false

        if (environment != other.environment) return false
        if (node != other.node) return false
        if (targetMolecule != other.targetMolecule) return false

        return true
    }

    override fun hashCode(): Int {
        var result = environment.hashCode()
        result = 31 * result + node.hashCode()
        result = 31 * result + targetMolecule.hashCode()
        return result
    }
}
