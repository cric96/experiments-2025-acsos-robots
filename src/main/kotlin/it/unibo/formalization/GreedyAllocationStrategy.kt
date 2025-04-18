package it.unibo.formalization

import it.unibo.formalization.GeometryUtils.calculateRouteCost
import it.unibo.formalization.RoutingHeuristics.computeMarginalCost
import kotlin.system.measureTimeMillis

/**
 * GreedyAllocationStrategy implements a distributed multi-agent task allocation algorithm.
 * It assigns tasks to robots based on the bids they make for each task.
 * The algorithm iteratively collects bids, assigns tasks to robots, and optimizes their routes.
 * @param robots List of robots available for task allocation.
 * @param tasks Collection of tasks to be allocated.
 * @param endDepot The depot where robots return after completing their tasks.
 * @param maxRouteCost Maximum cost allowed for a robot's route.
 */
class GreedyAllocationStrategy(
    private val robots: List<Node>,
    private val tasks: Collection<Node>,
    private val endDepot: Node,
    private val maxRouteCost: Double = Double.MAX_VALUE,
) {
    /**
     * Executes the distributed multi-agent task allocation algorithm.
     * Returns the final allocation results for each robot.
     */
    fun execute(): Pair<Long, List<RobotAllocationResult>> {
        val robotStates = initializeRobotStates()
        val unassignedTasks = tasks.toMutableList()
        val time =
            measureTimeMillis {
                while (unassignedTasks.isNotEmpty()) {
                    val taskBids = collectBidsForTasks(robotStates, unassignedTasks)

                    // Exit if no robot can bid on any remaining task
                    if (taskBids.values.all { it.isEmpty() }) break

                    val assignmentResults = assignTasksToRobots(taskBids, robotStates)

                    // Exit if no tasks were assigned this iteration
                    if (assignmentResults.anyTaskAssigned) {
                        // Remove assigned tasks
                        unassignedTasks.removeAll(assignmentResults.assignedTasks)

                        // Re-optimize routes for each robot
                        optimizeRobotRoutes(robotStates)
                    }
                }
            }
        return time to convertToFinalResults(robotStates)
    }

    private fun initializeRobotStates(): List<RobotAgentState> =
        robots.map { robot ->
            val initialRoute = mutableListOf(robot, endDepot)
            RobotAgentState(
                robot = robot,
                route = initialRoute,
                routeCost = calculateRouteCost(initialRoute),
            )
        }

    private fun collectBidsForTasks(
        robotStates: List<RobotAgentState>,
        unassignedTasks: List<Node>,
    ): Map<Node, List<Bid>> =
        unassignedTasks.associateWith { task ->
            robotStates
                .mapNotNull { state ->
                    val marginalCost = computeMarginalCost(state.route, task)
                    if (state.routeCost + marginalCost <= maxRouteCost) {
                        Bid(task, state.robot.id, marginalCost)
                    } else {
                        null
                    }
                }.sortedBy { it.cost }
        }

    private data class AssignmentResult(
        val assignedTasks: List<Node>,
        val anyTaskAssigned: Boolean,
    )

    private fun assignTasksToRobots(
        taskBids: Map<Node, List<Bid>>,
        robotStates: List<RobotAgentState>,
    ): AssignmentResult {
        // Calculate task priorities (difference between best and second-best bids)
        val taskPriorities = calculateTaskPriorities(taskBids)

        // Sort tasks by priority (largest difference first)
        val sortedTasks = taskBids.keys.sortedByDescending { taskPriorities[it] }

        val assignedRobots = mutableSetOf<Int>()
        val tasksToRemove = mutableListOf<Node>()
        var assignedAny = false
        val taskWithBids = sortedTasks.mapNotNull { task ->
            taskBids[task]?.takeIf { it.isNotEmpty() }?.let { nonEmptyBids ->
                task to nonEmptyBids
            }
        }

        for ((task, bids) in taskWithBids) { // Destructuring declaration for Pair(task, bids)

            // Find best robot that hasn't been assigned a task this iteration
            // Use let to handle the case where no suitable bid is found (replaces a continue)
            bids.firstOrNull { it.robot !in assignedRobots }?.let { bestBid ->

                // Find the corresponding robot state
                // Use let to handle the case where the robot state isn't found (replaces a continue)
                robotStates.find { it.robot.id == bestBid.robot }?.let { robotState ->

                    // Verify constraint with current route
                    val updatedCost = computeMarginalCost(robotState.route, task)

                    // Check the constraint (replaces the final continue)
                    // Only proceed with assignment if the constraint is met
                    if (robotState.routeCost + updatedCost <= maxRouteCost) {
                        // Assign task
                        robotState.assignedTasks.add(task)
                        tasksToRemove.add(task)
                        assignedRobots.add(bestBid.robot)
                        assignedAny = true
                    }
                }
            }
        }
        return AssignmentResult(tasksToRemove, assignedAny)
    }

    private fun calculateTaskPriorities(taskBids: Map<Node, List<Bid>>): Map<Node, Double> =
        taskBids.mapValues { (_, bids) ->
            if (bids.size >= 2) bids[1].cost - bids[0].cost else 0.0
        }

    private fun optimizeRobotRoutes(robotStates: List<RobotAgentState>) {
        robotStates.forEach { state ->
            val optimizedRoute =
                RoutingHeuristics.solveLocalRouting(
                    state.assignedTasks,
                    state.robot,
                    endDepot,
                )

            state.route = optimizedRoute.toMutableList()
            state.routeCost = calculateRouteCost(optimizedRoute)
        }
    }

    private fun convertToFinalResults(robotStates: List<RobotAgentState>): List<RobotAllocationResult> =
        robotStates.map { state ->
            RobotAllocationResult(
                robot = state.robot,
                route = state.route,
                routeCost = state.routeCost,
            )
        }
}
