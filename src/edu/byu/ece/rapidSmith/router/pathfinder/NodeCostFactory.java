package edu.byu.ece.rapidSmith.router.pathfinder;

import java.util.Collection;

/**
 * Created by wenzel on 04.11.16.
 */
public class NodeCostFactory {
    /*Queue<NodeCost> active = null;
    Queue<Queue<NodeCost>> old = new ArrayDeque<>();*/

    public NodeCost getNodeCost(PathfinderNode node, PathfinderNode parent, double cost, double estimatedRemaining) {
        /*NodeCost res;
        if (active==null || active.isEmpty()) {
            if (old.isEmpty())
                res = new NodeCost();
            else {
                active = old.poll();
                res = active.poll();
            }
        } else {
            res = active.poll();
        }
        res.initialize(node,parent,cost,estimatedRemaining);
        return res;*/
        return new NodeCost(node,parent,cost,estimatedRemaining);
    }

    public void recycle(NodeCost nodeCost) {
        /*if (active==null) {
            active = new PriorityQueue<>();
        }
        active.add(nodeCost);*/
        //TODO this is slower than throwing them away??? :(
    }

    public void recycleAll(Collection<NodeCost> l) {
        /*if (!l.isEmpty())
            old.add(new ArrayDeque<>(l));*/
        //TODO this is slower than throwing them away??? :(
    }
}
