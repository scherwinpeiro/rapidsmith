package edu.byu.ece.rapidSmith.router.pathfinder;

import edu.byu.ece.rapidSmith.design.Net;
import edu.byu.ece.rapidSmith.design.NetType;
import edu.byu.ece.rapidSmith.design.Pin;
import edu.byu.ece.rapidSmith.device.Device;
import edu.byu.ece.rapidSmith.device.PrimitiveType;
import edu.byu.ece.rapidSmith.primitiveDefs.PrimitiveDef;
import edu.byu.ece.rapidSmith.primitiveDefs.PrimitiveDefPin;
import edu.byu.ece.rapidSmith.router.Node;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Created by wenzel on 04.11.16.
 */
public class NetData {

	public final Set<Pin> sources;
	public final Set<PathfinderNode> sourceNodes;
	public final Set<Pin> sinks;
	public final Set<PathfinderNode> sinkNodes;

	public final Set<PathfinderNode> routing = new HashSet<>();

	public NetData(PathfinderNodeFactory nodeFactory, Net net, Device device, PrimitiveDef tieoffDef) {
		if (net.getSource() != null) {
			sources = Collections.singleton(net.getSource());
			sourceNodes = pinSetToNodes(nodeFactory, sources);
		} else {
			sources = Collections.emptySet();
			if (net.isStaticNet()) {
				sourceNodes = findStaticSourceNodes(device, tieoffDef, net, nodeFactory);
			} else {
				sourceNodes = Collections.emptySet();
			}
		}
		sinks = net.getPins().stream().filter(p -> p != net.getSource()).collect(Collectors.toSet());

		sinkNodes = pinSetToNodes(nodeFactory, sinks);
	}

	private Set<PathfinderNode> findStaticSourceNodes(Device device, PrimitiveDef tieoffDef, Net net, PathfinderNodeFactory nodeFactory) {
		/*net.getPins().stream()
				.map(Pin::getNets)
                .map(Net::getPins)
                .flatMap(Collection::stream)
                .map(Pin::getTile)
                .distinct()
                .*/


		Set<String> pinNames = tieoffDef.getPins().stream().filter(pin -> {
			return (pin.getExternalName().endsWith("1") && net.getType() == NetType.VCC)
					|| (pin.getExternalName().endsWith("0") && net.getType() == NetType.GND);
		}).map(PrimitiveDefPin::getExternalName).collect(Collectors.toSet());


		//TODO filter for relevant ones
		return Arrays.stream(device.getAllPrimitiveSitesOfType(PrimitiveType.TIEOFF))
				.flatMap(ps -> pinNames.stream().map(pin -> nodeFactory.getNode(ps.getTile(), ps.getExternalPinWireEnum(pin))))
				.collect(Collectors.toSet());
	}

	private Set<PathfinderNode> pinSetToNodes(PathfinderNodeFactory nodeFactory, Set<Pin> pins) {
		return pins.stream().map(nodeFactory::getNode).collect(Collectors.toSet());
	}

	private Set<PathfinderNode> pinSetToSwitchMatrixNodes(PathfinderNodeFactory nodeFactory, Set<Pin> pins, Device device) {
		return pins.stream().map(pin -> {
			final Node node = device.getSwitchMatrixSink(pin);
			if (node.getWire() >= 0) {
				return nodeFactory.getNode(node.getTile(), node.getWire());
			} else {
				return nodeFactory.getNode(pin);
			}
		}).collect(Collectors.toSet());
	}
}
