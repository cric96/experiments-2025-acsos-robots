package it.unibo.formalization

/**
 * Node represents a point in a 2D space with an ID.
 * It can be used to represent robots, tasks, or any other entities in the system.
 *
 * @property position The (x, y) coordinates of the node.
 * @property id The unique identifier for the node.
 */
data class Node(
    val position: Pair<Double, Double>,
    val id: Int,
) {
    /**
     * Calculates the distance between this node and another node.
     * This is a simple Euclidean distance calculation.
     *
     * @param other The other node to calculate the distance to.
     * @return The Euclidean distance between the two nodes.
     */
    fun distance(other: Node): Double {
        val dx = position.first - other.position.first
        val dy = position.second - other.position.second
        return Math.sqrt(dx * dx + dy * dy)
    }
}

/**
 * Bid represents a bid made by a robot for a task.
 * It contains the task node, the robot ID, and the cost associated with the bid.
 *
 * @property task The task node for which the bid is made.
 * @property robot The ID of the robot making the bid.
 * @property cost The cost associated with the bid, which can be a marginal cost or an adjusted cost.
 */
data class Bid(
    val task: Node,
    val robot: Int,
    val cost: Double, // Can be marginal cost or adjusted cost
)
