package it.unibo.formalization

import it.unibo.formalization.GeometryUtils.calculateRouteCost
import it.unibo.formalization.RoutingHeuristics.computeMarginalCost

interface TaskAllocation {
    val robots: List<Node>
    val tasks: List<Node>
    val startDepot: Node
    val endDepot: Node
    val maxRouteCost: Double
    fun execute(): List<RobotAllocationResult>
}

class GreedyAllocationStrategy(
    val robots: List<Node>,
    val tasks: List<Node>,
    val startDepot: Node,
    val endDepot: Node,
    val maxRouteCost: Double = Double.MAX_VALUE  // Default to no limit if not specified
) {
    /**
     * Executes the distributed multi-agent task allocation algorithm.
     * Returns the final allocation results for each robot.
     */
    fun execute(): List<RobotAllocationResult> {
        // Initialize robot states
        val robotStates = robots.map { robot ->
            val initialRoute = mutableListOf(robot, endDepot)
            val initialCost = GeometryUtils.calculateRouteCost(initialRoute)
            RobotAgentState(
                robot = robot,
                route = initialRoute,
                routeCost = initialCost
            )
        }

        val unassignedTasks = tasks.toMutableList()
        var tasksAssignedThisIteration: Boolean

        // Main allocation loop
        do {
            tasksAssignedThisIteration = false

            // Create a mapping of task to bids from all robots
            val taskBids = mutableMapOf<Node, MutableList<Bid>>()

            // Step 1: Each robot computes bids for all unassigned tasks
            for (robotState in robotStates) {
                for (task in unassignedTasks) {
                    val marginalCost = computeMarginalCost(robotState.route, task)

                    // Check if adding this task would exceed the maximum route cost
                    val potentialRouteCost = robotState.routeCost + marginalCost
                    if (potentialRouteCost <= maxRouteCost) {
                        val bid = Bid(task, robotState.robot.id, marginalCost)

                        // Add the bid to the corresponding task's bid list
                        if (task !in taskBids) {
                            taskBids[task] = mutableListOf()
                        }
                        taskBids[task]?.add(bid)
                    }
                    // If potential cost exceeds max, robot doesn't bid on this task
                }
            }

            // Step 2: For each task, find the best bid and assign to the corresponding robot
            val tasksToRemove = mutableListOf<Node>()

            // Prioritize tasks with the largest difference between best and second-best bid
            val taskPriorities = taskBids.mapValues { (_, bids) ->
                if (bids.size >= 2) {
                    val sortedBids = bids.sortedBy { it.cost }
                    sortedBids[1].cost - sortedBids[0].cost  // Difference between best and second best
                } else {
                    0.0  // Only one bid, no difference
                }
            }

            // Sort tasks by priority (largest difference first)
            val sortedTasks = taskBids.keys.sortedByDescending { taskPriorities[it] }

            // Now assign one task per robot in this iteration, if possible
            val assignedRobots = mutableSetOf<Int>()

            for (task in sortedTasks) {
                val bids = taskBids[task] ?: continue
                if (bids.isEmpty()) continue // Skip if no robot can take this task

                // Sort bids by cost (lowest first)
                val sortedBids = bids.sortedBy { it.cost }

                // Find the best robot that hasn't been assigned a task in this iteration
                val bestBid = sortedBids.firstOrNull { it.robot !in assignedRobots }
                    ?: continue  // Skip if all potential robots already got a task this iteration

                // Find the robot state for this robot
                val robotState = robotStates.find { it.robot.id == bestBid.robot } ?: continue

                // Double-check the maximum cost constraint with the latest route
                val updatedMarginalCost = computeMarginalCost(robotState.route, task)
                if (robotState.routeCost + updatedMarginalCost > maxRouteCost) {
                    continue  // Skip this assignment if it would now exceed the maximum cost
                }

                // Assign the task to the robot
                robotState.assignedTasks.add(task)
                tasksToRemove.add(task)
                assignedRobots.add(bestBid.robot)
                tasksAssignedThisIteration = true
            }

            // Remove assigned tasks from unassigned list
            unassignedTasks.removeAll(tasksToRemove)

            // Step 3: Local Route Update - Re-optimize routes for each robot
            for (robotState in robotStates) {
                val optimizedRoute = RoutingHeuristics.solveLocalRouting(
                    robotState.assignedTasks,
                    robotState.robot,
                    endDepot
                )

                val optimizedRouteCost = calculateRouteCost(optimizedRoute)

                // Only update if optimized route doesn't exceed maximum cost
                if (optimizedRouteCost <= maxRouteCost) {
                    robotState.route = optimizedRoute.toMutableList()
                    robotState.routeCost = optimizedRouteCost
                } else {
                    println("Warning: Optimized route for Robot ${robotState.robot} exceeds maximum cost.")
                }
            }

        } while (tasksAssignedThisIteration && unassignedTasks.isNotEmpty())

        // Convert robot states to final results
        return robotStates.map { state ->
            RobotAllocationResult(
                robot = state.robot,
                route = state.route,
                routeCost = state.routeCost
            )
        }
    }
}