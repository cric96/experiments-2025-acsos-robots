package it.unibo.alchemist.model.global

import it.unibo.alchemist.model.Environment
import it.unibo.alchemist.model.Node
import it.unibo.alchemist.model.Position
import it.unibo.alchemist.model.TimeDistribution

class MinimumMakespanAllocationStrategy<T, P : Position<P>>(
    environment: Environment<T, P>,
    timeDistribution: TimeDistribution<T>
) : InitialAllocationStrategy<T, P>(environment, timeDistribution) {

    override fun allocate(
        robots: List<Node<T>>,
        tasks: List<Node<T>>,
        sourceDepot: Node<T>,
        targetDepot: Node<T>
    ): List<Allocation<T>> {
        // Simple greedy allocation to minimize makespan
        // We assign tasks to robots one by one, always choosing the robot with the least allocated work

        // Calculate distances between nodes
        val distanceMatrix = computeDistanceMatrix(robots, tasks, sourceDepot, targetDepot)

        // Initialize empty allocation for each robot
        val robotToTasks = HashMap<Node<T>, MutableList<Node<T>>>()
        val robotToMakespan = HashMap<Node<T>, Double>()

        robots.forEach { robot ->
            robotToTasks[robot] = mutableListOf()
            // Initial makespan is the distance from source to target depot (robot starts at source)
            robotToMakespan[robot] = distanceMatrix.getValue(Pair(sourceDepot, targetDepot))
        }

        // Sort tasks by some heuristic (e.g., farthest from source depot first)
        val sortedTasks = tasks.sortedByDescending { task ->
            distanceMatrix.getValue(Pair(sourceDepot, task))
        }

        // Assign each task to the robot that will have the minimum resulting makespan
        for (task in sortedTasks) {
            var bestRobot: Node<T>? = null
            var minMakespan = Double.MAX_VALUE

            for (robot in robots) {
                val currentPath = robotToTasks[robot]!!
                // Calculate new makespan if we add this task
                val newMakespan = calculateNewMakespan(
                    sourceDepot, targetDepot, robot, currentPath, task, distanceMatrix
                )

                if (newMakespan < minMakespan) {
                    minMakespan = newMakespan
                    bestRobot = robot
                }
            }

            // Assign task to the best robot
            bestRobot?.let {
                robotToTasks[it]!!.add(task)
                robotToMakespan[it] = minMakespan
            }
        }

        // Create allocations from our mapping
        return robots.map { robot ->
            Allocation(robot, (robotToTasks[robot] ?: emptyList()).asReversed() + listOf(targetDepot))
        }
    }

    private fun computeDistanceMatrix(
        robots: List<Node<T>>,
        tasks: List<Node<T>>,
        sourceDepot: Node<T>,
        targetDepot: Node<T>
    ): Map<Pair<Node<T>, Node<T>>, Double> {
        val allNodes = HashSet<Node<T>>()
        allNodes.addAll(robots)
        allNodes.addAll(tasks)
        allNodes.add(sourceDepot)
        allNodes.add(targetDepot)

        val distanceMatrix = HashMap<Pair<Node<T>, Node<T>>, Double>()

        for (node1 in allNodes) {
            for (node2 in allNodes) {
                if (node1 != node2) {
                    val pos1 = environment.getPosition(node1)
                    val pos2 = environment.getPosition(node2)
                    val distance = pos1.distanceTo(pos2)
                    distanceMatrix[Pair(node1, node2)] = distance
                }
            }
        }

        return distanceMatrix
    }

    private fun calculateNewMakespan(
        sourceDepot: Node<T>,
        targetDepot: Node<T>,
        robot: Node<T>,
        currentPath: List<Node<T>>,
        newTask: Node<T>,
        distanceMatrix: Map<Pair<Node<T>, Node<T>>, Double>
    ): Double {
        // Calculate total path length with the new task inserted
        var totalDistance = 0.0

        // Distance from source to first task
        val firstTask = if (currentPath.isEmpty()) newTask else currentPath.first()
        totalDistance += distanceMatrix.getValue(Pair(sourceDepot, firstTask))

        // Distances between tasks
        var prevTask = firstTask
        for (task in if (currentPath.isEmpty()) emptyList() else currentPath.subList(1, currentPath.size)) {
            totalDistance += distanceMatrix.getValue(Pair(prevTask, task))
            prevTask = task
        }

        // Add new task to the end
        if (currentPath.isNotEmpty()) {
            totalDistance += distanceMatrix.getValue(Pair(prevTask, newTask))
        }

        // Distance from last task to target depot
        totalDistance += distanceMatrix.getValue(Pair(newTask, targetDepot))

        return totalDistance
    }
}