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
import it.unibo.collektive.stdlib.accumulation.findParents
import it.unibo.collektive.stdlib.consensus.boundedElection
import it.unibo.collektive.stdlib.spreading.distanceTo
import it.unibo.collektive.stdlib.spreading.gradientCast
import it.unibo.collektive.stdlib.util.hops
import it.unibo.formalization.GreedyAllocationStrategy
import it.unibo.formalization.Node as NodeFormalization

// Utils *

/**
 * A simple function which returns the tasks without the source depot.
 */
fun allTasksWithoutSource(depotsSensor: DepotsSensor): List<NodeFormalization> =
    (depotsSensor.tasks.toSet() - setOf(depotsSensor.sourceDepot)).toList().sortedBy { it.id }

// Programs

/**
 * The replanning function is used to recompute the path of the robot
 * when the environment changes.
 * It supports two different strategies:
 * - Gossip-based replanning
 * - Leader-based replanning
 *
 * @param env the environment variables
 * @param distanceSensor the distance sensor
 * @param locationSensor the location sensor
 * @param depotsSensor the depots sensor
 */
fun Aggregate<Int>.replanning(
    env: EnvironmentVariables,
    distanceSensor: CollektiveDevice<*>,
    locationSensor: LocationSensor,
    depotsSensor: DepotsSensor,
) {
    if (!depotsSensor.alive()) {
        env["target"] = locationSensor.coordinates()
        env["replanning"] = 0
    } else {
        when (env.get("leaderBased") as Boolean) {
            true ->
                boundedElectionReplanning(
                    env,
                    distanceSensor,
                    locationSensor,
                    depotsSensor,
                )
            else ->
                gossipReplanning(
                    env,
                    distanceSensor,
                    locationSensor,
                    depotsSensor,
                )
        }
    }
    // utility function / extraction
    env["hue"] = localId // for debugging
    distanceTracking(env, locationSensor)
    env["neighbors"] = neighboring(1).fold(0) { acc, value -> acc + value }
    lastMovingTime(env, locationSensor, depotsSensor)
    if(depotsSensor.alive()) {
        breakingCycle(env, locationSensor, depotsSensor)
    }
}

private const val MAX_BOUND = 1000.0
private const val TIME_WINDOW = 5

/**
 * A function that implements the gossip-based replanning strategy.
 */
fun Aggregate<Int>.gossipReplanning(
    env: EnvironmentVariables,
    distanceSensor: CollektiveDevice<*>,
    locationSensor: LocationSensor,
    depotsSensor: DepotsSensor,
) {
    val allTasks = evolve(allTasksWithoutSource(depotsSensor)) { it }
    // all ids in the system, ever seen
    val allIds = share(setOf(localId)) { it.fold(setOf(localId)) { acc, value -> acc + value } }

    evolve(ReplanningState.createFrom(allTasks, depotsSensor)) { state ->
        val nodeFormalization = NodeFormalization(locationSensor.coordinates(), localId)

        // All task ever done (via nonStabilizingGossip)
        val allTaskDone = gossipTasksDone(state.dones.filter { it.value }.keys) // avoid to recompute task already done

        // All robots that I may see with multipath communication
        val allRobots = gossipNodeCoordinates(nodeFormalization, distanceSensor, allIds, MAX_BOUND)
        val stableCondition =
            gossipStabilityCondition(
                distanceSensor,
                state,
                allIds,
                allRobots,
            )
        when {
            // stability condition is not satisfied, recompute the path
            !stableCondition -> {
                // allocation part: recompute the path giving the new information
                val reducedTasks = allTasks.filter { it !in allTaskDone }
                val globalPlan =
                    GreedyAllocationStrategy(
                        allRobots.sortedBy { it.id },
                        reducedTasks.sortedBy { it.id },
                        depotsSensor.destinationDepot,
                    ).execute().second
                env["replanning"] = 1
                env["totalReplanning"] = (env.getOrNull<Int>("totalReplanning") ?: 0) + 1
                val myPlan = globalPlan.find { it.robot.id == localId }
                standStill(env, locationSensor) // avoid flickering
                state.copy(
                    path = myPlan?.route.orEmpty(),
                    allocations = globalPlan,
                )
            }
            else -> {
                env["replanning"] = 0
                followPlan(
                    env,
                    depotsSensor,
                    locationSensor,
                    state,
                )
            }
        }
    }
}

/**
 * A function that checks if the robots are stable in the system.
 */
fun Aggregate<Int>.gossipStabilityCondition(
    distanceSensor: CollektiveDevice<*>,
    state: ReplanningState,
    allIds: Set<Int>,
    allRobots: List<NodeFormalization>,
): Boolean {
    // Consensus part: check if the node should recompute the path
    val pathMap = state.allocations.associate { it.robot.id to it.route.map { robot -> robot.id } }
    val globalConsistency = isGlobalPathConsistent(pathMap, distanceSensor, allIds, MAX_BOUND)
    val allConsistent = areAllStable(globalConsistency, distanceSensor, allIds, MAX_BOUND)
    // Check if the robots are stable
    val areRobotStable = stableFor(allRobots.map { it.id }.toSet(), TIME_WINDOW)
    return areRobotStable && allConsistent
}

/**
 * A function that implements the bounded election replanning strategy.
 * It uses a leader-based approach to recompute the path of the robot.
 */
fun Aggregate<Int>.boundedElectionReplanning(
    env: EnvironmentVariables,
    distanceSensor: CollektiveDevice<*>,
    locationSensor: LocationSensor,
    depotsSensor: DepotsSensor,
) {
    val allTasks = allTasksWithoutSource(depotsSensor)
    val leaderId = boundedElection(MAX_BOUND, with(distanceSensor) { distances() })
    val isLeader = leaderId == localId
    env["isLeader"] =
        if (isLeader) {
            1.0
        } else {
            0.0
        }
    val distanceField = hops().map { it.toDouble() }
    val nodePosition = NodeFormalization(locationSensor.coordinates(), localId)
    val potential: Double = distanceTo(isLeader, metric = distanceField)
    // collect nodes
    val allIds = share(setOf(localId)) { it.fold(setOf(localId)) { acc, value -> acc + value } }
    val allRobotsFromLeader =
        gossipNodeCoordinates(nodePosition, distanceSensor, allIds, MAX_BOUND)
    val areRobotsStable = stableForBy(allRobotsFromLeader, TIME_WINDOW) { it.map { node -> node.id }.toSet() }
    evolve(ReplanningState.createFrom(allTasks, depotsSensor)) { state ->
        val taskEverDone = gossipTasksDone(state.dones.filter { it.value }.keys)
        env["taskEverDone"] = taskEverDone.map { it.id }
        env["taskSize"] = state.path.size
        val reducedTasks = allTasks.filter { it !in taskEverDone }
        val newPlan =
            if (!areRobotsStable && isLeader) {
                env["replanning"] = 1
                env["totalReplanning"] = (env.getOrNull<Int>("totalReplanning") ?: 0) + 1
                GreedyAllocationStrategy(
                    allRobotsFromLeader.sortedBy { it.id },
                    reducedTasks.sortedBy { it.id },
                    depotsSensor.destinationDepot,
                ).execute().second
            } else {
                env["replanning"] = 0
                state.allocations
            }
        // share
        val leaderPlan = gradientCast(isLeader, newPlan, distanceField)
        val leaderStable = gradientCast(isLeader, isLeader, distanceField) // namely, a leader may exists
        env["stable"] = leaderStable
        env["path"] = state.path.map { it.id }
        val myPlan = leaderPlan.find { it.robot.id == localId }
        if (leaderStable) {
            followPlan(
                env,
                depotsSensor,
                locationSensor,
                state.copy(
                    path = myPlan?.route ?: listOf(nodePosition, depotsSensor.destinationDepot),
                    allocations = leaderPlan,
                ),
            )
        } else {
            //standStill(env, locationSensor) // avoid flickering
            followPlan(
                env,
                depotsSensor,
                locationSensor,
                state.copy(
                    allocations = leaderPlan,
                )
            )
        }
    }
}

/**
 * Follow plan is used to follow the plan given by the replanning function.
 * It also updates the state of the robot when the task is done.
 */
fun Aggregate<Int>.followPlan(
    env: EnvironmentVariables,
    depotsSensor: DepotsSensor,
    locationSensor: LocationSensor,
    state: ReplanningState,
): ReplanningState {
    val path = state.path.drop(1) // source depot is not a task
    // find first available
    val firstAvailable = path.firstOrNull { !(state.dones[it] ?: false) }
    val selected = firstAvailable ?: state.path.last()
    if (depotsSensor.isReachLastTask(selected)) {
        env["target"] = locationSensor.coordinates()
        return state
    }
    env["target"] = locationSensor.estimateCoordinates(selected)
    env["selected"] = selected.id
    // check if the task is done
    val isDone = depotsSensor.isTaskOver(selected)
    if (isDone) {
        env["dones"] = (env.getOrNull<Int>("dones") ?: 0) + 1
    }
    val updated = state.dones.toMutableMap()
    updated[selected] = isDone
    // update the state
    return state.copy(dones = updated)
}

/**
 * A simple function that sets the target to the current position of the robot.
 * Namely, it is used to stop the robot when it is not moving.
 */
fun standStill(
    env: EnvironmentVariables,
    locationSensor: LocationSensor,
) {
    env["target"] = locationSensor.coordinates()
}
