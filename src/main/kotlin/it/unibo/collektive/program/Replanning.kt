package it.unibo.collektive.program

import it.unibo.alchemist.model.sensors.DepotsSensor
import it.unibo.alchemist.model.sensors.LocationSensor
import it.unibo.collektive.aggregate.api.Aggregate
import it.unibo.collektive.aggregate.api.Aggregate.Companion.neighboring
import it.unibo.collektive.alchemist.device.sensors.EnvironmentVariables
import it.unibo.formalization.Bid
import it.unibo.formalization.RoutingHeuristics
import it.unibo.formalization.Node as NodeFormalization

data class ReplanningState(
    val dones: Map<NodeFormalization, Boolean>,
    val assigned: Set<NodeFormalization>,
    val path: List<NodeFormalization>
) {
    companion object {
        fun createFrom(tasks: Set<NodeFormalization>, depotsSensor: DepotsSensor): ReplanningState {
            val assigned = setOf<NodeFormalization>()
            val path = listOf(depotsSensor.sourceDepot, depotsSensor.destinationDepot)
            return ReplanningState(
                dones = tasks.associate { it to false }.toMap(),
                assigned = assigned,
                path = path
            )
        }
    }
}

fun Aggregate<Int>.everDone(dones: Map<NodeFormalization, Boolean>): Map<NodeFormalization, Boolean> {
    val others = neighboring(dones)
    val neighbourhood = others.toMap()
    val updated = neighbourhood.values.reduce {
            acc, v -> acc.map { (key, value) -> key to (value || v[key] ?: false) }.toMap()
    }
    return updated
}

val balanceFactor = 10
fun Aggregate<Int>.fullRuntime(
    env: EnvironmentVariables,
    locationSensor: LocationSensor,
    depotsSensor: DepotsSensor
) {
    if(!depotsSensor.isAgent()) { return }
    env["hue"] = localId // for debugging
    if(!depotsSensor.alive()) {
        env["target"] = locationSensor.coordinates()
    } else {
        val allTasks = depotsSensor.tasks.toSet()
        val myPosition = locationSensor.coordinates()

        evolve(ReplanningState.createFrom(allTasks, depotsSensor)) { state ->
            // compute the marginal cost for each task (unassigned)
            val assigned = state.assigned
            val path = state.path
            val dones = state.dones
            val donesGossip = everDone(dones)
            val adaptMarginalCostByAssignedFactor = 1 + assigned.size * balanceFactor
            val unassigned = allTasks - assigned
            val sameTasksInField = neighboring(assigned).toMap().map { (key, tasks) -> tasks.map { key to it }}.flatten()
            val sameTasksGroupById = sameTasksInField.groupBy { it.second.id }
            // for each task, take the one with the lowest node id
            val takeWinner = sameTasksGroupById.map { (_, node) -> node.minBy { it.first } }
            val takeTaskToRemove = takeWinner.filter { it.first != localId }
            // remove the tasks from the path
            val pathWithoutTasks = path.filterNot { it in takeTaskToRemove.map { it.second } }

            val marginal = (allTasks - dones.filter { it.value }.keys).map { task ->
                task to RoutingHeuristics.computeMarginalCost(pathWithoutTasks, task) * adaptMarginalCostByAssignedFactor
            }.map { (task, cost) -> Bid(task, NodeFormalization(myPosition, localId), cost) }
            val allBids = neighboring(marginal).toMap().values.flatten().groupBy { it.task }
            val taskBids = mutableMapOf<NodeFormalization, MutableList<Bid>>()
            // associate all the bids to the tasks
            allBids.forEach { (task, bids) ->
                taskBids[task] = bids.toMutableList()
            }

            // Calculate task priorities based on difference between best and second-best bid
            val assignedLocal = (assigned - takeTaskToRemove.map { it.second }.toSet()).toMutableSet()
            // Find the highest priority task I can win
            for ((task, bids) in taskBids) {
                if (bids.isEmpty()) continue // Skip if no robot can take this task
                // Sort bids by cost (lowest first) and then by robot ID for tie-breaking
                val sortedBids = bids.sortedWith(compareBy<Bid> { it.cost }.thenBy { it.robot.id })

                // Find the best bid
                val bestBid = sortedBids.first()
                // Check if I can win this task
                if (bestBid.robot.id == localId) {
                    assignedLocal.add(task)
                    break // Exit after winning one task
                }
            }
            val filterDones = assignedLocal - dones.filter { it.value }.keys
            // Store the updated path in environment variables
            val updatedPath = RoutingHeuristics.solveLocalRouting(filterDones.toSet(), NodeFormalization(myPosition, localId), depotsSensor.destinationDepot)
            env["raw"] = updatedPath.map { it.id }

            val updated = state.copy(dones = donesGossip, assigned = assignedLocal, path = updatedPath)
            followPlan(
                env,
                depotsSensor,
                locationSensor,
                updated
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
