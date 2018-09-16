package edu.byu.ece.rapidSmith.router.pathfinder;

/**
 * Cost of a PathfinderNode as currently seen in the routing
 */
public class NodeCost implements Comparable<NodeCost> {

	/**
	 * Associated node
	 */
	private final PathfinderNode node;
	/**
	 * The associated node's parent
	 */
	private final PathfinderNode parent;
	/**
	 * Cost for getting there
	 */
	private final double cost;
	private final double estimatedRemaining;
	private final double total;
	private boolean valid;

	public NodeCost(PathfinderNode node, PathfinderNode parent, double cost, double estimatedRemaining) {

		this.node = node;
		this.parent = parent;
		this.cost = cost;
		this.estimatedRemaining = estimatedRemaining;
		valid = true;
		total = cost + estimatedRemaining;
	}


    /*public void initialize(PathfinderNode node, PathfinderNode parent, double cost, double estimatedRemaining) {
		this.node = node;
        this.parent = parent;
        this.cost = cost;
        this.estimatedRemaining = estimatedRemaining;
        this.valid = true;
    }*/

	public PathfinderNode getNode() {
		return node;
	}

	public double getCost() {
		return cost;
	}


	@Override
	public int compareTo(NodeCost o) {
		return Double.compare(total, o.total);

	}

	public PathfinderNode getParent() {
		return parent;
	}

	public double getEstimatedRemaining() {
		return estimatedRemaining;
	}

	public boolean isValid() {
		return valid;
	}

	public void invalidate() {
		valid = false;
	}
}
