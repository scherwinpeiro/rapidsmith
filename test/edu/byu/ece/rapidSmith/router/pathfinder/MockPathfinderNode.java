package edu.byu.ece.rapidSmith.router.pathfinder;

import edu.byu.ece.rapidSmith.design.PIP;
import edu.byu.ece.rapidSmith.device.Tile;
import edu.byu.ece.rapidSmith.device.WireEnumerator;
import edu.byu.ece.rapidSmith.router.wireCluster.TileWire;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by wenzel on 07.11.16.
 */
public class MockPathfinderNode extends PathfinderNode {

	public static Tile mockTile = new Tile();

	static {
		mockTile.setName("Mock");
	}

	private final String name;

	public MockPathfinderNode(String name, WireEnumerator wireEnumerator) {
		add(new TileWire(mockTile, 0), wireEnumerator);
		this.name = name;
	}

	protected Map<MockPathfinderNode, Integer> neighbours = new HashMap<>();
	private MockPathfinderNode[] neighboursArr = new MockPathfinderNode[0];

	@Override
	public PathfinderNode[] getNeighbours(PathfinderNodeFactory factory) {
		return neighboursArr;
	}

	public void connectTo(MockPathfinderNode neighbour, int cost) {
		neighbours.put(neighbour, cost);
		neighboursArr = neighbours.keySet().toArray(new MockPathfinderNode[neighbours.size()]);
	}

	public int getCost(PathfinderNode neighbour) {
		Integer res = neighbours.get(neighbour);
		if (res == null) {
			System.out.println("blah");
			throw new RuntimeException(this + " does not have neighbour " + neighbour + ". Neighbours are: " + neighbours);
		}
		return res;
	}


    /*@Override
    public boolean isPipConnection(PathfinderNode node) {
        return true;
    }*/

	@Override
	public String toString() {
		return name;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		if (!super.equals(o)) return false;

		MockPathfinderNode that = (MockPathfinderNode) o;

		return name.equals(that.name);

	}

	@Override
	public int hashCode() {
		int result = super.hashCode();
		result = 31 * result + name.hashCode();
		return result;
	}

	public PIP createPip(PathfinderNode successor) {
		return null;
	}
}
