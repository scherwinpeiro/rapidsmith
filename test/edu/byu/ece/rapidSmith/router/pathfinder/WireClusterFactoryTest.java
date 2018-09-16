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

package edu.byu.ece.rapidSmith.router.pathfinder;

import de.tu_darmstadt.rs.util.tracer.Tracer;

import edu.byu.ece.rapidSmith.device.Device;
import edu.byu.ece.rapidSmith.device.Tile;
import edu.byu.ece.rapidSmith.device.WireConnection;
import edu.byu.ece.rapidSmith.device.WireEnumerator;
import edu.byu.ece.rapidSmith.router.wireCluster.TileWire;
import edu.byu.ece.rapidSmith.router.wireCluster.WireCluster;
import edu.byu.ece.rapidSmith.router.wireCluster.WireClusterFactory;
import edu.byu.ece.rapidSmith.util.DeviceDatabaseProvider;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Created by wenzel on 19.04.17.
 */
public class WireClusterFactoryTest {


	private static Device device;
	private static WireEnumerator wireEnumerator;


	static class TestingWireCluster extends WireCluster {

		public final String name;

		TestingWireCluster(String name) {
			this.name = name;
		}

		@Override
		public String toString() {
			return name;
		}
	}


	static class TestingWireClusterFactory extends WireClusterFactory<TestingWireCluster> {

		public TestingWireClusterFactory(Device device) {
			super(device, device.getWireEnumerator());
		}

		@Override
		protected TestingWireCluster getRepresentative(TileWire tw) {
			return new TestingWireCluster(tw.tile.toString() + "." + wireEnumerator.getWireName(tw.wire));
		}

		@Override
		public TestingWireCluster getCluster(Tile tile, int wire) {
			return super.getCluster(tile, wire);
		}
	}

	@BeforeClass
	public static void initClass() {
		device = DeviceDatabaseProvider.getDeviceDatabase().loadDevice("xc6slx16csg324");
		wireEnumerator = DeviceDatabaseProvider.getDeviceDatabase().loadWireEnumerator(device.getFamilyType());
	}

	@Test
	public void checkClustersCorrect() {

		Tracer.start();
		Tracer.enter("Create clusters");
		TestingWireClusterFactory factory = new TestingWireClusterFactory(device);
		Tracer.exit();
		Tracer.enter("Check clusters");

		device.getTileMap().values().forEach(tile -> {
			if (tile.getWireHashMap() != null) {
				tile.getWireHashMap().forEach((wire, conns) -> {
					Object orig = factory.getCluster(tile, wire);
					for (WireConnection conn : conns) {
						Object n2 = factory.getCluster(conn.getTile(tile), conn.getWire());


						boolean same = orig == n2;
						if (same == conn.isPIP()) {
							String message = "In " + tile.getName()
									+ ", " + wireEnumerator.getWireName(wire)
									+ " is connected to " + conn.getTile(tile) + "." + conn.getWire()
									+ " " + (conn.isPIP()? "by pip" : "directly") + ". "
									+ "objects are " + (same? "the same" : "not the same");
							Assert.fail(message);
						}
					}
				});
			}
		});
		Tracer.exit();
		Tracer.enter("Stats");

		System.out.println("Number of clusters: " + factory.getAllClusters().count());
		final long numElems = factory.getAllClusters().mapToLong(c -> c.getTileWires().size()).sum();
		System.out.println("elements in clusters: " + numElems);

		Tracer.exit();
		Tracer.printStats(System.out);

	}

	private boolean hasBackEdge(Tile tile, int wire, WireConnection conn) {
		final Tile otherTile = conn.getTile(tile);
		if (otherTile.getWireHashMap() == null) {
			System.out.println("no wires");
			return false;
		}
		if (otherTile.getWireConnections(conn.getWire()) == null) {
			System.out.println("no conns");
			return false;
		}
		for (WireConnection back : otherTile.getWireConnections(conn.getWire())) {
			if (back.getTile(otherTile) == tile && back.getWire() == wire) {
				return true;
			}
		}
		return false;
	}

	@Test
	@Ignore
	public void verifyBidirectional() {
		device.getTileMap().values().stream().forEach(tile -> {
			if (tile.getWireHashMap() != null) {
				tile.getWireHashMap().forEach((wire, conns) -> {
					for (WireConnection conn : conns) {
						if (!conn.isPIP()) {
							if (!hasBackEdge(tile, wire, conn)) {
								Assert.fail("No back edge for " + tile + "." + wire + " " + conn);
							} else {
								System.out.println("ok");
							}
						}
					}
				});

			}
		});
	}
}
