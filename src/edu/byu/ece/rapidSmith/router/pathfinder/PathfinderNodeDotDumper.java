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

import edu.byu.ece.rapidSmith.design.PIP;
import edu.byu.ece.rapidSmith.device.WireConnection;
import edu.byu.ece.rapidSmith.router.wireCluster.TileWire;
import edu.byu.ece.rapidSmith.router.wireCluster.WireCluster;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Created by wenzel on 19.04.17.
 */
public class PathfinderNodeDotDumper {

	private static String getTwName(TileWire tw) {
		return tw.toString(tw.tile.getDevice().getWireEnumerator()).replace('.', '_');
	}

	private static void createTileClusters(Collection<PathfinderNode> nodes, Collection<TileWire> tileWires, PrintWriter writer, Collection<TileWire> toMark) {
		tileWires.stream().collect(Collectors.groupingBy(tw -> tw.tile)).forEach((tile, tws) -> {
			writer.println("subgraph cluster_" + tile.getName() + " {");
			tws.forEach(tw -> {
				writer.println(getTwName(tw) + "[label=" + tw.tile.getDevice().getWireEnumerator().getWireName(tw.wire) + getTwStyle(tw, toMark) + "];");
			});
			writer.println("label = \"" + tile.getName() + "\";");
			writer.println("color=blue;");
			writer.println("}");
		});

	}

	private static void createNodeClusters(Collection<PathfinderNode> nodes, Collection<TileWire> tileWires, PrintWriter writer, Collection<TileWire> toMark) {
		nodes.forEach(node -> {
			writer.println("subgraph cluster_"+Integer.toHexString(node.hashCode())+" {");
			for (TileWire tw : node.getTileWires()) {
				writer.println(getTwName(tw) + "[label=\"" + tw.tile.getName()+"."+tw.tile.getDevice().getWireEnumerator().getWireName(tw.wire)+"\"" + getTwStyle(tw, toMark) + "];");
			}
			writer.println("color=blue;");
			writer.println("label = \"" + node.hashCode() + "\";");
			writer.println("}");
		});

	}

	public static void dumpNodes(Collection<TileWire> toMark, Collection<PathfinderNode> nodes, Path output, boolean showOtherConnected, Collection<PIP> activePips) throws IOException {
		try (final PrintWriter writer = new PrintWriter(Files.newBufferedWriter(output))) {

			writer.println("digraph G{");

			final Set<TileWire> tileWires = nodes.stream().flatMap(WireCluster::streamTileWires).collect(Collectors.toSet());

			//createTileClusters(nodes, tileWires, writer, toMark);
			createNodeClusters(nodes, tileWires, writer, toMark);

			tileWires.forEach(tw -> {
				tw.streamConnections().forEach(conn -> {
					final TileWire next = tw.neighbour(conn);
					if (tileWires.contains(next) || showOtherConnected || tw.equals(toMark)) {
						writer.println(getTwName(tw) + " -> " + getTwName(next) + getConnStyle(conn, activePips, tw) + ";");
					}
				});
			});

			writer.println("}");
		}
	}

	private static String getTwStyle(TileWire tw, Collection<TileWire> toMark) {
		if (toMark.contains(tw)) {
			return ", color=red";
		} else {
			return "";
		}
	}

	public static void dumpNodes(Collection<TileWire> toMark, Collection<PathfinderNode> nodes, Path output, boolean showOtherConnected) throws IOException {
		dumpNodes(toMark, nodes, output, showOtherConnected, null);
	}


	public static void dumpNodes(Collection<PathfinderNode> nodes, Path output, boolean showOtherConnected, Collection<PIP> activePips) throws IOException {
		dumpNodes(null, nodes, output, showOtherConnected, activePips);
	}


	public static void dumpNodes(Collection<PathfinderNode> nodes, Path output, boolean showOtherConnected) throws IOException {
		dumpNodes(null, nodes, output, showOtherConnected, null);
	}


	private static String getConnStyle(WireConnection conn, Collection<PIP> activePips, TileWire tileWire) {
		if (conn.isPIP()) {
			PIP pip = new PIP(tileWire.tile, tileWire.wire, conn.getWire());
			if (activePips != null && activePips.contains(pip)) {
				return "[color=blue]";
			} else {
				return "[color=red, style=dashed]";
			}
		}
		return "";
	}

}
