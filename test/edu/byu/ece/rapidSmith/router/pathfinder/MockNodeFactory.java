package edu.byu.ece.rapidSmith.router.pathfinder;

import edu.byu.ece.rapidSmith.design.Pin;
import edu.byu.ece.rapidSmith.device.Device;
import edu.byu.ece.rapidSmith.device.Tile;
import edu.byu.ece.rapidSmith.device.WireEnumerator;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Created by wenzel on 07.11.16.
 */
class MockNodeFactory extends PathfinderNodeFactory {

    private Set<MockPathfinderNode> otherNodes = new HashSet<>();

    public MockNodeFactory(Device dev, WireEnumerator wireEnumerator) {
        super(dev, wireEnumerator);
    }

    @Override
    public PathfinderNode getNode(Tile tile, int wire) {
        throw new UnsupportedOperationException("Not implemented in mock");
    }

    Map<Pin,MockPathfinderNode> pinNodes = new HashMap<>();

    @Override
    public MockPathfinderNode getNode(Pin pin) {
        return pinNodes.get(pin);
    }

    public MockPathfinderNode createNodeForPin(Pin pin) {
        return pinNodes.computeIfAbsent(pin,p->new MockPathfinderNode(pin.getName(), wireEnumerator));
    }

	//@Override
	public Stream<PathfinderNode> getAllClusters() {
		return Stream.concat(pinNodes.values().stream(),otherNodes.stream());
	}

    public void registerNode(MockPathfinderNode n) {
        otherNodes.add(n);
    }

	@Override
	protected void initializeClusters(Device device) {
		//Nothing to do
	}
}
