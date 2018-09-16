package edu.byu.ece.rapidSmith.router.pathfinder;

import de.tu_darmstadt.rs.util.tracer.Tracer;

import edu.byu.ece.rapidSmith.design.Net;
import edu.byu.ece.rapidSmith.design.PIP;
import edu.byu.ece.rapidSmith.design.Pin;
import edu.byu.ece.rapidSmith.device.PrimitiveSite;
import edu.byu.ece.rapidSmith.device.Tile;
import edu.byu.ece.rapidSmith.device.WireConnection;
import edu.byu.ece.rapidSmith.device.WireDirection;
import edu.byu.ece.rapidSmith.device.WireEnumerator;
import edu.byu.ece.rapidSmith.router.wireCluster.TileWire;
import edu.byu.ece.rapidSmith.router.wireCluster.WireCluster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Predicate;

/**
 * Node in the routing.
 * The regular Node class also has parent and level. We don't want that, because every node exists exactly once.
 */
public class PathfinderNode extends WireCluster {

	private static final Logger logger = LoggerFactory.getLogger(PathfinderNode.class);
	/*private final Tile tile;
	private final int wire;*/

	private int currentUsage;
	private float historicalOveruse;
	private int historicalOveruseCount;

	private int seenInLocalIteration;
	private int seenInGlobalIteration;

	private int lastIterationUsage;

	/**
	 * Parent node. Used while routing a nets
	 */
	private PathfinderNode parent;

	/**
	 * The current NodeCost assigned to this node
	 */
	private NodeCost currentNodeCost;
	/**
	 * The iteration the nodeCost was assigned in
	 */
	private int nodeCostIteration;

	/**
	 * Nets that this node is used in. When a node does not have overuse, but then another nets wants to use this node
	 * as well, the original nets saved here has to be rerouted as well
	 */
	private Collection<Net> nets = new HashSet<>();
	private boolean blocked;
	private boolean sinkNode;

	private boolean isClockNode;

/*
	/**
	 * @param tile The tile of the new node.
	 * @param wire The wire of the new node.
	 * /
	public PathfinderNode(Tile tile, int wire) {
		this.tile = tile;
		this.wire = wire;
	}


	/**
	 * @return the tile
	 * /
	public Tile getTile() {
		return tile;
	}


	/**
	 * @return the wire
	 * /
	public int getWire() {
		return wire;
	}
*/

	public PathfinderNode getParent() {
		return parent;
	}

	public void setParent(PathfinderNode parent) {
		this.parent = parent;
	}

	public int getCurrentUsage() {
		return currentUsage;
	}

	public void setCurrentUsage(int currentUsage) {
		this.currentUsage = currentUsage;
	}

	public float getHistoricalOveruse() {
		return historicalOveruse;
	}

	public int getSeenInLocalIteration() {
		return seenInLocalIteration;
	}

	public void setSeenInLocalIteration(int seenInLocalIteration) {
		this.seenInLocalIteration = seenInLocalIteration;
	}

    /*public SinkPin getSinkPin(){
		return tile.getSinkPin(wire);
    }*/

	private int getManhattanDistance(Tile tile, Set<TileWire> others) {
		int min = Integer.MAX_VALUE;
		for (TileWire other : others) {
			int dist = tile.getManhattanDistance(other.tile);
			if (dist < min) {
				min = dist;
			}
		}
		return min;
	}

	private int getManhattanDistance(Set<TileWire> tiles, Set<TileWire> others) {
		int min = Integer.MAX_VALUE;
		for (TileWire tw : tiles) {
			int dist = getManhattanDistance(tw.tile, others);
			if (dist < min) {
				min = dist;
			}
		}
		return min;
	}

	private int getDistance(int aMin, int aMax, int bMin, int bMax) {
		//Check for overlap
		if (aMin <= bMax && bMin <= aMax) {
			return 0;
		}

		//a smaller than b?
		if (aMax < bMin) {
			return bMin - aMax;
		}

		return aMin - bMax;
	}

	public int getManhattanDistance(PathfinderNode sink) {
		//Naive
		/*return tileWires.stream().map(tileWire -> tileWire.tile).mapToInt(t ->
				sink.tileWires.stream().map(tileWire -> tileWire.tile).mapToInt(t::getManhattanDistance).min().orElseThrow(() -> new RuntimeException("sink has no tiles"))
		).min().orElseThrow(() -> new RuntimeException("node has no tiles"));*/

		//Explicit for
		/*int min = Integer.MAX_VALUE;
		for (TileWire own : tileWires) {
			for (TileWire other : sink.tileWires) {
				int dist = own.tile.getManhattanDistance(other.tile);
				if (dist < min) {
					min = dist;
				}
			}
		}
		return min;*/

		//Single tile
		/*if (singleTile != null) {
			if (sink.singleTile != null) {
				return singleTile.getManhattanDistance(sink.singleTile);
			} else {
				return getManhattanDistance(singleTile, sink.tileWires);
			}
		} else {
			if (sink.singleTile != null) {
				return getManhattanDistance(sink.singleTile, tileWires);
			} else {
				return getManhattanDistance(tileWires, sink.tileWires);
			}
		}*/

		//Min max
		return getDistance(minX, maxX, sink.minX, sink.maxX)
			+  getDistance(minY, maxY, sink.minY, sink.maxY);
	}

	private PathfinderNode[] neighbours;
	//private Map<PathfinderNode, Boolean> isPip;
/*
	public PathfinderNode[] getNeighbours(PathfinderNodeFactory factory) {
		if (neighbours == null) {
			WireConnection[] wires = tile.getWireConnections(wire);
			isPip = new HashMap<>();
			if (isSinkNode() || wires == null) {
				neighbours = new PathfinderNode[0];
			} else {
				Set<PathfinderNode> neighbourSet = new HashSet<>();
				for (int i = 0; i < wires.length; i++) {
					WireConnection conn = wires[i];
					PathfinderNode node = factory.getNode(conn.getTile(tile), conn.getWire());
					if (!node.isBlocked()) {
						neighbourSet.add(node);
						isPip.put(node, conn.isPIP());
					}
				}
				neighbours = neighbourSet.toArray(new PathfinderNode[neighbourSet.size()]);
			}
		}
		return neighbours;
	}*/

	public PathfinderNode[] getNeighbours(PathfinderNodeFactory factory) {

		if (neighbours == null) {
			Tracer.enterLoop("neighbours");
			Set<PathfinderNode> neighbourSet = new HashSet<>();
			tileWires.forEach((tileWire -> {
				WireConnection[] wires = tileWire.tile.getWireConnections(tileWire.wire);
				if (!isSinkNode() && wires != null) {
					for (WireConnection conn : wires) {
						PathfinderNode node = factory.getNode(conn.getTile(tileWire.tile), conn.getWire());
						if (node != this && !node.isBlocked()) {
							final boolean notAlreadyContained = neighbourSet.add(node);
							if (!notAlreadyContained) {
								throw new RuntimeException("Multiple connections between " + this + " and " + node);
							}
						}
					}
				}
			}));
			neighbours = neighbourSet.toArray(new PathfinderNode[neighbourSet.size()]);
			Tracer.exit();
		}
		return neighbours;
	}

/*
	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}

		PathfinderNode that = (PathfinderNode) o;

		if (wire != that.wire) {
			return false;
		}
		return tile.equals(that.tile);

	}

	@Override
	public int hashCode() {
		int result = tile.hashCode();
		result = 31 * result + wire;
		return result;
	}
*/

	/*public boolean isPipConnection(PathfinderNode node) {
		Boolean pip = isPip.get(node);
		if (pip == null) {
			throw new RuntimeException(node + " is not a neighbour of " + this);
		}
		return pip;
	}*/

	public int getLastIterationUsage() {
		return lastIterationUsage;
	}

	private static float highestHistorical = 0;

	public void updateGlobalIteration(int globalIteration) {
		while (this.seenInGlobalIteration < globalIteration) {
			seenInGlobalIteration++;

			lastIterationUsage = currentUsage;

			if (currentUsage > 1) {
				historicalOveruseCount++;
				historicalOveruse = historicalOveruse * 1.5f + 1;
				if (historicalOveruse > highestHistorical) {
					highestHistorical = historicalOveruse;
					logger.info("highest historical overuse now is {}", highestHistorical);
				}
			}
		}
	}

	public NodeCost getCurrentNodeCost() {
		return currentNodeCost;
	}

	public void setCurrentNodeCost(NodeCost currentNodeCost) {
		this.currentNodeCost = currentNodeCost;
	}

	public int getNodeCostIteration() {
		return nodeCostIteration;
	}

	public void setNodeCostIteration(int nodeCostIteration) {
		this.nodeCostIteration = nodeCostIteration;
	}

	public Collection<Net> getNets() {
		return nets;
	}

	public void block() {
		this.blocked = true;
	}

	public boolean isBlocked() {
		return blocked;
	}

	public void setSinkNode(boolean sinkNode) {
		this.sinkNode = sinkNode;
	}

	public boolean isSinkNode() {
		return sinkNode;
	}

	public boolean anyMatch(Predicate<TileWire> predicate) {
		return tileWires.stream().anyMatch(predicate);
	}

	private boolean clockSink = false;

	public boolean isClockSink() {
		return clockSink;
	}

	private Tile singleTile = null;
	private int minX, maxX, minY, maxY;

	@Override
	public void add(TileWire tileWire, WireEnumerator wireEnumerator) {
		boolean first = tileWires.isEmpty();
		super.add(tileWire, wireEnumerator);

		if (first) {
			singleTile = tileWire.tile;
		} else {
			if (tileWire.tile != singleTile) {
				singleTile = null;
			}
		}

		final int x = tileWire.tile.getTileXCoordinate();
		final int y = tileWire.tile.getTileYCoordinate();

		if (first) {
			minX = x;
			maxX = x;
			minY = y;
			maxY = y;
		} else {
			minX = Math.min(minX, x);
			minY = Math.min(minY, y);
			maxX = Math.max(maxX, x);
			maxY = Math.max(maxY, y);
		}


		if (wireEnumerator.getWireDirection(tileWire.wire) == WireDirection.CLK) {
			isClockNode = true;
		}
		final String wn = wireEnumerator.getWireName(tileWire.wire);
		if (wn.contains("BUFIO2FB") || wn.contains("CLK")) {
			clockSink = true;
		}
	}

	public boolean isClockNode() {
		return isClockNode;
	}


	public Optional<Pin> toPin(BiFunction<PrimitiveSite, String, Pin> primitiveSiteMapper) {
		for (TileWire tileWire : tileWires) {
			final Optional<Pin> pin = tileWire.toPin(primitiveSiteMapper);
			if (pin.isPresent())
				return pin;
		}
		return Optional.empty();
	}

	public PIP createPip(PathfinderNode successor) {
		for (TileWire currentTw : getTileWires()) {
			for (TileWire nextTw : successor.getTileWires()) {
				final boolean correct = currentTw.streamConnections()
						.map(currentTw::neighbour)
						.anyMatch(n -> n.equals(nextTw));
				if (correct) {
					return currentTw.makePip(nextTw);
				}
			}
		}
		return null;
	}
}
