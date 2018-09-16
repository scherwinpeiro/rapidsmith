/*
 * _______________________________________________________________________________
 *
 *  Copyright (c) 2012 TU Dresden, Chair for Embedded Systems
 *  Copyright (c) 2013-2016 TU Darmstadt, Computer Systems Group
 *  (http://www.rs.tu-darmstadt.de) All rights reserved.
 *
 *  Redistribution and use in source and binary forms, with or without
 *  modification, are permitted provided that the following conditions
 *  are met:
 *
 *  1. Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *  2. Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *  3. All advertising materials mentioning features or use of this software
 *     must display the following acknowledgement: "This product includes
 *     software developed by the TU Dresden Chair for Embedded Systems, TU Darmstadt Computer Systems Group and
 *     its contributors."
 *
 *  4. Neither the name of the TU Dresden Chair for Embedded Systems, TU Darmstadt Computer Systems Group nor the
 *     names of its contributors may be used to endorse or promote products
 *     derived from this software without specific prior written permission.
 *
 *  THIS SOFTWARE IS PROVIDED BY TU DRESDEN CHAIR FOR EMBEDDED SYSTEMS, TU DARMSTADT COMPUTER SYSTEMS GROUP AND
 *  CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING,
 *  BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 *  FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 *  REGENTS OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 *  SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
 *  TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 *  PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 *  LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 *  NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 *  SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * _______________________________________________________________________________
 */

package edu.byu.ece.rapidSmith.router.wireCluster;

import edu.byu.ece.rapidSmith.device.Device;
import edu.byu.ece.rapidSmith.device.Tile;
import edu.byu.ece.rapidSmith.device.WireConnection;
import edu.byu.ece.rapidSmith.device.WireEnumerator;

import java.util.Arrays;
import java.util.stream.Stream;

/**
 * Save a single object for all directly connected wires
 *
 * @param <T> Content type
 */
public abstract class WireClusterFactory<T extends IWireCluster> {


	protected final WireEnumerator wireEnumerator;
	private WireDataMap<T> clusters = new WireDataMap<>();



	/**
	 * Create a WireClusterFactory
	 *
	 * @param device the device to generate clusters for
	 */
	public WireClusterFactory(Device device, WireEnumerator wireEnumerator) {
		this.wireEnumerator = wireEnumerator;

		initializeClusters(device);

	}

	protected void initializeClusters(Device device) {
		System.out.println("Initializing clusters");
		device.getTileMap().values().forEach(tile -> {
			getWires(tile).forEach(wire -> {
				final TileWire tw = new TileWire(tile, wire);
				if (!clusters.containsKey(tw.tile, tw.wire)) {
					initClusters(tile, wire, getRepresentative(tw));
				}
			});
		});
		System.out.println("finished clusters");
	}

	private Stream<Integer> getWires(Tile tile) {
		//Wires for input pins have no outbound connections and are therefore not listed in the routing wires.
		//We need to grab them separately
		Stream<Integer> pinWires;
		if (tile.getPrimitiveSites() != null) {
			pinWires = Arrays.stream(tile.getPrimitiveSites()).flatMap(site -> site.getPins().values().stream());
		} else {
			pinWires = Stream.empty();
		}
		Stream<Integer> routingWires = tile.getWires().stream();

		return Stream.concat(pinWires, routingWires).distinct();
	}

	/**
	 * Create a representative for a specific wire.
	 *
	 * Note that there may be more representatives generated than will be in use later
	 *
	 * @param tileWire the tilewire to get a representative for
	 * @return a representative
	 */
	protected abstract T getRepresentative(TileWire tileWire);


	private void initClusters(Tile tile, int wire, T representative) {
		final TileWire tw = new TileWire(tile, wire);
		final T oldValue = clusters.put(tw.tile, tw.wire, representative);
		if (oldValue != null) {
			if (oldValue == representative) {
				return;
			} else {
				merge(representative, oldValue);
			}
		} else {
			representative.add(tw, wireEnumerator);
		}

		final WireConnection[] conns = tile.getWireConnections(wire);
		if (conns != null) {
			for (WireConnection conn : conns) {
				if (!conn.isPIP()) {
					initClusters(conn.getTile(tile), conn.getWire(), representative);
				}
			}
		}
	}

	private void merge(T representative, T other) {
		other.streamTileWires().forEach(tw -> {
			representative.add(tw, wireEnumerator);
			clusters.put(tw.tile, tw.wire, representative);
		});

	}

	protected T getCluster(Tile tile, int wire) {
		final T res = clusters.get(tile, wire);
		if (res==null) {
			return inputPinCluster(tile, wire);
		}
		return res;
	}

	private T inputPinCluster(Tile tile, int wire) {
		final TileWire tileWire = new TileWire(tile, wire);
		T res = getRepresentative(tileWire);
		res.add(tileWire, wireEnumerator);
		clusters.put(tile, wire, res);
		return res;
	}


	public Stream<T> getAllClusters() {
		return clusters.stream();
	}
}
