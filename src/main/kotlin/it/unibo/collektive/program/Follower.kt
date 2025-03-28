package it.unibo.collektive.program

import it.unibo.alchemist.model.Node
import it.unibo.alchemist.model.molecules.SimpleMolecule
import it.unibo.alchemist.model.sensors.LocationSensor
import it.unibo.collektive.aggregate.api.Aggregate
import it.unibo.collektive.alchemist.device.sensors.EnvironmentVariables

fun isTaskDone(node: Node<Any>): Boolean {
    val isDone = node.contents[SimpleMolecule("isDone")]
    return isDone != null && isDone != 0.0
}

fun isTarget(node: Node<Any>): Boolean =
    node.contents.getOrDefault(SimpleMolecule("destination"), false) as Boolean


fun Aggregate<Int>.followTasks(env: EnvironmentVariables, locationSensor: LocationSensor) {
    val tasks = env.getOrNull<List<Node<Any>>>("tasks")
    val findFirstAvailableTask = tasks?.firstOrNull { task -> !isTaskDone(task) || isTarget(task) }
    findFirstAvailableTask?.let {
        env["target"] = locationSensor.estimateCoordinates(it)
    }

}
