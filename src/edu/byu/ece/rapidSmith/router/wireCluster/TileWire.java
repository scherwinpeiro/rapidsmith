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

import edu.byu.ece.rapidSmith.design.PIP;
import edu.byu.ece.rapidSmith.design.Pin;
import edu.byu.ece.rapidSmith.device.PrimitiveSite;
import edu.byu.ece.rapidSmith.device.Tile;
import edu.byu.ece.rapidSmith.device.WireConnection;
import edu.byu.ece.rapidSmith.device.WireContainer;
import edu.byu.ece.rapidSmith.device.WireEnumerator;
import edu.byu.ece.rapidSmith.router.Node;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Stream;

/**
 * A wire in a specific tile
 */
public class TileWire implements WireContainer {

	public final Tile tile;
	public final int wire;

	public TileWire(Tile tile, int wire) {
		this.tile = tile;
		this.wire = wire;
	}

	public TileWire(Node node) {
		this(node.getTile(), node.getWire());
	}

	@Override
	public String toString() {
		return tile.toString() + "." + wire;
	}

	public String toString(WireEnumerator we) {
		return tile.toString() + "." + we.getWireName(wire);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof TileWire)) {
			return false;
		}

		TileWire tileWire = (TileWire) o;

		if (wire != tileWire.wire) {
			return false;
		}
		return tile.equals(tileWire.tile);
	}

	@Override
	public int hashCode() {
		int result = tile.hashCode();
		result = 31 * result + wire;
		return result;
	}

	public Stream<WireConnection> streamConnections() {
		final WireConnection[] conns = tile.getWireConnections(wire);
		if (conns == null) {
			return Stream.empty();
		}
		return Arrays.stream(conns);
	}

	public TileWire neighbour(WireConnection conn) {
		return new TileWire(conn.getTile(tile), conn.getWire());
	}

	public PIP makePip(TileWire nextTw) {
		return new PIP(tile, wire, nextTw.wire);
	}

	public Optional<Pin> toPin(BiFunction<PrimitiveSite, String, Pin> primitiveSiteMapper) {
		for (PrimitiveSite primitiveSite : tile.getPrimitiveSites()) {
			for (String pinName : primitiveSite.getPins().keySet()) {
				int wire = primitiveSite.getPins().get(pinName);
				if (wire==this.wire) {
					return Optional.of(primitiveSiteMapper.apply(primitiveSite, pinName));
				}
			}
		}
		return Optional.empty();
	}
}
