package it.unibo.collektive.program

import it.unibo.alchemist.model.sensors.DepotsSensor
import it.unibo.alchemist.model.sensors.LocationSensor
import it.unibo.collektive.aggregate.api.Aggregate
import it.unibo.collektive.aggregate.api.neighboring
import it.unibo.collektive.alchemist.device.sensors.EnvironmentVariables
import it.unibo.formalization.Bid
import it.unibo.formalization.GreedyAllocationStrategy
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

val balanceFactor = 100
fun Aggregate<Int>.replanning(
    env: EnvironmentVariables,
    locationSensor: LocationSensor,
    depotsSensor: DepotsSensor
) {
    env["hue"] = localId // for debugging
    if(!depotsSensor.alive()) {
        env["target"] = locationSensor.coordinates()
    } else {
        val allTasks = depotsSensor.tasks.toSet() - setOf(depotsSensor.sourceDepot)
        val myPosition = evolve(locationSensor.coordinates()) { it }
        evolve(ReplanningState.createFrom(allTasks, depotsSensor)) { state ->
            /** Ever done -- check if the task is completed by one of the robot */
            val allTaskDone = everDone(state.dones)
            // find other robots
            val allRobots = neighboring(NodeFormalization(myPosition, localId)).toMap().values.toList()
            val oldViewStack = evolve(listOf<List<NodeFormalization>>()) { listOf(allRobots).plus(it).take(2) }
            val oldView = oldViewStack.last()
            val (assigned, path) = if(oldView.map { it.id }.toSet() != allRobots.map { it.id }.toSet()) {
                val allocation = GreedyAllocationStrategy(
                    allRobots.toList(), allTasks.toList(), NodeFormalization(myPosition, localId), depotsSensor.destinationDepot
                )
                val result = allocation.execute()
                // take mine
                val myPlan = result.find { it.robot.id == localId }
                val myTasks = myPlan?.route?.filterNot { it == depotsSensor.sourceDepot } ?: emptyList()
                val myPath = myPlan?.route ?: emptyList()
                (myTasks).toSet() to myPath
            } else {
                state.assigned to state.path
            }


            followPlan(
                env,
                depotsSensor,
                locationSensor,
                state.copy(
                    dones = allTaskDone,
                    assigned = assigned,
                    path = path
                )
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
