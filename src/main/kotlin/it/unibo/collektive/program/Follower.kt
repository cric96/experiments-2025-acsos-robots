package it.unibo.collektive.program

import it.unibo.alchemist.model.Node
import it.unibo.alchemist.model.molecules.SimpleMolecule
import it.unibo.alchemist.model.sensors.DepotsSensor
import it.unibo.alchemist.model.sensors.LocationSensor
import it.unibo.collektive.aggregate.api.Aggregate
import it.unibo.collektive.aggregate.api.DelicateCollektiveApi
import it.unibo.collektive.aggregate.api.InMemory
import it.unibo.collektive.alchemist.device.sensors.EnvironmentVariables
import it.unibo.formalization.GreedyAllocationStrategy

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
        env["selected"] = it.id
    }
}

@OptIn(DelicateCollektiveApi::class)
fun Aggregate<Int>.runtimeReconfiguration(env: EnvironmentVariables, locationSensor: LocationSensor, depotsSensor: DepotsSensor) {
    val tasks = env.getOrNull<List<Node<Any>>>("tasks") ?: emptyList()
    val allNeighborhood = neighboring(tasks, InMemory).toMap()
    val mockNeighboring = neighboring(listOf<Node<Any>>(), InMemory)
    val remember = evolve(mockNeighboring.toMap()) { allNeighborhood.toMap() }
    val myPosition = locationSensor.coordinates().let { Pair(it.first, it.second) }
    val robots = neighboring(myPosition, InMemory).toMap().toSortedMap().values.toList()
    // flatten on all the tasks
    val start = depotsSensor.getSourceDepot
    val destination = depotsSensor.getDestinationDepot
    val allTasks = depotsSensor.tasks
    val allocator = GreedyAllocationStrategy(
        robots,
        allTasks,
        start,
        destination,
    )
    val result = allocator.execute()
    result.find { it.robot == myPosition }?.let { myTasks ->
        // remove source
        val path = myTasks.route.filterNot { it == start }
        env["raw"] = path
        env["tasks"] = path.map { depotsSensor.taskNode(it) }
    }
    followTasks(env, locationSensor)

}