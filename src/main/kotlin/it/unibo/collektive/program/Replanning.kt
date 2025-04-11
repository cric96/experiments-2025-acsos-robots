package it.unibo.collektive.program

import it.unibo.alchemist.collektive.device.CollektiveDevice
import it.unibo.alchemist.model.sensors.DepotsSensor
import it.unibo.alchemist.model.sensors.LocationSensor
import it.unibo.collektive.aggregate.api.Aggregate
import it.unibo.collektive.aggregate.api.share
import it.unibo.collektive.alchemist.device.sensors.EnvironmentVariables
import it.unibo.collektive.field.Field.Companion.fold
import it.unibo.collektive.stdlib.spreading.gossipMax
import it.unibo.collektive.stdlib.spreading.multiGradientCast
import it.unibo.collektive.stdlib.spreading.nonStabilizingGossip
import it.unibo.formalization.GreedyAllocationStrategy
import it.unibo.formalization.RobotAllocationResult
import it.unibo.formalization.Node as NodeFormalization

/** Utils **/
fun allTasksWithoutSource(depotsSensor: DepotsSensor): List<NodeFormalization> {
    return (depotsSensor.tasks.toSet() - setOf(depotsSensor.sourceDepot)).toList().sortedBy { it.id }
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
fun Aggregate<Int>.everDone(dones: Set<NodeFormalization>): Set<NodeFormalization> {
    return nonStabilizingGossip(dones) { left, right -> left + right }
}

fun <E> Aggregate<Int>.history(value: E, size: Int): List<E> = evolve(listOf(value)) { listOf(value).plus(it).take(size) }

fun <E> Aggregate<Int>.stableFor(value: E, size: Int): Boolean {
    val history = history(value, size)
    return history.size > 2 && history.all { it == value }
}
fun Aggregate<Int>.expandBubble(source: NodeFormalization, distanceSensor: CollektiveDevice<*>, maxBubbleSize: Double = 1000.0): List<NodeFormalization> {
    val allIds = share(setOf(localId)) { it.fold(setOf(localId)){ acc, value -> acc + value } }
    return multiGradientCast(
        sources = allIds,
        local = source to 0.0,
        metric = with(distanceSensor) { distances() },
        accumulateData = { fromSource, _, data -> data.first to fromSource }
    ).values.filter { it.second < maxBubbleSize }.map { it.first }.distinct()
}

fun Aggregate<Int>.areAllStable(stable: Boolean, distanceSensor: CollektiveDevice<*>): Boolean {
    val allIds = share(setOf(localId)) { it.fold(setOf(localId)){ acc, value -> acc + value } }
    val allRobots = multiGradientCast(
        sources = allIds,
        local = stable,
        metric = with(distanceSensor) { distances() },
        accumulateData = { fromSource, _, data -> data }
    ).values
    return allRobots.all { it }
}


fun Aggregate<Int>.replanning(
    env: EnvironmentVariables,
    distanceSensor: CollektiveDevice<*>,
    locationSensor: LocationSensor,
    depotsSensor: DepotsSensor
) {
    env["hue"] = localId // for debugging
    if(!depotsSensor.alive()) {
        env["target"] = locationSensor.coordinates()
    } else {
        val allTasks =  evolve(allTasksWithoutSource(depotsSensor)) { it }
        val myPosition = evolve(locationSensor.coordinates()) { it }
        val globalClock = gossipMax(evolve(0) { it + 1})
        env["clock"] = globalClock

        evolve(ReplanningState.createFrom(allTasks, depotsSensor)) { state ->
            val globalConsistency = isGlobalPathConsistent(state.allocations, distanceSensor)
            /** Ever done -- check if the task is completed by one of the robot */
            val allTaskDone = everDone(state.dones.filter { it.value }.keys) // avoid to recompute task already done
            /** All robots that I may see with multipath communication */
            val allRobots = expandBubble(NodeFormalization(locationSensor.coordinates(), localId), distanceSensor)
            /** Check if the robots are stable */
            val areRobotsStable = stableFor(allRobots.map { it.id }.toSet(), 10)
            /** Check if the robots are stable */
            val areAllStable = areAllStable(areRobotsStable, distanceSensor)
            val stableCondition = areAllStable && globalConsistency
            when {
                // stability condition is not satisfied, recompute the path
                !stableCondition -> {
                    val reducedTasks = allTasks.filter { it !in allTaskDone }
                    val allocation = GreedyAllocationStrategy(
                        allRobots.sortedBy { it.id },
                        reducedTasks,
                        NodeFormalization(locationSensor.coordinates(), localId),
                        depotsSensor.destinationDepot
                    )
                    val globalPlan = allocation.execute()
                    val myPlan = globalPlan.find { it.robot.id == localId }
                    standStill(env, locationSensor)
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
}

fun standStill(env: EnvironmentVariables, locationSensor: LocationSensor) {
    env["target"] = locationSensor.coordinates()
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

/** Sanity checks **/
fun Aggregate<Int>.isGlobalPathConsistent(paths: List<RobotAllocationResult>, distanceSensor: CollektiveDevice<*>): Boolean {
    val allIds = share(setOf(localId)) { it.fold(setOf(localId)){ acc, value -> acc + value } }
    val allRobots = multiGradientCast(
        sources = allIds,
        local = paths,
        metric = with(distanceSensor) { distances() },
        accumulateData = { fromSource, _, data -> data }
    ).values
    val myPath = paths.find { it.robot.id == localId }
    // check that, everywhere, the path is the same for this robot
    val everyoneSeeMe = allRobots.filter { it -> it.find { it.robot.id == localId } != null }
    // check that, everywhere, the path for me is the same
    val isPathsConsistent = everyoneSeeMe.map { it.find { it.robot.id == localId }?.route.orEmpty() }
    // find the one that are different
    return everyoneSeeMe.size == allRobots.size && isPathsConsistent.all { it == myPath?.route }
}
