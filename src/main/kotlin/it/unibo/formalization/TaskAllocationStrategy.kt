package it.unibo.formalization

import it.unibo.formalization.GeometryUtils.calculateRouteCost
import it.unibo.formalization.RoutingHeuristics.computeMarginalCost

class GreedyAllocationStrategy(
    val robots: List<Node>,
    val tasks: Collection<Node>,
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
            for (robotState in robotStates.sortedBy { it.robot.id }) {
                for (task in unassignedTasks.sortedBy { it.id }) {
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
                robotState.route = optimizedRoute.toMutableList()
                robotState.routeCost = optimizedRouteCost
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

class ImprovedAllocationStrategy(
    val robots: List<Node>,
    val tasks: Collection<Node>,
    val startDepot: Node,
    val endDepot: Node,
    val maxRouteCost: Double = Double.MAX_VALUE
) {
    /**
     * Executes an optimized distributed multi-agent task allocation algorithm.
     * Returns the final allocation results for each robot.
     */
    fun execute(): List<RobotAllocationResult> {
        // Initialize robot states with pre-calculated insertion costs
        val robotStates = robots.map { robot ->
            val initialRoute = mutableListOf(robot, endDepot)
            val initialCost = GeometryUtils.calculateRouteCost(initialRoute)
            RobotAgentState(
                robot = robot,
                route = initialRoute,
                routeCost = initialCost
            )
        }

        // Pre-compute all initial insertion costs for each (robot, task) pair
        val insertionCostCache = precomputeInsertionCosts(robotStates, tasks).toMutableMap()

        // Create a list of unassigned tasks
        val unassignedTasks = tasks.toMutableSet()

        // Main allocation loop - continues until no more tasks can be assigned
        while (unassignedTasks.isNotEmpty()) {
            var bestTask: Node? = null
            var bestRobot: RobotAgentState? = null
            var bestCost = Double.MAX_VALUE
            var bestInsertionPoint = 0

            // Find the globally best (robot, task) pair with lowest insertion cost
            for (robotState in robotStates) {
                for (task in unassignedTasks) {
                    val cacheKey = Pair(robotState.robot.id, task.id)
                    val insertionData = insertionCostCache[cacheKey] ?: continue

                    // Skip if adding this task would exceed the maximum route cost
                    if (robotState.routeCost + insertionData.cost > maxRouteCost) {
                        continue
                    }

                    // Update best pair if this insertion is better
                    if (insertionData.cost < bestCost) {
                        bestCost = insertionData.cost
                        bestTask = task
                        bestRobot = robotState
                        bestInsertionPoint = insertionData.position
                    }
                }
            }

            // If no task can be assigned, break the loop
            if (bestTask == null || bestRobot == null) {
                break
            }

            // Assign the task to the best robot
            bestRobot.assignedTasks.add(bestTask)
            bestRobot.route.add(bestInsertionPoint, bestTask)
            bestRobot.routeCost += bestCost
            unassignedTasks.remove(bestTask)

            // Update insertion costs for the affected robot
            updateInsertionCosts(bestRobot, unassignedTasks, insertionCostCache)
        }

        // Perform a single final route optimization pass
        for (robotState in robotStates) {
            if (robotState.assignedTasks.isNotEmpty()) {
                val optimizedRoute = RoutingHeuristics.solveLocalRouting(
                    robotState.assignedTasks,
                    robotState.robot,
                    endDepot
                )

                val optimizedRouteCost = calculateRouteCost(optimizedRoute)
                robotState.route = optimizedRoute.toMutableList()
                robotState.routeCost = optimizedRouteCost
            }
        }

        // Convert robot states to final results
        return robotStates.map { state ->
            RobotAllocationResult(
                robot = state.robot,
                route = state.route,
                routeCost = state.routeCost
            )
        }
    }

    /**
     * Pre-computes insertion costs for all (robot, task) pairs
     */
    private fun precomputeInsertionCosts(
        robotStates: List<RobotAgentState>,
        tasks: Collection<Node>
    ): Map<Pair<Int, Int>, InsertionData> {
        val cache = mutableMapOf<Pair<Int, Int>, InsertionData>()

        for (robotState in robotStates) {
            for (task in tasks) {
                val insertionData = findBestInsertion(robotState.route, task)
                cache[Pair(robotState.robot.id, task.id)] = insertionData
            }
        }

        return cache
    }

    /**
     * Updates insertion costs for a robot after a task has been assigned
     */
    private fun updateInsertionCosts(
        robotState: RobotAgentState,
        remainingTasks: Set<Node>,
        cache: MutableMap<Pair<Int, Int>, InsertionData>
    ) {
        for (task in remainingTasks) {
            val insertionData = findBestInsertion(robotState.route, task)
            cache[Pair(robotState.robot.id, task.id)] = insertionData
        }
    }

    /**
     * Finds the best position to insert a task in a route and calculates the insertion cost
     */
    private fun findBestInsertion(route: List<Node>, task: Node): InsertionData {
        var bestPosition = 1  // Start with position 1 (after the robot, before endDepot)
        var bestCost = Double.MAX_VALUE

        // Try inserting the task at each position in the route (except position 0, which is the robot)
        for (i in 1 until route.size) {
            val newRoute = route.toMutableList()
            newRoute.add(i, task)

            val oldCost = if (i == 1) {
                // If inserting at position 1, only need to calculate cost between robot and endDepot
                GeometryUtils.travelCost(route[0], route[1])
            } else {
                // Otherwise, calculate cost between positions i-1 and i
                GeometryUtils.travelCost(route[i-1], route[i])
            }

            val newCost = GeometryUtils.travelCost(newRoute[i-1], newRoute[i]) +
                    GeometryUtils.travelCost(newRoute[i], newRoute[i+1])

            val insertionCost = newCost - oldCost

            if (insertionCost < bestCost) {
                bestCost = insertionCost
                bestPosition = i
            }
        }

        return InsertionData(bestCost, bestPosition)
    }

    /**
     * Helper class to store insertion cost and position
     */
    data class InsertionData(val cost: Double, val position: Int)
}
