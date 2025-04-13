package it.unibo.collektive.program

import it.unibo.alchemist.collektive.device.CollektiveDevice
import it.unibo.alchemist.model.sensors.DepotsSensor
import it.unibo.alchemist.model.sensors.LocationSensor
import it.unibo.collektive.aggregate.api.Aggregate
import it.unibo.collektive.aggregate.api.share
import it.unibo.collektive.alchemist.device.sensors.EnvironmentVariables
import it.unibo.collektive.field.Field.Companion.fold
import it.unibo.collektive.stdlib.accumulation.convergeCast
import it.unibo.collektive.stdlib.accumulation.convergeSum
import it.unibo.collektive.stdlib.consensus.boundedElection
import it.unibo.collektive.stdlib.spreading.distanceTo
import it.unibo.collektive.stdlib.spreading.gradientCast
import it.unibo.collektive.stdlib.spreading.multiGradientCast
import it.unibo.collektive.stdlib.spreading.nonStabilizingGossip
import it.unibo.formalization.GreedyAllocationStrategy
import it.unibo.formalization.RobotAllocationResult
import it.unibo.formalization.Node as NodeFormalization

/** Utils **/
fun allTasksWithoutSource(depotsSensor: DepotsSensor): List<NodeFormalization> {
    return (depotsSensor.tasks.toSet() - setOf(depotsSensor.sourceDepot)).toList().sortedBy { it.id }
}

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

data class ReplanningState(
    val dones: Map<NodeFormalization, Boolean>,
    val path: List<NodeFormalization>,
    val allocations: List<RobotAllocationResult> = emptyList(),
) {
    companion object {
        fun createFrom(tasks: List<NodeFormalization>, depotsSensor: DepotsSensor): ReplanningState {
            val path = listOf(depotsSensor.sourceDepot, depotsSensor.destinationDepot)
            return ReplanningState(
                dones = tasks.associate { it to false }.toMap(),
                path = path,
                allocations = emptyList()
            )
        }
    }
}

/** Field Calculus Utilis **/
/**
 * Check if the task is done by one of the robot, ever in the history
 */
fun Aggregate<Int>.gossipTasksDone(dones: Set<NodeFormalization>): Set<NodeFormalization> {
    return nonStabilizingGossip(dones) { left, right -> left + right }
}

fun <E> Aggregate<Int>.history(value: E, size: Int): List<E> = evolve(listOf(value)) { listOf(value).plus(it).take(size) }

fun <E> Aggregate<Int>.stableFor(value: E, size: Int): Boolean {
    val history = history(value, size)
    return history.size > 2 && history.all { it == value }
}
fun Aggregate<Int>.gossipNodeCoordinates(
    source: NodeFormalization,
    distanceSensor: CollektiveDevice<*>,
    allIds: Set<Int>,
    maxBound: Double
): List<NodeFormalization> {
    val globalView = multiBroadcastBounded(
        distanceSensor = distanceSensor,
        sources = allIds,
        value = source,
        maxBound = maxBound
    ).values
    val removeMyself = globalView.filter { it.id != localId }
    return removeMyself + listOf(source)
}

fun Aggregate<Int>.areAllStable(
    stable: Boolean,
    distanceSensor: CollektiveDevice<*>,
    allIds: Set<Int>,
    maxBound: Double
): Boolean {
    val allRobots = multiBroadcastBounded(
        distanceSensor = distanceSensor,
        sources = allIds,
        value = stable,
        maxBound = maxBound
    ).values
    return allRobots.all { it }
}

/** Sanity checks **/
fun Aggregate<Int>.isGlobalPathConsistent(
    paths: List<RobotAllocationResult>,
    distanceSensor: CollektiveDevice<*>,
    allIds: Set<Int>,
    maxBound: Double
): Boolean {
    val allRobotsMap = multiBroadcastBounded(
        distanceSensor = distanceSensor,
        sources = allIds,
        value = paths,
        maxBound = maxBound
    )
    val myPath = paths.find { it.robot.id == localId }
    val allRobots = allRobotsMap.values
    // check that, everywhere, the path is the same for this robot
    val everyoneSeeMe = allRobots.filter { it -> it.find { it.robot.id == localId } != null }
    // check that, everywhere, the path for me is the same
    val isPathsConsistent = everyoneSeeMe.map { it.find { it.robot.id == localId }?.route.orEmpty() }
    // find the one that are different
    fun routeToIds(route: List<NodeFormalization>?): List<Int>? {
        return route?.map { it.id }
    }
    return everyoneSeeMe.size == allRobots.size && isPathsConsistent.all { routeToIds(it) == routeToIds(myPath?.route) }
}

fun Aggregate<Int>.replanning(
    env: EnvironmentVariables,
    distanceSensor: CollektiveDevice<*>,
    locationSensor: LocationSensor,
    depotsSensor: DepotsSensor
) {

    // check if convergence cast works
    env["hue"] = localId // for debugging
    if(!depotsSensor.alive()) {
        env["target"] = locationSensor.coordinates()
    } else {
        //gossipReplanning(
        //    env,
        //    distanceSensor,
        //    locationSensor,
        //    depotsSensor
        //)
        boundedElectionReplanning(
            env,
            distanceSensor,
            locationSensor,
            depotsSensor
        )
    }
}

val cache = mutableMapOf<List<NodeFormalization>, List<RobotAllocationResult>>()
val maxBound = 1000.0
val timeWindow = 5
fun Aggregate<Int>.gossipReplanning(
    env: EnvironmentVariables,
    distanceSensor: CollektiveDevice<*>,
    locationSensor: LocationSensor,
    depotsSensor: DepotsSensor) {
    val allTasks =  evolve(allTasksWithoutSource(depotsSensor)) { it }
    val myPosition = evolve(locationSensor.coordinates()) { it }
    // all ids in the system, ever seen
    val allIds = share(setOf(localId)) { it.fold(setOf(localId)){ acc, value -> acc + value } }

    evolve(ReplanningState.createFrom(allTasks, depotsSensor)) { state ->
        val nodeRepresentation = NodeFormalization(locationSensor.coordinates(), localId)
        /** Consensus part: check if the node should recompute the path */
        val globalConsistency = isGlobalPathConsistent(state.allocations, distanceSensor, allIds, maxBound)
        val allConsistent = areAllStable(globalConsistency, distanceSensor, allIds, maxBound)
        env["globalConsistent"] = globalConsistency
        /** Ever done -- check if the task is completed by one of the robot */
        val allTaskDone = gossipTasksDone(state.dones.filter { it.value }.keys) // avoid to recompute task already done
        /** All robots that I may see with multipath communication */
        val allRobots = gossipNodeCoordinates(nodeRepresentation, distanceSensor, allIds, maxBound)
        env["allRobots"] = allRobots
        // get id that are repeated
        /** Check if the robots are stable */
        val areRobotStable = stableFor(allRobots.map { it.id }.toSet(), timeWindow)
        // val allRobotsAreaStable = areAllStable(isRobotStable, distanceSensor, allIds)
        /** Check if the robots are stable */
        //val areAllStable = areAllStable(isRobotStable, distanceSensor, allIds)
        val stableCondition = areRobotStable && allConsistent
        when {
            // stability condition is not satisfied, recompute the path
            !stableCondition -> {
                /** allocation part: recompute the path giving the new information */
                val reducedTasks = allTasks.filter { it !in allTaskDone }
                val globalPlan = cache.getOrPut(allRobots.sortedBy { it.id }) {
                    GreedyAllocationStrategy(
                        allRobots.sortedBy { it.id },
                        reducedTasks.sortedBy { it.id },
                        depotsSensor.destinationDepot
                    ).execute()
                }
                val myPlan = globalPlan.find { it.robot.id == localId }
                standStill(env, locationSensor) // avoid flickering
                state.copy(
                    path = myPlan?.route.orEmpty(),
                    allocations = globalPlan
                )
            }
            else -> followPlan(
                env,
                depotsSensor,
                locationSensor,
                state
            )
        }
    }
}


fun <E, Y> Aggregate<Int>.stableForBy(value: E, size: Int, by: (E) -> Y): Boolean {
    val history = history(value, size)
    return history.size > 2 && history.all { by(it) == by(value) }
}

fun Aggregate<Int>.boundedElectionReplanning(
    env: EnvironmentVariables,
    distanceSensor: CollektiveDevice<*>,
    locationSensor: LocationSensor,
    depotsSensor: DepotsSensor
) {
    //val positionCache = evolve(locationSensor.coordinates()) { it }
    val allTasks = allTasksWithoutSource(depotsSensor)
    val leaderId = boundedElection(maxBound, with(distanceSensor) { distances() })
    env["leader"] = leaderId
    val isLeader = leaderId == localId
    val nodePosition = NodeFormalization(locationSensor.coordinates(), localId)
    // collect nodes
    val allRobotsFromLeader = convergeCast(listOf(nodePosition), isLeader) { left, right ->
        left + right
    }
    val areRobotsStable = stableForBy(allRobotsFromLeader, timeWindow) { it.map { it.id }.toSet() }
    evolve(ReplanningState.createFrom(allTasks, depotsSensor)) { state ->
        val taskEverDone = gossipTasksDone(state.dones.filter { it.value}.keys)
        val reducedTasks = allTasks.filter { it !in taskEverDone }
        val newPlan = if(!areRobotsStable && isLeader) {
            GreedyAllocationStrategy(
                allRobotsFromLeader.sortedBy { it.id },
                reducedTasks.sortedBy { it.id },
                depotsSensor.destinationDepot
            ).execute()
        } else {
            state.allocations
        }
        // share
        val leaderPlan = gradientCast(isLeader, newPlan, with(distanceSensor) { distances()})
        val leaderStable = gradientCast(isLeader, areRobotsStable, with(distanceSensor) { distances() })
        env["stable"] = leaderStable
        val myPlan = leaderPlan.find { it.robot.id == localId }
        if(leaderStable) {
            followPlan(
                env, depotsSensor, locationSensor, state.copy(
                    path = myPlan?.route ?: listOf(nodePosition, nodePosition),
                    allocations = leaderPlan
                )
            )
        } else {
            standStill(env, locationSensor)
            state.copy(
                allocations = leaderPlan
            )
        }

    }
}

fun Aggregate<Int>.followPlan(
    env: EnvironmentVariables,
    depotsSensor: DepotsSensor,
    locationSensor: LocationSensor,
    state: ReplanningState
): ReplanningState {
    val path = state.path.drop(1) // source depot is not a task
    // find first available
    val firstAvailable = path.firstOrNull { !(state.dones[it] ?: false) }
    val selected = firstAvailable ?: state.path.last()
    env["target"] = locationSensor.estimateCoordinates(selected)
    env["selected"] = selected.id
    // check if the task is done
    val isDone = depotsSensor.isTaskOver(selected)
    val updated = state.dones.toMutableMap()
    updated[selected] = isDone
    // update the state
    return state.copy(dones = updated)
}


fun standStill(env: EnvironmentVariables, locationSensor: LocationSensor) {
    env["target"] = locationSensor.coordinates()
}