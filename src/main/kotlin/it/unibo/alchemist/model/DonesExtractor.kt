package it.unibo.alchemist.model

import it.unibo.alchemist.boundary.Extractor
import it.unibo.alchemist.model.molecules.SimpleMolecule

class DonesExtractor : Extractor<Double> {
    var taskList: List<Node<*>>? = null
    override val columnNames: List<String>
        get() = listOf("isDonePercentage")

    override fun <T> extractData(
        environment: Environment<T, *>,
        reaction: Actionable<T>?,
        time: Time,
        step: Long,
    ): Map<String, Double> {
        // take the tasks
        val doneMolecule = SimpleMolecule("isDone")
        taskList = taskList ?: environment.nodes
            .filter { it.contains(doneMolecule) }
        val currentDones: Double =
            taskList
                ?.map { it.contents[doneMolecule] as Double }
                ?.sum()
                .let { it?.div(taskList?.size ?: 1) }
                ?: 0.0
        return mapOf("isDonePercentage" to currentDones)
    }
}
