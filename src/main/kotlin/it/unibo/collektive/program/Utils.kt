package it.unibo.collektive.program

import it.unibo.alchemist.collektive.device.CollektiveDevice
import it.unibo.alchemist.model.sensors.LocationSensor
import it.unibo.collektive.aggregate.api.Aggregate
import it.unibo.collektive.alchemist.device.sensors.EnvironmentVariables
import it.unibo.collektive.stdlib.spreading.multiGradientCast
import it.unibo.collektive.stdlib.spreading.nonStabilizingGossip
import it.unibo.formalization.Node
import kotlin.math.pow
import kotlin.math.sqrt

/** Export utils **/
fun Pair<Double, Double>.distance(other: Pair<Double, Double>): Double {
    return sqrt((this.first - other.first).pow(2.0) + (this.second - other.second).pow(2.0))
}

fun Aggregate<Int>.distanceTracking(env: EnvironmentVariables, locationSensor: LocationSensor) {
    val (distance, _) = evolve(0.0 to locationSensor.coordinates()) { (totalDistance, myPosition) ->
        val currentPosition = locationSensor.coordinates()
        val distance = currentPosition.distance(myPosition)
        val newDistance = totalDistance + distance
        newDistance to currentPosition
    }
    env["distance"] = distance
}

/** Aggregate functions **/
// Utility: gossip information with stabilization, namely when one node dies, that information is not propagated
fun <D> Aggregate<Int>.multiBroadcastBounded(
    distanceSensor: CollektiveDevice<*>,
    sources: Set<Int>,
    value: D,
    maxBound: Double,
): Map<Int, D> {
    return multiGradientCast(
        sources = sources,
        local = value to 0.0,
        metric = with(distanceSensor) { distances() },
        accumulateData = { fromSource, distance, data ->
            data.first to (fromSource + distance)
        }
    ).filter { it.value.second < maxBound }.mapValues { it.value.first }
}

fun Aggregate<Int>.gossipNodeCoordinates(
    source: Node,
    distanceSensor: CollektiveDevice<*>,
    allIds: Set<Int>,
    maxBound: Double
): List<Node> {
    val globalView = multiBroadcastBounded(
        distanceSensor = distanceSensor,
        sources = allIds,
        value = source,
        maxBound = maxBound
    ).values
    val removeMyself = globalView.filter { it.id != localId }
    return removeMyself + listOf(source)
}

/** Sanity checks -- check that each node compute the same allocations strategy **/
fun Aggregate<Int>.isGlobalPathConsistent(
    paths: Map<Int, List<Int>>,
    distanceSensor: CollektiveDevice<*>,
    allIds: Set<Int>,
    maxBound: Double
): Boolean = multiBroadcastBounded(
        distanceSensor = distanceSensor,
        sources = allIds,
        value = paths.hashCode(),
        maxBound = maxBound
    ).values.all { it == paths.hashCode() }

/** Stable check (time aspect) **/
fun <E> Aggregate<Int>.history(value: E, size: Int): List<E> = evolve(listOf(value)) { listOf(value).plus(it).take(size) }

fun <E> Aggregate<Int>.stableFor(value: E, size: Int): Boolean {
    val history = history(value, size)
    return history.size > 2 && history.all { it == value }
}


fun <E, Y> Aggregate<Int>.stableForBy(value: E, size: Int, by: (E) -> Y): Boolean {
    val history = history(value, size)
    return history.size > 2 && history.all { by(it) == by(value) }
}

/** Stable check (space aspect) **/
fun Aggregate<Int>.areAllStable(
    stable: Boolean,
    distanceSensor: CollektiveDevice<*>,
    allIds: Set<Int>,
    maxBound: Double
): Boolean = multiBroadcastBounded(
        distanceSensor = distanceSensor,
        sources = allIds,
        value = stable,
        maxBound = maxBound
    ).values.all { it }


/**
 * Gossip the done tasks to all the nodes.
 */
fun Aggregate<Int>.gossipTasksDone(dones: Set<Node>): Set<Node> =
    nonStabilizingGossip(dones) { left, right -> left + right }