/*
 * Copyright (c) 2010 Brigham Young University
 *
 * This file is part of the BYU RapidSmith Tools.
 *
 * BYU RapidSmith Tools is free software: you may redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation, either version 2 of
 * the License, or (at your option) any later version.
 *
 * BYU RapidSmith Tools is distributed in the hope that it will be
 * useful, but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * A copy of the GNU General Public License is included with the BYU
 * RapidSmith Tools. It can be found at doc/gpl2.txt. You may also
 * get a copy of the license at <http://www.gnu.org/licenses/>.
 */

package edu.byu.ece.rapidSmith.timing;


import edu.byu.ece.rapidSmith.design.*;
import edu.byu.ece.rapidSmith.device.PrimitiveType;
import edu.byu.ece.rapidSmith.device.WireConnection;
import edu.byu.ece.rapidSmith.primitiveDefs.*;
import edu.byu.ece.rapidSmith.router.InstanceElement;
import edu.byu.ece.rapidSmith.router.Node;
import edu.byu.ece.rapidSmith.router.RoutingElement;

import java.io.*;
import java.util.*;

public class DotDumper {

	private static String getNodeName(Node node, Design design) {
		//We don't want to use node.toString here as that includes the level. Therefore, if a wire appears multiple times on different levels, it would generate different nodes in the graph.
		return "\""+node.getTile() + " " + (node.getWire() == -1 ? "-1" : design.getWireEnumerator().getWireName(node.getWire()))+"\"";
	}


	private static Pin getPin(Node node, Set<Pin> unseenPins, Design design, Net net, boolean noremove) {
		for (Pin p : unseenPins) {
			if (p.getInstance().getTile().equals(node.getTile()) && design.getDevice().getPrimitiveExternalPin(p)==node.getWire()) {
				if (!noremove)
					unseenPins.remove(p);
				return p;
			}
		}
		for (Pin p : net.getPins()) {
			if (p.getInstance().getTile().equals(node.getTile()) && design.getDevice().getPrimitiveExternalPin(p)==node.getWire()) {
				return p;
			}
		}

		return null;
	}

	private static String escapeName(Pin p) {
		return p.toString().replace("\"", "\\\"");
	}

	private static void dumpNode(Node node, PrintWriter writer, Design design, Set<Pin> unseenPins, boolean wasUnseen, Net net, boolean isDisabledPIP) {
		if (node.isPIP()) {
			if (isDisabledPIP)
				writer.println(getNodeName(node, design) + " [shape=octagon];");
			else
				writer.println(getNodeName(node, design) + " [shape=box];");

		}

		Pin p = getPin(node, unseenPins, design,net, wasUnseen);
		if (p != null)
			writer.println(getNodeName(node, design) + " [style=filled, label = \"" + escapeName(p) + "\\n" + node.toString(design.getWireEnumerator()) + "\"];");
		else
			writer.println(getNodeName(node, design) + " [label = \"" + node.toString(design.getWireEnumerator()) + "\"];");
		if (wasUnseen)
			writer.println(getNodeName(node, design) + " [color = red];");


		if (node.getParent()!=null)
			writer.println(getNodeName(node.getParent(), design)+" -> "+getNodeName(node,design)+";");
	}

	private static void dumpConnected(WireConnection w, Design d, Node parent, Net net, PrintWriter writer, Set<Node> seen, boolean dumpDisabledPIPs, Set<Pin> unseenPins) {



		Node currPathNode = new Node(w.getTile(parent.getTile()), w.getWire(), parent, parent.getLevel()+1, w.isPIP());


		if (currPathNode.isPIP()) {
			PIP ref = new PIP(currPathNode.getTile(), currPathNode.getParent().getWire(), currPathNode.getWire());
			if (!net.getPIPs().contains(ref)) {
				if (dumpDisabledPIPs)
					dumpNode(currPathNode, writer, d, unseenPins, false, net, true);
				return;
			}
		}

		dumpNode(currPathNode, writer, d, unseenPins, false, net, false);

		Node seenNode = new Node(w.getTile(parent.getTile()), w.getWire(), null, 0);
		if (seen.add(seenNode))
			if (currPathNode.getConnections()!=null)
				for (WireConnection wireConnection : currPathNode.getConnections()) {
					dumpConnected(wireConnection,d,currPathNode, net, writer, seen, dumpDisabledPIPs, unseenPins);
				}


	}

	public static void dumpNets(Collection<Net> nets, Design design, File out, boolean dumpDisabledPIPs) throws FileNotFoundException {
		PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(out)));
		try {

			writer.println("digraph G {");

			int i=0;
			for (Net net : nets) {

				Pin source = null;
				for (Pin p : net.getPins()) {
					if (p.isOutPin()) {
						source = p;
						break;
					}
				}


				if (source == null)
					continue;

				writer.println("subgraph cluster"+i+++" {");
				writer.println("label = \""+net.getName()+"\";");
				writer.println("color = black;");
						
				Node n = new Node(source.getInstance().getTile(),
						design.getDevice().getPrimitiveExternalPin(source), null, 0);
				if (n.getConnections()!=null) {

					Set<Pin> unseenPins = new HashSet<Pin>(net.getPins());
					dumpNode(n, writer, design, unseenPins, false, net, false);
					HashSet<Node> seen = new HashSet<Node>();
					for (WireConnection c : n.getConnections()) {
						dumpConnected(c, design, n, net, writer, seen, dumpDisabledPIPs, unseenPins);

					}

					for (Pin p: unseenPins) {

						Node np = new Node(p.getInstance().getTile(),
								design.getDevice().getPrimitiveExternalPin(p), null, 0);
						dumpNode(np, writer, design, unseenPins, true, net, false);

						System.out.println("Dumping unseen pin "+p+" on net "+net.getName()+" wire: "+np);
						if (source==null)
							System.out.println("Source is null");
						else
							System.out.println("source is "+source+" on net "+net.getName());


					}
				}

				writer.println("}");

			}


			writer.println("}");
		} finally {
			writer.close();
		}
	}

	private static boolean isPinEnabled(PrimitiveDefList primitives, Instance inst, Element element, PrimitiveDefPin pin) {
		if (inst==null)
			return true;
		InstanceElement ie = inst.getInstanceElement(element,primitives);
		return ie.isPinEnabled(pin,primitives);
	}

	public static void dumpInstance(PrimitiveDefList primitives, PrimitiveType type, Instance inst, File out) throws FileNotFoundException {
		PrimitiveDef primitive = primitives.getPrimitiveDef(type);
		PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(out)));
		try {
			writer.println("digraph G {");
			writer.println("concentrate=true");

			Map<String,PrimitiveDefPin> extPins = new HashMap<String, PrimitiveDefPin>();
			for (PrimitiveDefPin pin : primitive.getPins()) {
				extPins.put(pin.getInternalName(), pin);
				writer.println(pin.getInternalName()+";");
			}


			for (Element e: primitive.getElements()) {
				/*if (extPins.get(e.getName())!=null  || e.getConnections().size()==0)
					continue;*/
				writer.println("\"" + e.getName() + "\" [shape=none,margin=0,width=0,height=0,label=<");

				InstanceElement ie;
				if (inst!=null) {
					ie = inst.getInstanceElement(e, primitives);
				} else {
					ie = new InstanceElement(e,primitive,primitives,null);
				}

				String disable;
				if (ie.isDisabled())
					disable = " BGCOLOR=\"GRAY\"";
				else disable = "";

				writer.println("<TABLE BORDER=\"0\" CELLBORDER=\"0\" CELLSPACING=\"0\" CELLPADDING=\"0\" "+disable+">");
				printPins(writer, e,inst,primitives,extPins,false);
				String color="";

				if (ie.isRegister(primitives)!= RoutingElement.RegisterType.COMBINATORIAL &&
						ie.isRegister(primitives)!= RoutingElement.RegisterType.OFF)
					color=" BGCOLOR=\"RED\"";

				writer.println("<TR><TD BORDER=\"1\" CELLPADDING=\"4\" "+color+">");

				writer.println(e.getName());
				if (e.isBel())
					writer.println(" BEL");
				if (ie.isRegister(primitives)!= RoutingElement.RegisterType.COMBINATORIAL)
					writer.println(" "+ie.isRegister(primitives));

				writer.println("</TD></TR>");

				if (e.getCfgOptions()!=null && inst!=null) {
					for (String s : e.getCfgOptions()) {
						writer.println("<TR><TD BORDER=\"1\" CELLPADDING=\"4\">cfg: " + htmlescape(s) + "</TD></TR>");
					}
				}
				if (inst!=null) {
					Attribute a = inst.getAttribute(e.getName());
					writer.println("<TR><TD BORDER=\"1\" CELLPADDING=\"4\">attr: " + htmlescape(a) + "</TD></TR>");

					writer.println("<TR><TD BORDER=\"1\" CELLPADDING=\"4\">isClock: " + htmlescape(ie.isClock()) + "</TD></TR>");
				}

				printPins(writer,e,inst,primitives,extPins,true);

				writer.println("</TABLE>>];");


			}
			Set<Connection> seenConnections = new HashSet<Connection>();
			for (Element e: primitive.getElements()) {

				for (Connection conn : e.getConnections()) {
					if (seenConnections.add(conn)) {

						if (conn.isForwardConnection()) {
							String fromName = conn.getElement0()+":"+conn.getPin0();
							String toName = conn.getElement1()+":"+conn.getPin1();

							boolean en = isConnEnabled(inst, conn, primitive,primitives);

							String style="label=\""+conn.getDelay()+"\"";
							if (!en) style+=",style=dotted";
							if (Double.isInfinite(conn.getDelay()))
								style+=",color=blue";
							writer.println(fromName + " -> " + toName+"["+style+"];");
						}
					} else System.out.println("conn already seen: "+conn);
				}
			}


			writer.println("}");
		} finally {
			writer.close();
		}
	}

	private static String htmlescape(Object a) {
		if (a!=null)
			return htmlescape(a.toString());
		else return "null";
	}

	private static boolean isConnEnabled(Instance inst, Connection conn, PrimitiveDef primitive, PrimitiveDefList primitives) {

		if (inst==null)
			return true;

		Element elem0 = conn.getElement0(primitive);
		Element elem1 = conn.getElement1(primitive);

		return isPinEnabled(primitives,inst,elem0,conn.getPin0(primitive))
				&& isPinEnabled(primitives,inst,elem1,conn.getPin1(primitive));
	}

	private static String htmlescape(String s) {
		return s.replace("<","&lt;").replace(">","&gt;");
	}

	private static void printPins(PrintWriter writer, Element e, Instance instance, PrimitiveDefList primitives, Map<String, PrimitiveDefPin> extPins, boolean outputs) {
		writer.println("<TR><TD><TABLE BORDER=\"0\" CELLBORDER=\"1\" CELLSPACING=\"0\" CELLPADDING=\"4\"><TR>");
		boolean nopins = true;
		for (PrimitiveDefPin pin : e.getPins()) {
			if (pin.isOutput()==outputs) {
				nopins=false;
				String color="";
				if (!isPinEnabled(primitives,instance,e,pin))
					color="BGCOLOR=\"GRAY\"";

				writer.println("<TD "+color+" PORT=\""+pin.getInternalName()+"\">"+pin.getInternalName()+"</TD>");
			}
		}
		if (nopins) {
			writer.println("<TD> </TD>");
		}
		writer.println("</TR></TABLE></TD></TR>");
	}

	private static File getDumpDir() {
		final File dumps = new File("dumps");
		dumps.mkdirs();
		return dumps;
	}

	public static void dumpInstance(Instance inst, PrimitiveDefList primitives, File out) throws FileNotFoundException {
		dumpInstance(primitives, inst.getType(), inst, out);
	}

	private static Set<Instance> dumped = new HashSet<>();
	public static void dumpInstanceOnce(Instance inst, PrimitiveDefList primitives) throws FileNotFoundException {
		if (dumped.add(inst)) {
			dumpInstance(inst, primitives, new File(getDumpDir(), inst.getName().replace('/', '_') + ".dot"));
		}
	}
	public static void dumpInstance(Instance inst, PrimitiveDefList primitives) throws FileNotFoundException {
		if (inst==null)
			throw new RuntimeException("instance is null!");
		dumpInstance(inst,primitives,new File(getDumpDir(),inst.getName().replace('/','_')+".dot"));
	}
	public static void dumpPrimitiveDef(PrimitiveDefList primitives, PrimitiveType primitive, File out) throws FileNotFoundException {
		dumpInstance(primitives, primitive, null, out);
	}
}
