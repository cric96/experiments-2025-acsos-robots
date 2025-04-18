package it.unibo.alchemist.model

import it.unibo.alchemist.model.linkingrules.AbstractLocallyConsistentLinkingRule
import it.unibo.alchemist.model.linkingrules.ConnectWithinDistance
import it.unibo.alchemist.model.molecules.SimpleMolecule
import it.unibo.alchemist.model.neighborhoods.Neighborhoods

/**
 * A [LinkingRule] that connects nodes within a certain distance, but breaks the connection
 * if the node contains a certain molecule.
 */
class MoleculeBreaksConnection<T, P : Position<P>>(
    private val environment: Environment<T, P>,
    radius: Double,
    private val molecule: String,
) : AbstractLocallyConsistentLinkingRule<T, P>() {
    private val distance: ConnectWithinDistance<T, P> = ConnectWithinDistance(radius)

    override fun computeNeighborhood(
        p0: Node<T>?,
        p1: Environment<T, P>?,
    ): Neighborhood<T> {
        val neighborhood = distance.computeNeighborhood(p0, p1)
        // remove the node with the molecule
        val molecule = SimpleMolecule(molecule)
        val neighbors = neighborhood.neighbors.filter { it.contents[molecule] == null }
        return Neighborhoods.make(environment, neighborhood.center, neighbors)
    }
}
