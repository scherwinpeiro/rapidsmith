package edu.byu.ece.rapidSmith.router.pathfinder;

import edu.byu.ece.rapidSmith.design.Pin;
import edu.byu.ece.rapidSmith.device.Device;
import edu.byu.ece.rapidSmith.device.Tile;
import edu.byu.ece.rapidSmith.device.WireEnumerator;
import edu.byu.ece.rapidSmith.router.wireCluster.TileWire;
import edu.byu.ece.rapidSmith.router.wireCluster.WireClusterFactory;

import java.util.Arrays;
import java.util.Objects;

/**
 * Creates Nodes, but every node is created only once
 */
public class PathfinderNodeFactory extends WireClusterFactory<PathfinderNode> {
    private final Device dev;

    public PathfinderNodeFactory(Device dev) {
		super(dev, dev.getWireEnumerator());
		this.dev = dev;
    }

	public PathfinderNodeFactory(Device dev, WireEnumerator wireEnumerator) {
		super(dev, wireEnumerator);
		this.dev = dev;
	}

	@Override
	protected PathfinderNode getRepresentative(TileWire tileWire) {
		return new PathfinderNode();
	}

	public PathfinderNode getNode(Tile tile, int wire) {
		final PathfinderNode cluster = getCluster(tile, wire);
		/*PathfinderNode cluster = data.get(tile, wire);
		if (cluster==null) {
			cluster = new PathfinderNode(tile, wire);
			data.put(tile, wire, cluster);
		}*/
	/*	if (cluster==null) {
			if (!dev.getTileMap().values().stream().anyMatch(t->t==tile)) {
				System.err.println("tile not found");
			}
			System.err.println(tile.getWireConnections(wire));
			System.err.println("No Node for "+tile+"."+dev.getWireEnumerator().getWireName(wire) +" "+wire);
			return new PathfinderNode(tile, wire);


		}*/
		return cluster;
    }

    public PathfinderNode getNode(Pin pin) {
        Objects.requireNonNull(pin);
        try {
			return getNode(pin.getTile(), dev.getPrimitiveExternalPin(pin));
		} catch (NullPointerException e) {
			System.err.println("pin: "+pin);
			System.err.println(Arrays.toString(pin.getTile().getWireConnections(dev.getPrimitiveExternalPin(pin))));
			throw e;
		}
    }

}
