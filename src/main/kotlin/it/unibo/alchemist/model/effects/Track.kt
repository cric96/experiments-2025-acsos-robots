package it.unibo.alchemist.model.effects

import it.unibo.alchemist.boundary.ui.api.Wormhole2D
import it.unibo.alchemist.model.Environment
import it.unibo.alchemist.model.Node
import it.unibo.alchemist.model.Position2D
import it.unibo.alchemist.model.molecules.SimpleMolecule
import java.awt.Color
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Shape
import java.awt.geom.AffineTransform
import java.awt.geom.Ellipse2D

@Suppress("DEPRECATION")
class TrajectoryEffect : it.unibo.alchemist.boundary.swingui.effect.api.Effect {
    @Transient
    private var positionsMemory: MutableMap<Int, MutableList<Pair<Position2D<*>, Double>>> = mutableMapOf()

    @Transient
    private var lastDrawMemory: MutableMap<Int, Int> = mutableMapOf()

    private var trackEnabled: Boolean = true

    private var snapshotSize: Int = 1000

    private var nodeSize: Int = 4

    private var colorMolecule: String = "hue"

    private var velocityMolecule: String = "velocity"

    private var maxValue: String = ""


    override fun <T : Any, P : Position2D<P>> apply(
        g: Graphics2D,
        node: Node<T>,
        environment: Environment<T, P>,
        wormhole: Wormhole2D<P>,
    ) {
        if(!node.contents.containsKey(SimpleMolecule("agent"))) return
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
        if (trackEnabled) drawTrajectory(graphics2D, node, color, wormhole, DRONE_SHAPE)
        graphics2D.color = color
        graphics2D.fill(transformedShape)
        updateTrajectory(node, environment)
    }

    private fun <P : Position2D<P>> drawTrajectory(
        graphics2D: Graphics2D,
        node: Node<*>,
        colorBase: Color,
        wormhole2D: Wormhole2D<P>,
        shape: Shape,
    ) {
        val positions = positionsMemory[node.id] ?: emptyList()
        val alpha = MAX_COLOR / (Math.min(snapshotSize, positions.size) * ADJUST_ALPHA_FACTOR + 1)
        positions.takeLast(snapshotSize).withIndex().forEach { (index, pair) ->
            val (position, rotation) = pair
            val colorFaded =
                Color(colorBase.red, colorBase.green, colorBase.blue, 10)

            @Suppress("UNCHECKED_CAST")
            val transform =
                computeTransform(
                    wormhole2D.getViewPoint(position as P).x,
                    wormhole2D.getViewPoint(position).y,
                    nodeSize.toDouble(),
                    rotation,
                )
            val transformedShape = transform.createTransformedShape(shape)
            graphics2D.color = colorFaded
            graphics2D.fill(transformedShape)
        }
    }

    private fun computeTransform(x: Int, y: Int, size: Double, rotation: Double): AffineTransform =
        AffineTransform().apply {
            translate(x.toDouble(), y.toDouble())
            scale(size, size)
            rotate(rotation)
        }

    private fun computeColorOrBlack(node: Node<*>, environment: Environment<*, *>): Color = node
        .takeIf { it.contains(SimpleMolecule(colorMolecule)) }
        ?.getConcentration(SimpleMolecule(colorMolecule))
        ?.let { it as? Number }
        ?.toDouble()
        ?.let {
            Color.getHSBColor(
                (it / (maxValue.toDoubleOrNull() ?: environment.nodeCount.toDouble())).toFloat(),
                1f,
                1f,
            )
        }
        ?: Color.BLACK

    private fun <P : Position2D<P>, T> updateTrajectory(node: Node<T>, environment: Environment<T, P>) {
        val positions = positionsMemory[node.id] ?: mutableListOf()
        val lastDraw = lastDrawMemory[node.id] ?: 0
        val roundedTime = environment.simulation.time.toDouble().toInt()
        val threshouldRedraw = 0.05
        if (roundedTime >= lastDraw) {
            lastDrawMemory[node.id] = lastDraw
            // take the last position
            val lastPosition = positions.lastOrNull()?.first as P?
            val currentPosition = environment.getPosition(node)
            val shouldNotUpdate = lastPosition?.distanceTo(currentPosition)?.let { it < threshouldRedraw } ?: false
            // if it is to near, do not put the new position
            if(shouldNotUpdate) return
            positions.add(environment.getPosition(node) to rotation(node))
            if (positions.size > MAX_SNAPSHOT_LENGTH) {
                positions.removeAt(0)
            }
            positionsMemory[node.id] = positions
        }
    }

    private fun <T> rotation(node: Node<T>): Double = node
        .takeIf { it.contains(SimpleMolecule(velocityMolecule)) }
        ?.getConcentration(SimpleMolecule(velocityMolecule))
        ?.let { it as? DoubleArray }
        ?.let { Math.atan2(it[0], it[1]) }
        ?: 0.0

    private companion object {
        private const val MAX_NODE_SIZE: Int = 20

        private const val MAX_TIMESPAN: Int = 100

        private const val MAX_SNAPSHOT_LENGTH: Int = 1000

        private const val MIN_SNAPSHOT_LENGTH: Int = 10

        private const val DEFAULT_SNAPSHOT_LENGTH: Int = 140

        private const val ADJUST_ALPHA_FACTOR: Int = 4

        private const val CLOCK: Int = 10

        private const val MAX_COLOR: Double = 255.0

        private const val DRONE_SIZE = 4.0

        private val DRONE_SHAPE: Ellipse2D.Float = Ellipse2D.Float(-DRONE_SIZE.toFloat() / 2.0f, -DRONE_SIZE.toFloat() / 2.0f, DRONE_SIZE.toFloat(), DRONE_SIZE.toFloat())

    }
}





