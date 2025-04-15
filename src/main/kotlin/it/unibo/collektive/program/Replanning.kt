package it.unibo.collektive.program

import it.unibo.alchemist.collektive.device.CollektiveDevice
import it.unibo.alchemist.model.sensors.DepotsSensor
import it.unibo.alchemist.model.sensors.LocationSensor
import it.unibo.collektive.aggregate.api.Aggregate
import it.unibo.collektive.aggregate.api.neighboring
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
import kotlin.math.pow
import kotlin.math.sqrt
import it.unibo.formalization.Node as NodeFormalization

/** Replanning state */
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

/** Utils **/
fun allTasksWithoutSource(depotsSensor: DepotsSensor): List<NodeFormalization> {
    return (depotsSensor.tasks.toSet() - setOf(depotsSensor.sourceDepot)).toList().sortedBy { it.id }
}

fun Aggregate<Int>.replanning(
    env: EnvironmentVariables,
    distanceSensor: CollektiveDevice<*>,
    locationSensor: LocationSensor,
    depotsSensor: DepotsSensor
) {
    if(!depotsSensor.alive()) {
        env["target"] = locationSensor.coordinates()
        env["replanning"] = 0
    } else {
        when(env.get("leaderBased") as Boolean) {
            true -> boundedElectionReplanning(
                env, distanceSensor, locationSensor, depotsSensor
            )
            else -> gossipReplanning(
                env, distanceSensor, locationSensor, depotsSensor
            )
        }
    }
    // utility function / extraction
    env["hue"] = localId // for debugging
    distanceTracking(env, locationSensor)
    env["neighbors"] = neighboring(1).fold(0) { acc, value -> acc + value }
}

val maxBound = 1000.0
val timeWindow = 5

fun Aggregate<Int>.gossipReplanning(
    env: EnvironmentVariables,
    distanceSensor: CollektiveDevice<*>,
    locationSensor: LocationSensor,
    depotsSensor: DepotsSensor
) {
    val allTasks =  evolve(allTasksWithoutSource(depotsSensor)) { it }
    // all ids in the system, ever seen
    val allIds = share(setOf(localId)) { it.fold(setOf(localId)){ acc, value -> acc + value } }

    evolve(ReplanningState.createFrom(allTasks, depotsSensor)) { state ->
        val nodeFormalization = NodeFormalization(locationSensor.coordinates(), localId)
        /**
         * All task ever done (via nonStabilizingGossip)
         */
        val allTaskDone = gossipTasksDone(state.dones.filter { it.value }.keys) // avoid to recompute task already done
        /** All robots that I may see with multipath communication */
        val allRobots = gossipNodeCoordinates(nodeFormalization, distanceSensor, allIds, maxBound)
        val stableCondition = gossipStabilityCondition(
            distanceSensor,
            state,
            allIds,
            allRobots
        )
        when {
            // stability condition is not satisfied, recompute the path
            !stableCondition -> {
                /** allocation part: recompute the path giving the new information */
                val reducedTasks = allTasks.filter { it !in allTaskDone }
                val globalPlan = GreedyAllocationStrategy(
                        allRobots.sortedBy { it.id },
                        reducedTasks.sortedBy { it.id },
                        depotsSensor.destinationDepot
                    ).execute().second
                env["replanning"] = 1
                env["totalReplanning"] = (env.getOrNull<Int>("totalReplanning") ?: 0) + 1
                val myPlan = globalPlan.find { it.robot.id == localId }
                standStill(env, locationSensor) // avoid flickering
                state.copy(
                    path = myPlan?.route.orEmpty(),
                    allocations = globalPlan
                )
            }
            else -> {
                env["replanning"] = 0
                followPlan(
                    env,
                    depotsSensor,
                    locationSensor,
                    state
                )
            }
        }
    }
}

fun Aggregate<Int>.gossipStabilityCondition(
    distanceSensor: CollektiveDevice<*>,
    state: ReplanningState,
    allIds: Set<Int>,
    allRobots: List<NodeFormalization>
): Boolean {
    /** Consensus part: check if the node should recompute the path */
    val pathMap = state.allocations.associate { it.robot.id to it.route.map { it.id } }
    val globalConsistency = isGlobalPathConsistent(pathMap, distanceSensor, allIds, maxBound)
    val allConsistent = areAllStable(globalConsistency, distanceSensor, allIds, maxBound)
    /** Check if the robots are stable */
    val areRobotStable = stableFor(allRobots.map { it.id }.toSet(), timeWindow)
    return areRobotStable && allConsistent
}

fun Aggregate<Int>.boundedElectionReplanning(
    env: EnvironmentVariables,
    distanceSensor: CollektiveDevice<*>,
    locationSensor: LocationSensor,
    depotsSensor: DepotsSensor
) {
    val allTasks = allTasksWithoutSource(depotsSensor)
    val leaderId = boundedElection(maxBound, with(distanceSensor) { distances() })
    val isLeader = leaderId == localId
    env["isLeader"] = if(isLeader) { 1.0 } else { 0.0 }
    val nodePosition = NodeFormalization(locationSensor.coordinates(), localId)
    // collect nodes
    val allRobotsFromLeader = convergeCast(listOf(nodePosition), isLeader) { left, right ->
        left + right
    }
    val areRobotsStable = stableForBy(allRobotsFromLeader, timeWindow) { it.map { it.id }.toSet() }
    evolve(ReplanningState.createFrom(allTasks, depotsSensor)) { state ->
        val taskEverDone = gossipTasksDone(state.dones.filter { it.value }.keys)
        env["taskSize"] = state.path.size
        val reducedTasks = allTasks.filter { it !in taskEverDone }
        val newPlan = if(!areRobotsStable && isLeader) {
            env["replanning"] = 1
            env["totalReplanning"] = (env.getOrNull<Int>("totalReplanning") ?: 0) + 1
            GreedyAllocationStrategy(
                allRobotsFromLeader.sortedBy { it.id },
                reducedTasks.sortedBy { it.id },
                depotsSensor.destinationDepot
            ).execute().second
        } else {
            env["replanning"] = 0
            state.allocations
        }
        // share
        val leaderPlan = gradientCast(isLeader, newPlan, with(distanceSensor) { distances()})
        val leaderStable = gradientCast(isLeader, areRobotsStable && isLeader, with(distanceSensor) { distances() })
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
    if(isDone) {
        env["dones"] = (env.getOrNull<Int>("dones") ?: 0) + 1
    }
    val updated = state.dones.toMutableMap()
    updated[selected] = isDone
    // update the state
    return state.copy(dones = updated)
}


fun standStill(env: EnvironmentVariables, locationSensor: LocationSensor) {
    env["target"] = locationSensor.coordinates()
}