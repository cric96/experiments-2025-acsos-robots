package it.unibo.collektive.program

import it.unibo.alchemist.collektive.device.CollektiveDevice
import it.unibo.alchemist.model.sensors.DepotsSensor
import it.unibo.alchemist.model.sensors.LocationSensor
import it.unibo.collektive.aggregate.api.Aggregate
import it.unibo.collektive.alchemist.device.sensors.EnvironmentVariables
import it.unibo.collektive.stdlib.spreading.multiGradientCast
import it.unibo.collektive.stdlib.spreading.nonStabilizingGossip
import it.unibo.formalization.Node
import kotlin.math.pow
import kotlin.math.sqrt

// Export utils *

/**
 * A function that computes the distance between two points in a 2D space.
 */
fun Pair<Double, Double>.distance(other: Pair<Double, Double>): Double =
    sqrt((this.first - other.first).pow(2.0) + (this.second - other.second).pow(2.0))

/**
 * A function used to track the distance of a robot which is moving.
 */
fun Aggregate<Int>.distanceTracking(
    env: EnvironmentVariables,
    locationSensor: LocationSensor,
) {
    val (distance, _) =
        evolve(0.0 to locationSensor.coordinates()) { (totalDistance, myPosition) ->
            val currentPosition = locationSensor.coordinates()
            val distance = currentPosition.distance(myPosition)
            val newDistance = totalDistance + distance
            newDistance to currentPosition
        }
    env["distance"] = distance
}

/* Aggregate functions
   Utility: gossip information with stabilization,
   namely when one node dies,
   that information is not propagated
 */

/**
 * Gossip the information to all the nodes.
 * When one node dies, that information is not propagated.
 */
fun <D> Aggregate<Int>.multiBroadcastBounded(
    distanceSensor: CollektiveDevice<*>,
    sources: Set<Int>,
    value: D,
    maxBound: Double,
): Map<Int, D> {
    val distanceField = with(distanceSensor) { distances() }
    return multiGradientCast(
        sources = sources,
        local = value to 0.0,
        metric = distanceField,
        accumulateData = { fromSource, distance, data ->
            data.first to (fromSource + distance)
        },
    ).filter { it.value.second < maxBound }.mapValues { it.value.first }
}

/**
 * Gossip the node coordinates to all the nodes.
 * @param source what the node should gossip
 * @param distanceSensor the distance sensor
 * @param allIds the ids of the system perceived by the node
 * @param maxBound the maximum bound to consider a node dead
 */
fun Aggregate<Int>.gossipNodeCoordinates(
    source: Node,
    distanceSensor: CollektiveDevice<*>,
    allIds: Set<Int>,
    maxBound: Double,
): List<Node> {
    val globalView =
        multiBroadcastBounded(
            distanceSensor = distanceSensor,
            sources = allIds,
            value = source,
            maxBound = maxBound,
        ).values
    val removeMyself = globalView.filter { it.id != localId }
    return removeMyself + listOf(source)
}

/**
 * isGlobalPathConsistent check it the paths are consistent,
 * namely if all the nodes have the same paths.
 * @param paths the paths to check (local)
 * @param distanceSensor the distance sensor
 * @param allIds the ids of the system perceived by the node
 * @param maxBound the maximum bound to consider a node dead
 */
fun Aggregate<Int>.isGlobalPathConsistent(
    paths: Map<Int, List<Int>>,
    distanceSensor: CollektiveDevice<*>,
    allIds: Set<Int>,
    maxBound: Double,
): Boolean =
    multiBroadcastBounded(
        distanceSensor = distanceSensor,
        sources = allIds,
        value = paths.hashCode(),
        maxBound = maxBound,
    ).values.all { it == paths.hashCode() }

// Stable check (time aspect) *

/**
 * A function that tracks the history of a value.
 * @param value the value to track
 * @param size the size of the history
 */
fun <E> Aggregate<Int>.history(
    value: E,
    size: Int,
): List<E> = evolve(listOf(value)) { listOf(value).plus(it).take(size) }

/**
 * Check if the value is stable in the last size values.
 * @param value the value to check
 * @param size the size of the history
 */
fun <E> Aggregate<Int>.stableFor(
    value: E,
    size: Int,
): Boolean {
    val history = history(value, size)
    return history.size > 2 && history.all { it == value }
}

/**
 * Check if the value is stable in the last size values.
 * @param value the value to check
 * @param size the size of the history
 * @param by the function to check the stability
 */
fun <E, Y> Aggregate<Int>.stableForBy(
    value: E,
    size: Int,
    by: (E) -> Y,
): Boolean {
    val history = history(value, size)
    return history.size > 2 && history.all { by(it) == by(value) }
}

// Stable check (space aspect) *

/**
 * Check if a value is stable in the whole system via gossiping.
 * @param stable the value to check
 * @param distanceSensor the distance sensor
 * @param allIds the ids of the system perceived by the node
 * @param maxBound the maximum bound to consider a node dead
 */
fun Aggregate<Int>.areAllStable(
    stable: Boolean,
    distanceSensor: CollektiveDevice<*>,
    allIds: Set<Int>,
    maxBound: Double,
): Boolean =
    multiBroadcastBounded(
        distanceSensor = distanceSensor,
        sources = allIds,
        value = stable,
        maxBound = maxBound,
    ).values.all { it }

/**
 * Gossip the tasks done to all the nodes.
 * Note! this is not self-stabilizing,
 * so if a node dies, the information will remain in the system.
 */
fun Aggregate<Int>.gossipTasksDone(dones: Set<Node>): Set<Node> = nonStabilizingGossip(dones) { l, r -> l + r }

private const val SHOULD_STOP_WINDOW = 180
private const val CYCLE_NUMBER = 10

/**
 * A utility used to avoid the nodes to go back and forth.
 */
fun Aggregate<Int>.breakingCycle(
    env: EnvironmentVariables,
    locationSensor: LocationSensor,
    depotsSensor: DepotsSensor,
): Boolean {
    val positionTrack = history(locationSensor.coordinates(), SHOULD_STOP_WINDOW)
    val sorted = positionTrack.sortedWith(compareBy({ it.first }, { it.second }))
    val min = sorted.first()
    val max = sorted.last()
    // count how many max and min are inside
    val minCount = sorted.count { it == min }
    val maxCount = sorted.count { it == max }
    if (minCount > CYCLE_NUMBER && maxCount > CYCLE_NUMBER && min != max) {
        env["target"] = depotsSensor.destinationDepot.position
        return true
    }
    return false
}
