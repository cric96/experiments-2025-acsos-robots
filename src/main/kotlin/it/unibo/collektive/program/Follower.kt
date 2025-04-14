package it.unibo.collektive.program

import it.unibo.alchemist.model.sensors.DepotsSensor
import it.unibo.alchemist.model.sensors.LocationSensor
import it.unibo.collektive.aggregate.api.Aggregate
import it.unibo.collektive.alchemist.device.sensors.EnvironmentVariables
import it.unibo.formalization.Node as NodeFormalization

fun Aggregate<Int>.followTasks(env: EnvironmentVariables, locationSensor: LocationSensor, depotsSensor: DepotsSensor) {
    env["hue"] = localId
    if(depotsSensor.alive()) {
        val tasks = env.get<List<NodeFormalization>>("tasks")
        val task = tasks[0]
        env["target"] = locationSensor.estimateCoordinates(task)
        env["selected"] = task.id
        if(depotsSensor.isTaskOver(task)) {
            env["tasks"] = tasks.drop(1)
        }
    } else {
        env["target"] = locationSensor.coordinates()
    }
}
