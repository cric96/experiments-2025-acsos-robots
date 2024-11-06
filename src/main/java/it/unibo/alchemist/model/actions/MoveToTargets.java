package it.unibo.alchemist.model.actions;

import com.google.common.collect.ImmutableList;
import it.unibo.alchemist.model.Action;
import it.unibo.alchemist.model.Environment;
import it.unibo.alchemist.model.Node;
import it.unibo.alchemist.model.Position2D;
import it.unibo.alchemist.model.Molecule;
import it.unibo.alchemist.model.Reaction;
import it.unibo.alchemist.model.movestrategies.speed.ConstantSpeed;
import it.unibo.alchemist.model.movestrategies.target.FollowTarget;
import it.unibo.alchemist.model.routes.PolygonalChain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.apache.commons.math3.util.FastMath.atan2;
import static org.apache.commons.math3.util.FastMath.cos;
import static org.apache.commons.math3.util.FastMath.sin;

public class MoveToTargets<T, P extends Position2D<P>> extends AbstractConfigurableMoveNode<T, P>{

    private final List<Molecule> initialTargets;
    private int currentTarget = 0;
    private final double speed;

    protected MoveToTargets(final Environment<T, P> environment,
            final Node<T> node,
                            final Reaction<T> reaction,
                            final Collection<Molecule> trackedMolecules,
                            final double speed) {
        super(environment, node,
                (p1, p2) -> new PolygonalChain<>(ImmutableList.of(p1, p2)),
                new FollowTarget<>(environment, node, trackedMolecules.iterator().next()),
                new ConstantSpeed<>(reaction, speed));
        initialTargets = new ArrayList<>(trackedMolecules);
        this.speed = speed;
    }

    @Override
    protected P interpolatePositions(P current, P target, double maxWalk) {
        final P vector = target.minus(current.getCoordinates());
        if (current.distanceTo(target) < maxWalk) {
            return vector;
        }
        final double angle = atan2(vector.getY(), vector.getX());

        var nextPosition = getEnvironment().makePosition(maxWalk * cos(angle), maxWalk * sin(angle));
        if (nextPosition.equals(initialTargets.get(currentTarget))) {
            currentTarget++;
            var newTarget  = new FollowTarget<>(getEnvironment(), getNode(), initialTargets.get(currentTarget));
            super.setTargetPoint(newTarget.getTarget());
        }
        return getEnvironment().makePosition(maxWalk * cos(angle), maxWalk * sin(angle));
    }

    @Override
    public MoveToTargets<T, P> cloneAction(Node<T> node, Reaction<T> reaction) {
        return new MoveToTargets<>(getEnvironment(), node, reaction, initialTargets, speed);
    }


}
