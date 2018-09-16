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

import edu.byu.ece.rapidSmith.device.Device;
import edu.byu.ece.rapidSmith.device.Tile;
import edu.byu.ece.rapidSmith.device.TileType;
import edu.byu.ece.rapidSmith.device.WireConnection;
import edu.byu.ece.rapidSmith.device.WireEnumerator;
import edu.byu.ece.rapidSmith.device.helper.WireHashMap;
import edu.byu.ece.rapidSmith.util.DeviceDatabaseProvider;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by wenzel on 19.04.17.
 */
public class TestConnectedSizes {

	private static boolean contains(WireConnection[] arr, WireConnection elem) {
		for (WireConnection e : arr) {
			if (elem.equals(e)) {
				return true;
			}
		}
		return false;
	}

	private static boolean wireConnsEqual(WireConnection[] a, WireConnection[] b) {
		for (WireConnection aElem : a) {
			if (!contains(b, aElem)) {
				return false;
			}
		}
		return true;
	}

	private static boolean allWireConnsEqual(Collection<WireConnection[]> wireConns) {
		WireConnection[] first = null;
		for (WireConnection[] wireConn : wireConns) {
			if (first == null) {
				first = wireConn;
			} else {
				if (!wireConnsEqual(wireConn, first)) {
					return false;
				}
			}
		}
		return true;
	}

	public static void main(String[] args) {
		//Design design = new Design("asdf");
		Device device = DeviceDatabaseProvider.getDeviceDatabase().loadDevice("xc6slx16csg324");
		final WireEnumerator wireEnumerator = DeviceDatabaseProvider.getDeviceDatabase().loadWireEnumerator(device.getFamilyType());

		//checkWireConnsPerWireEqual(device, wireEnumerator);
		//countDifferentWireHashMaps(device);
		//checkCrossTileWiresIfPip(device, wireEnumerator);
		//listConnected(device, wireEnumerator);
		//verifyRouteNode(device, wireEnumerator);
	}

	private static Stream<Tile> getConnectedTilesByWires(Tile tile, int wire, boolean allowFirstLevel) {
		return Arrays.stream(tile.getWireConnections(wire))
				.filter(c -> !c.isPIP())
				.flatMap(c -> {
					Stream<Tile> byOther;
					if (allowFirstLevel) {
						byOther = getConnectedTilesByWires(c.getTile(tile), wire, true);
					} else {
						byOther = Stream.empty();
					}
					if (c.getRowOffset() != 0 || c.getColumnOffset() != 0) {
						return Stream.concat(Stream.of(c.getTile(tile)), byOther);
					} else {
						return byOther;
					}
				});
	}

	private static void listConnected(Device device, WireEnumerator wireEnumerator) {
		streamAllTiles(device).forEach(tile -> {
			tile.getWires().forEach(wire -> {
				final Set<Tile> conn = getConnectedTilesByWires(tile, wire, false).collect(Collectors.toSet());
				if (conn.size() > 1) {
					System.out.println(wireEnumerator.getWireName(wire) + " is connected to " + conn);
				}

			});
		});
	}

	private static void checkCrossTileWiresIfPip(Device device, WireEnumerator wireEnumerator) {
		streamAllTiles(device).map(Tile::getWireHashMap).filter(Objects::nonNull).distinct().forEach(connMap -> {
			connMap.forEach((wire, conns) -> {
				Arrays.stream(conns).forEach(conn -> {
					if (!conn.isPIP() && (conn.getColumnOffset() != 0 || conn.getRowOffset() != 0)) {
						System.out.println("Cross tile wire from " + wireEnumerator.getWireName(wire) + " to " + conn.toString(wireEnumerator));
					}
				});
			});
		});
	}

	private static void checkWireConnsPerWireEqual(Device device, WireEnumerator wireEnumerator) {
		Map<Integer, Map<TileType, Map<Tile, WireConnection[]>>> possibleConnsPerWire = new HashMap<>();

		streamAllTiles(device)
				.forEach(tile -> {
					Optional.ofNullable(tile.getWireHashMap()).ifPresent(map -> map.keySet().forEach(wire -> {
						possibleConnsPerWire.computeIfAbsent(wire, (x) -> new HashMap<>())
								.computeIfAbsent(tile.getType(), (x) -> new HashMap<>())
								.put(tile, tile.getWireConnections(wire));
					}));
				});

		int[] equal = {0};
		int[] neq = {0};

		possibleConnsPerWire.forEach((wire, tileTypes) -> {
			tileTypes.forEach((type, conns) -> {
				if (!allWireConnsEqual(conns.values())) {
					System.out.println(type + " " + wireEnumerator.getWireName(wire));
					conns.forEach((tile, c) -> {
						System.out.println(tile + ": " + Arrays.toString(c));
					});
					System.out.println("\n\n\n");
					neq[0]++;
				} else {
					equal[0]++;
				}
			});
		});
		System.out.println("neq = " + neq[0]);
		System.out.println("equal = " + equal[0]);
	}

	private static void countDifferentWireHashMaps(Device device) {
		Set<WireHashMap> wireMaps = new HashSet<>();
		final int[] count = {0};
		streamAllTiles(device).forEach(a -> {
			wireMaps.add(a.getWireHashMap());
			count[0]++;
		});
		System.out.println(wireMaps.size() + " different in " + count[0] + " tiles");
	}

	private static Stream<Tile> streamAllTiles(Device device) {
		return Arrays.stream(device.getTiles()).flatMap(Arrays::stream);
	}

}
