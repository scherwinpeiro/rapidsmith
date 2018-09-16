package edu.byu.ece.rapidSmith.router.wireCluster;

import edu.byu.ece.rapidSmith.device.WireContainer;
import edu.byu.ece.rapidSmith.device.WireEnumerator;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Default implementation of IWireCluster
 */
public class WireCluster implements IWireCluster, WireContainer {

	/**
	 * The tileWires in this cluster
	 */
	protected Set<TileWire> tileWires = new HashSet<>();

	public WireCluster() {
	}

	@Override
	public Stream<TileWire> streamTileWires() {
		return tileWires.stream();
	}

	@Override
	public void add(TileWire tileWire, WireEnumerator wireEnumerator) {
		tileWires.add(tileWire);
	}

	public Set<TileWire> getTileWires() {
		return tileWires;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof WireCluster)) {
			return false;
		}

		WireCluster that = (WireCluster) o;

		return tileWires.equals(that.tileWires);
	}

	@Override
	public int hashCode() {
		return tileWires.hashCode();
	}

	@Override
	public String toString() {
		return tileWires.toString();
	}

	public String toString(WireEnumerator wireEnumerator) {
		return tileWires.stream().map(tw -> tw.toString(wireEnumerator)).collect(Collectors.joining(",", "[", "]"));
	}
}
