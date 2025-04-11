package it.unibo.collektive.program

import it.unibo.alchemist.model.sensors.DepotsSensor
import it.unibo.alchemist.model.sensors.LocationSensor
import it.unibo.collektive.aggregate.api.Aggregate
import it.unibo.collektive.alchemist.device.sensors.EnvironmentVariables
import it.unibo.formalization.Node as NodeFormalization

fun Aggregate<Int>.followTasks(env: EnvironmentVariables, locationSensor: LocationSensor, depotsSensor: DepotsSensor) {
    val tasks = env.get<List<NodeFormalization>>("tasks")
    evolve(tasks.map { false }.toMutableList()) { dones ->
        val firstIndexFalse = dones.indexOfFirst { !it }
        val task = tasks[firstIndexFalse]
        env["target"] = locationSensor.estimateCoordinates(task)
        env["selected"] = task.id
        val isDone = depotsSensor.isTaskOver(task)
        // update the task done
        dones[firstIndexFalse] = isDone
        dones
    }
}
