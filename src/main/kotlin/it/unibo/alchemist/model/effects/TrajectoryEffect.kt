package it.unibo.alchemist.model.effects

import it.unibo.alchemist.boundary.ui.api.Wormhole2D
import it.unibo.alchemist.model.Environment
import it.unibo.alchemist.model.Node
import it.unibo.alchemist.model.Position2D
import it.unibo.alchemist.model.molecules.SimpleMolecule
import java.awt.Color
import java.awt.Graphics2D
import java.awt.Point
import java.awt.geom.AffineTransform
import java.awt.geom.Ellipse2D

/**
 * An effect that draws a trajectory of the nodes in the environment.
 */
@Suppress("DEPRECATION")
class TrajectoryEffect : it.unibo.alchemist.boundary.swingui.effect.api.Effect {
    @Transient
    private val positionsMemory: MutableMap<Int, MutableList<Pair<Position2D<*>, Double>>> = mutableMapOf()

    @Transient
    private val lastDrawMemory: MutableMap<Int, Int> = mutableMapOf()
    // private var snapshotSize: Int = 1000

    private val nodeSize: Int = 4

    private val colorMolecule: String = "hue"

    private val velocityMolecule: String = "velocity"

    override fun <T : Any, P : Position2D<P>> apply(
        g: Graphics2D,
        node: Node<T>,
        environment: Environment<T, P>,
        wormhole: Wormhole2D<P>,
    ) {
        if (!node.contents.containsKey(SimpleMolecule("agent"))) return
        val nodePosition: P = environment.getPosition(node)
        val viewPoint: Point = wormhole.getViewPoint(nodePosition)
        val (x, y) = Pair(viewPoint.x, viewPoint.y)
        drawDirectedNode(g, node, x, y, environment, wormhole)
    }

    override fun getColorSummary(): Color = Color.BLACK

    private fun <T : Any, P : Position2D<P>> drawDirectedNode(
        graphics2D: Graphics2D,
        node: Node<T>,
        x: Int,
        y: Int,
        environment: Environment<T, P>,
        wormhole: Wormhole2D<P>,
    ) {
        val transform = computeTransform(x, y, nodeSize.toDouble(), 1.0)
        val color = computeColorOrBlack(node, environment)
        val transformedShape = transform.createTransformedShape(DRONE_SHAPE)
        drawTrajectory(graphics2D, node, color, wormhole)
        graphics2D.color = color
        graphics2D.fill(transformedShape)
        updateTrajectory(node, environment)
    }

    private fun <P : Position2D<P>> drawTrajectory(
        graphics2D: Graphics2D,
        node: Node<*>,
        colorBase: Color,
        wormhole2D: Wormhole2D<P>,
    ) {
        val positions = positionsMemory[node.id].orEmpty()
        val trajectory = positions // .takeLast(snapshotSize)
        if (trajectory.size > 1) {
            trajectory.zipWithNext().forEachIndexed { index, value ->
                @Suppress("UNCHECKED_CAST")
                val alphaValue =
                    ((index.toFloat() / trajectory.size) * ADJUST_COLOR_FACTOR)
                        .toInt()
                        .coerceIn(MINIMUM_COLOR_ALPHA, ADJUST_COLOR_FACTOR)
                val (first, second) = value
                val startPoint = wormhole2D.getViewPoint(first.first as P)
                val endPoint = wormhole2D.getViewPoint(second.first as P)
                val colorFaded = Color(colorBase.red, colorBase.green, colorBase.blue, alphaValue)
                graphics2D.color = colorFaded
                graphics2D.stroke = java.awt.BasicStroke(4.0f)
                graphics2D.drawLine(startPoint.x, startPoint.y, endPoint.x, endPoint.y)
            }
        }
    }

    private fun computeTransform(
        x: Int,
        y: Int,
        size: Double,
        rotation: Double,
    ): AffineTransform =
        AffineTransform().apply {
            translate(x.toDouble(), y.toDouble())
            scale(size, size)
            rotate(rotation)
        }

    private fun computeColorOrBlack(
        node: Node<*>,
        environment: Environment<*, *>,
    ): Color =
        node
            .takeIf { it.contains(SimpleMolecule(colorMolecule)) }
            ?.getConcentration(SimpleMolecule(colorMolecule))
            ?.let { it as? Number }
            ?.toDouble()
            ?.let {
                Color.getHSBColor(
                    (it / (environment.nodeCount.toDouble())).toFloat(),
                    1f,
                    1f,
                )
            }
            ?: Color.BLACK

    private fun <P : Position2D<P>, T> updateTrajectory(
        node: Node<T>,
        environment: Environment<T, P>,
    ) {
        val positions = positionsMemory[node.id] ?: mutableListOf()
        val lastDraw = lastDrawMemory[node.id] ?: 0
        val roundedTime =
            environment.simulation.time
                .toDouble()
                .toInt()
        if (roundedTime >= lastDraw) {
            lastDrawMemory[node.id] = lastDraw
            // take the last position
            val lastPosition = positions.lastOrNull()?.first as P?
            val currentPosition = environment.getPosition(node)
            val shouldNotUpdate = lastPosition?.distanceTo(currentPosition)?.let { it < THRESHOLD_REDRAW } ?: false
            // if it is to near, do not put the new position
            if (shouldNotUpdate) return
            positions.add(environment.getPosition(node) to rotation(node))
            if (positions.size > MAX_SNAPSHOT_LENGTH) {
                positions.removeAt(0)
            }
            positionsMemory[node.id] = positions
        }
    }

    private fun <T> rotation(node: Node<T>): Double =
        node
            .takeIf { it.contains(SimpleMolecule(velocityMolecule)) }
            ?.getConcentration(SimpleMolecule(velocityMolecule))
            ?.let { it as? DoubleArray }
            ?.let { Math.atan2(it[0], it[1]) }
            ?: 0.0

    private companion object {
        private const val THRESHOLD_REDRAW = 0.05
        private const val ADJUST_COLOR_FACTOR: Int = 50

        private const val MINIMUM_COLOR_ALPHA: Int = 10

        private const val MAX_SNAPSHOT_LENGTH: Int = 1000

        private const val DRONE_SIZE = 4.0

        private val DRONE_SHAPE: Ellipse2D.Float =
            Ellipse2D.Float(
                -DRONE_SIZE.toFloat() / 2.0f,
                -DRONE_SIZE.toFloat() / 2.0f,
                DRONE_SIZE.toFloat(),
                DRONE_SIZE.toFloat(),
            )
    }
}
