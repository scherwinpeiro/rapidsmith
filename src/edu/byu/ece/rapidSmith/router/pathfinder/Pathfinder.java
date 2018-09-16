package edu.byu.ece.rapidSmith.router.pathfinder;


import de.tu_darmstadt.rs.util.tracer.Tracer;

import edu.byu.ece.rapidSmith.design.Attribute;
import edu.byu.ece.rapidSmith.design.Design;
import edu.byu.ece.rapidSmith.design.Instance;
import edu.byu.ece.rapidSmith.design.Net;
import edu.byu.ece.rapidSmith.design.NetType;
import edu.byu.ece.rapidSmith.design.PIP;
import edu.byu.ece.rapidSmith.design.Pin;
import edu.byu.ece.rapidSmith.device.PrimitiveSite;
import edu.byu.ece.rapidSmith.device.PrimitiveType;
import edu.byu.ece.rapidSmith.device.WireContainer;
import edu.byu.ece.rapidSmith.primitiveDefs.PrimitiveDef;
import edu.byu.ece.rapidSmith.primitiveDefs.PrimitiveDefDetails;
import edu.byu.ece.rapidSmith.primitiveDefs.PrimitiveDefList;
import edu.byu.ece.rapidSmith.primitiveDefs.PrimitiveDefPin;
import edu.byu.ece.rapidSmith.router.InstanceElement;
import edu.byu.ece.rapidSmith.router.RoutingElement;
import edu.byu.ece.rapidSmith.timing.TimingCalculator;
import edu.byu.ece.rapidSmith.timing.TimingCalibration;
import edu.byu.ece.rapidSmith.timing.logic.IsolatedDelayDesignCreator;
import edu.byu.ece.rapidSmith.timing.routing.DelayModel;
import edu.byu.ece.rapidSmith.util.DeviceDatabaseProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * The Pathfinder Router
 */
public class Pathfinder {

	private static final Logger logger = LoggerFactory.getLogger(Pathfinder.class);

	private final Design design;
	private final PathfinderNodeFactory pathfinderNodeFactory;
	private final NodeCostFactory nodeCostFactory;
	private final TimingCalculator timingCalculator;
	private final PrimitiveDefList primitiveDefs;
	private final boolean keepExistingRoutes;

	private Map<Net, NetData> netDatas = new HashMap<>();

	private int localIteration;
	private PrintWriter netLog;

	private final IsolatedDelayDesignCreator isolatedDelayDesignCreator;

	Pathfinder(Design design, PathfinderNodeFactory pathfinderNodeFactory, NodeCostFactory nodeCostFactory, TimingCalculator timingCalculator, PrimitiveDefList primitiveDefs, boolean keepExistingRoutes) {

		this.design = design;
		this.pathfinderNodeFactory = pathfinderNodeFactory;
		this.nodeCostFactory = nodeCostFactory;
		this.timingCalculator = timingCalculator;
		this.primitiveDefs = primitiveDefs;
		this.keepExistingRoutes = keepExistingRoutes;
		isolatedDelayDesignCreator = new IsolatedDelayDesignCreator(design, primitiveDefs, Paths.get("isolatedDelays"));
	}


	public Pathfinder(Design design, TimingCalculator timingCalculator, PrimitiveDefList primitiveDefs, boolean keepExistingRoutes) {
		this.design = design;
		pathfinderNodeFactory = new PathfinderNodeFactory(design.getDevice());
		this.timingCalculator = timingCalculator;
		this.primitiveDefs = primitiveDefs;
		this.keepExistingRoutes = keepExistingRoutes;
		nodeCostFactory = new NodeCostFactory();
		isolatedDelayDesignCreator = new IsolatedDelayDesignCreator(design, primitiveDefs, Paths.get("isolatedDelays"));
	}

	public void routeDesign() {
		Tracer.enterLoop("Route Design");
		unrouteable = false;
		//First, we find out which nets actually need to be routed
		Set<Net> netsToRoute;
		try (Tracer ignored = Tracer.enter("Nets to routs")) {
			netsToRoute = findNetsToRoute(HashSet::new);
		}
		//Put all static nets into a single net with many sources
		try (Tracer ignored = Tracer.enter("Merge static nets")) {
			mergeStaticNets(netsToRoute);
		}
		try (Tracer ignored = Tracer.enter("Init net data")) {
			initNetData(netsToRoute);
		}

		//Block routethroughs that are used for other stuff
		restrictRoutethroughs(primitiveDefs);


		logger.info("Routing {} nets", netsToRoute.size());
		routeAllNets(netsToRoute);

		//Pull static nets apart again
		separateStaticNets(netsToRoute);

		/*netsToRoute.forEach(net -> {
			try {
				NetData netData = netDatas.get(net);
				Files.createDirectories(Paths.get("dumps"));
				final Set<TileWire> marked = Stream.of(netData.sourceNodes, netData.sinkNodes)
						.flatMap(Collection::stream)
						.map(WireCluster::getTileWires)
						.flatMap(Collection::stream)
						.collect(Collectors.toSet());
				PathfinderNodeDotDumper.dumpNodes(marked, netData.routing, Paths.get("dumps/" + toFileName(net.getName()) + ".dot"), false, net.getPIPs());
			} catch (IOException e) {
				e.printStackTrace();
			}
		});*/

		Tracer.exit();
	}

	/**
	 * During routing, static nets are collected into two nets (one for vcc, one for gnd) with many sources. Separate
	 * them into nets with a single source
	 * @param netsToRoute
	 */
	private void separateStaticNets(Set<Net> netsToRoute) {
		Tracer.enter("Separate static nets");
		netsToRoute.forEach(net -> {
			if (net.getType() != NetType.WIRE) {
				separateStaticNet(net);
			}
		});
		Tracer.exit();

	}

	/**
	 * During routing, static nets are collected into two nets (one for vcc, one for gnd) with many sources. Separate
	 * a static net into nets with a single source
	 * @param net
	 */
	private void separateStaticNet(Net net) {
		NetData netData = netDatas.get(net);
		final int[] i = {0};
		netData.sourceNodes.forEach(sourceNode -> {
			//Is the node used?
			if (netData.routing.contains(sourceNode)) {
				//Split into a new net
				Net split = new Net(net.getName() + "_" + i[0]++, net.getType());

				Pin sourcePin = getStaticSourcePin(sourceNode);

				split.addPin(sourcePin);
				split.setSource(sourcePin);
				sourcePin.setNet(split);

				traverseStaticSplitNet(net, netData, split, sourceNode);
				design.addNet(split);
			}
		});
		design.removeNet(net);
	}

	/**
	 * Traverse a static net to add pips and pins to a new separated net
	 *
	 * @param net
	 * @param netData
	 * @param split
	 * @param sourceNode
	 */
	private void traverseStaticSplitNet(Net net, NetData netData, Net split, PathfinderNode sourceNode) {
		ArrayList<PIP> pips = new ArrayList<>();
		findNetPips(netData.routing, net.getPIPs(), sourceNode, pips::add, sinkNode -> {
			Pin sinkPin = sinkNode.toPin((primitiveSite, pinName) -> design.getInstanceAtPrimitiveSite(primitiveSite).getPin(pinName))
					.orElseThrow(() -> new RuntimeException("Pin not found"));
			split.addPin(sinkPin);
			sinkPin.setNet(split);
		});
		split.setPIPs(pips);

	}

	/**
	 * Make a static source pin from a PathfinderNode
	 * @param sourceNode
	 * @return
	 */
	private Pin getStaticSourcePin(PathfinderNode sourceNode) {
		return sourceNode.toPin((primitiveSite, pinName) -> {
			Instance instance = design.getInstanceAtPrimitiveSite(primitiveSite);
			if (instance == null) {
				instance = createStaticSourceInstance(primitiveSite);
			}
			Pin pin = instance.getPin(pinName);
			if (pin == null) {
				pin = new Pin(true, pinName, instance);
				instance.addPin(pin);
				String type;
				if (pinName.endsWith("0")) {
					type = "GND";
				} else {
					type = "VCC";
				}
				instance.addAttribute(new Attribute("_" + type + "_SOURCE", "", pinName));
			}
			return pin;
		}).orElseThrow(() -> new RuntimeException("Pin not found"));
	}

	/**
	 * Create an instance for a static source pin
	 * @param primitiveSite
	 * @return
	 */
	private Instance createStaticSourceInstance(PrimitiveSite primitiveSite) {
		Instance instance = new Instance("STATIC_SOURCE_" + primitiveSite.getName(), primitiveSite.getType());
		design.addInstance(instance);
		instance.place(primitiveSite);
		instance.addAttribute(new Attribute("_NO_USER_LOGIC", "", ""));
		return instance;
	}

	private String toFileName(String name) {
		return name.replaceAll("[^A-Za-z0-9]", "_");
	}

	/**
	 * Add the PIPs to a net, starting at a specified source node
	 *
	 * @param routing the routing
	 * @param sourceNode the source node
	 */
	private void findNetPips(Set<PathfinderNode> routing, Collection<PIP> allowedPIPs, PathfinderNode sourceNode, Consumer<PIP> pipConsumer, Consumer<PathfinderNode> sinkConsumer) {
		Deque<PathfinderNode> queue = new ArrayDeque<>();
		queue.add(sourceNode);

		Set<PathfinderNode> seen = new HashSet<>();

		PathfinderNode current;
		while ((current = queue.poll()) != null) {
			final PathfinderNode[] neighbours = current.getNeighbours(pathfinderNodeFactory);
			if (neighbours.length == 0) {
				sinkConsumer.accept(current);
			}
			for (PathfinderNode next : neighbours) {
				//Only add new nodes
				if (routing.contains(next)) {
					PIP pip = current.createPip(next);
					if (allowedPIPs.contains(pip)) {
						pipConsumer.accept(pip);
						queue.add(next);
					}
				}
			}
		}
	}

	private void routeAllNets(Set<Net> netsToRoute) {
		Set<Net> iterationNets = netsToRoute;
		int globalIterations = 0;
		Set<PathfinderNode> overusedNodes = new HashSet<>();

		while (!iterationNets.isEmpty() && globalIterations < 100) {
			Tracer.enterLoop("Global Iteration");
			//Set<Net> nextIterationNets = new HashSet<>();


			//TODO debug, remove
			double[] estimatedSum = {0};
			double[] actualSum = {0};
			int[] count = {0};
			BiConsumer<Double, Double> resultComparator = (estimated, actual) -> {
				estimatedSum[0] += estimated;
				actualSum[0] += actual;
				count[0]++;
			};
			int[] expandedNodes = {0};
			Consumer<Integer> expandedNodesConsumer = (e) -> {
				expandedNodes[0] += e;
			};


			long iterationBegin = System.nanoTime();
			for (Net net : iterationNets) {
				long netBegin = System.nanoTime();
				routeSingleNet(net, globalIterations, resultComparator, expandedNodesConsumer, overusedNodes);
				long netEnd = System.nanoTime();
				logNetTime(net.getName(), globalIterations, netEnd - netBegin);
			}
			long iterationEnd = System.nanoTime();
			long duration = iterationEnd - iterationBegin;


			long taBegin = System.nanoTime();
			try (Tracer ignored = Tracer.enterLoop("Timing analysis")) {
				netsToRoute.forEach(net -> {
					net.getSource().clearDelayCache();
				});
				timingCalculator.calculateTimings(this::unknownDelay);
			}
			long taEnd = System.nanoTime();
			long taDuration = taEnd - taBegin;

			timingCalculator.getCriticalPaths(1E-7).forEach(System.out::println);

			globalIterations++;
			iterationNets = overusedNodes.stream().flatMap(n -> n.getNets().stream()).collect(Collectors.toSet());
			logger.info("Iteration {}: {} nodes in {} nets with overuse, {}s route, {}s timing analysis, {}M expanded nodes, per node: {}µs", globalIterations, overusedNodes.size(), iterationNets.size(), duration * 1E-9, taDuration * 1E-9, expandedNodes[0] / 1000000f, duration * 1E-3 / expandedNodes[0]);

			if (iterationNets.size() < 10) {
				logger.info("to route: {}", iterationNets);
			}

			double underestimate = estimatedSum[0] / actualSum[0];
			logger.info("underestimation factor {}", underestimate);
			printStatus();

			if (unrouteable) {
				throw new RuntimeException("Design is not routable!");
			}

			//checkOveruse(nextIterationNets);

			Tracer.exit();
		}

		if (!iterationNets.isEmpty()) {
			throw new RuntimeException("Failed to find a solution within iteration limit.");
		} else {
			logger.info("Converged after {} iterations", globalIterations);
		}
	}

	private void unknownDelay(RoutingElement from, RoutingElement to) {
		logger.warn("Unknown delay from "+from+" to "+to);
		isolatedDelayDesignCreator.saveIsolatedDesignOnce(from.getInstance());
	}

	private void logNetTime(String name, int globalIterations, long l) {
		if (netLog != null) {
			netLog.println(name + "," + globalIterations + "," + l);
		}
	}

	protected void restrictRoutethroughs(PrimitiveDefList primitives) {
		Tracer.enter("Restrict Routethroughs");
		for (Instance instance : design.getInstances()) {

			//Not using getPins, because that only gives us used pins, not all pins
			//e.g. if a slice's A1-A3 are used, we need to block A4-A6 as well
			primitives.getPrimitiveDef(instance.getType())
					.getPins()
					.stream()
					.filter(pin -> !pin.isOutput())
					.forEach(pin -> {
						PathfinderNode node = pathfinderNodeFactory.getNode(instance.getTile(), design.getDevice().getPrimitiveExternalPin(instance, pin.getExternalName()));
						if (!isRoutethroughAllowed(instance, pin, node, primitives)) {
							node.setSinkNode(true);
						}
					});

		}
		Tracer.exit();
	}

	/**
	 * Can we use a routethrough?
	 * @param instance
	 * @param primitiveDefPin
	 * @param node
	 * @param primitives
	 * @return false if the resource that we would route through is used in the design
	 */
	private boolean isRoutethroughAllowed(Instance instance, PrimitiveDefPin primitiveDefPin, PathfinderNode node, PrimitiveDefList primitives) {
		//If the pin is in use, we disallow routethroughs
		Pin p = instance.getPin(primitiveDefPin.getExternalName());
		if (p != null) {
			return false;
		}

		//If the pin connects to internal elements that are enabled, we disallow the routethrough
		PrimitiveDef def = primitives.getPrimitiveDef(instance.getType());
		InstanceElement instanceElement = instance.getInstanceElement(def.getElement(primitiveDefPin.getInternalName()), primitives);
		boolean anySuccessorEnabled = instanceElement.getElement().getConnections().stream()
				.map(connection -> instance.getInstanceElement(connection.getElement1(), primitives))
				.anyMatch(next -> {
					Attribute attribute = next.getAttribute();
					return attribute != null && !attribute.getValue().equals("#OFF");
				});

		if (anySuccessorEnabled) {
			return false;
		}

		//TODO any other cases?

		return true;
	}

	private void initNetData(Collection<Net> nets) {
		PrimitiveDef tieoffDef = primitiveDefs.getPrimitiveDef(PrimitiveType.TIEOFF);
		nets.forEach(n -> netDatas.put(n, new NetData(pathfinderNodeFactory, n, design.getDevice(), tieoffDef)));
	}

	/**
	 * This merges all static nets into a single vcc and a single gnd net
	 *
	 * @param netsToRoute
	 */
	private void mergeStaticNets(Collection<Net> netsToRoute) {
		Net vccNet = new Net("GLOBAL_LOGIC1", NetType.VCC);
		Net gndNet = new Net("GLOBAL_LOGIC0", NetType.GND);

		for (Iterator<Net> it = netsToRoute.iterator(); it.hasNext(); ) {
			Net net = it.next();

			if (net.isStaticNet()) {
				//Maybe a source was already assigned. Delete that
				Pin source = net.getSource();
				if (source != null) {
					net.removePin(source);
					source.getInstance().removePin(source);
				}

				//Move the sink pins to the correct net
				if (net.getType() == NetType.VCC) {
					if (!vccNet.addPins(net.getPins())) {
						logger.warn("Could not add all pins from net " + net);
					}
				} else {
					if (!gndNet.addPins(net.getPins())) {
						logger.warn("Could not add all pins from net " + net);
					}
				}
				it.remove();
				design.removeNet(net);
			}
		}

		if (!vccNet.getPins().isEmpty()) {
			netsToRoute.add(vccNet);
			design.addNet(vccNet);
		}

		if (!gndNet.getPins().isEmpty()) {
			netsToRoute.add(gndNet);
			design.addNet(gndNet);
		}


	}

	protected void printStatus() {

	}

	private boolean unrouteable = false;


	private void checkOveruse(Collection<Net> overuseNets) {
		/*Map<PathfinderNode,Set<Net>> overusedNodes = new HashMap<>();
		netDatas.forEach((net,netData) -> {
            netData.routing.forEach(node -> {
                overusedNodes.computeIfAbsent(node,x-> new HashSet<>()).add(net);
            });
        });
        overusedNodes.keySet().stream()
                .filter(s->overusedNodes.get(s).size()>1)
                .forEach(node -> {
                    overusedNodes.get(node).stream()
                            .filter(n->!overuseNets.contains(n))
                            .forEach(net -> {
                                logger.error(net+" has overuse at "+node.toString(design.getWireEnumerator())+" but is not in list");
                                logger.error("nets using this node: "+overusedNodes.get(node).stream().filter(n->n!=net).map(Object::toString).collect(Collectors.joining(",")));
                            });
                });

        netDatas.forEach((net,nd) -> {
            nd.routing.forEach(node -> {
                if (node.getCurrentUsage()==0 || !node.getNets().contains(net))
                    throw new RuntimeException("Node "+node.toString(design.getWireEnumerator())+" is used in net "+net+" but usage is "+node.getCurrentUsage()+" "+node.getNets());
            });
        });*/
	}

	/**
	 * Route all sinks of one net
	 *
	 * @param net The net to route
	 * @param globalIteration
	 * @param resultComparator
	 * @return true if there is overuse on the current net
	 */
	private void routeSingleNet(Net net, int globalIteration, BiConsumer<Double, Double> resultComparator, Consumer<Integer> expandedNodesConsumer, Set<PathfinderNode> overusedNodes) {
		Tracer.enterLoop("Route Net");
		//boolean overuse = false;
		NetData netData = netDatas.get(net);

		//Unroute: Decrease usage
		unrouteNet(net, globalIteration, netData, overusedNodes);

		if (netData.sourceNodes.isEmpty() && !net.getPins().isEmpty()) {
			logger.warn("No source nodes for net " + net.getName());
		}


		//TODO order by criticality
		for (PathfinderNode sinkNode : netData.sinkNodes) {
			routeSink(net, globalIteration, resultComparator, expandedNodesConsumer, overusedNodes, netData, sinkNode);
		}
		Tracer.exit();
	}

	private void routeSink(Net net, int globalIteration, BiConsumer<Double, Double> resultComparator, Consumer<Integer> expandedNodesConsumer, Set<PathfinderNode> overusedNodes, NetData netData, PathfinderNode sinkNode) {
		Tracer.enterLoop("Route sink");
		localIteration++;

		//Initialize priority queue
		PriorityQueue<NodeCost> queue = new PriorityQueue<>();

		Set<PathfinderNode> startNodes;
		if (netData.routing.isEmpty()) {
			startNodes = netData.sourceNodes;
		} else {
			//Either start anywhere on the existing routing or at another source node
			startNodes = new HashSet<>(netData.routing);
			startNodes.addAll(netData.sourceNodes);
		}

		startNodes.forEach(node -> queue.add(nodeCostFactory.getNodeCost(node, null, 0, estimateRemainingCost(node, sinkNode))));

		NodeCost current;
		boolean found = false;

		int expandedNodes = 0;

		//Nodes may occur multiple times in our queue. Removing duplicates or invalidated items is O(N), whereas
		//checking if we already saw a node is O(1).

		//We count the routing iterations. In each node, we save the last localIteration we saw it in. If the counts
		//are equal, we already expanded it in this localIteration.
		while ((current = queue.poll()) != null) {
			//Did we already see the node?
			if (current.isValid() && current.getNode().getSeenInLocalIteration() != localIteration) {
				expandedNodes++;

				current.getNode().setSeenInLocalIteration(localIteration);

				current.getNode().setParent(current.getParent());

				if (current.getNode() == sinkNode) { //PathfinderNodes are unique, so we are allowed to use == instead of equals!

					if (!verifyRemainingCostEstimate(sinkNode, resultComparator)) {
						unrouteable = true;
					}

					addRouting(netData, sinkNode, net, overusedNodes);
					found = true;
					break;

				} else {
					enqueueNextNodes(globalIteration, sinkNode, queue, current, globalIteration);
				}
			}
			nodeCostFactory.recycle(current);
		}
		if (!found) {

			Pin pin = net.getPins().stream().filter(p -> pathfinderNodeFactory.getNode(p) == sinkNode).findAny().orElseThrow(() -> new RuntimeException("did not find sink"));

			logger.error("Could not find a route to " + pin + " (" + sinkNode.toString(design.getWireEnumerator()) + ") on net " + net.getName() + " " + net.getType());
			logger.error("Sources: " + WireContainer.stringMapper(startNodes, design.getWireEnumerator()));
			logger.error("Sources in same tile: " + startNodes.stream().filter(n -> n.getTileWires().stream().anyMatch(t -> t.tile == pin.getTile())).map(Object::toString).collect(Collectors.joining(" ")));
			unrouteable = true;

		}


		//Recycle remaining elements of queue
		nodeCostFactory.recycleAll(queue);

		expandedNodesConsumer.accept(expandedNodes);

		Tracer.exit();
	}

	private void enqueueNextNodes(int globalIteration, PathfinderNode sinkNode, PriorityQueue<NodeCost> queue, NodeCost current, int iteration) {
		for (PathfinderNode next : current.getNode().getNeighbours(pathfinderNodeFactory)) {

			double cost = getCost(current, next, sinkNode, globalIteration);
			double remaining = estimateRemainingCost(next, sinkNode);


			//Does it already have a nodeCost assigned?
			if (next.getNodeCostIteration() == localIteration) {
				NodeCost existing = next.getCurrentNodeCost();
				//Is the existing node cost better?
				if (existing.getCost() < cost || existing.getCost() + existing.getEstimatedRemaining() < cost + remaining) {
					//Then don't add a new one
					continue;
				} else {
					//Invalidate the existing one.
					//Removing is O(N), invalidating is O(1)
					existing.invalidate();
				}
			} else {
				next.updateGlobalIteration(globalIteration);
			}

			queue.add(nodeCostFactory.getNodeCost(next, current.getNode(), cost, remaining));
		}
	}

	private void unrouteNet(Net net, int globalIteration, NetData netData, Set<PathfinderNode> overusedNodes) {
		for (PathfinderNode node : netData.routing) {
			node.updateGlobalIteration(globalIteration);
			node.getNets().remove(net);
			node.setCurrentUsage(node.getCurrentUsage() - 1);
			if (node.getCurrentUsage() == 1) {
				overusedNodes.remove(node);
			}
		}
		netData.routing.clear();
		net.getPIPs().clear();
	}


	int addCount = 0;

	/**
	 * Save the used routing
	 *
	 * @param netData the netData to save to
	 * @param sinkNode Current sink node
	 * @return true if there is overuse on the current net
	 */
	private void addRouting(NetData netData, PathfinderNode sinkNode, Net net, Set<PathfinderNode> overusedNodes) {
		PathfinderNode node = sinkNode;
		Tracer.enterLoop("Add Routing");
		List<PathfinderNode> toDump = new ArrayList<>();
		while (node != null) {
			//If we are not routing the first sink, we may reach a point where the rest of the parents are already
			//added. In that case, skip adding the rest
			if (!netData.routing.add(node)) {
				break;
			}

			int oldUsage = node.getCurrentUsage();

			//If there was only one usage, add that net to the reroute list
			if (oldUsage == 1) {
				overusedNodes.add(node);
			}

			node.getNets().add(net);
			toDump.add(node);

			node.setCurrentUsage(oldUsage + 1);

			PathfinderNode parent = node.getParent();


			if (parent != null) {
				net.addPIP(parent.createPip(node));
			}

			node = parent;
		}

		Tracer.exit();
		/*if (addCount < 100) {
			try {
				//Pin pin = net.getPins().stream().filter(p -> pathfinderNodeFactory.getNode(p) == sinkNode).findAny().orElseThrow(() -> new RuntimeException("did not find sink"));

				PathfinderNodeDotDumper.dumpNodes(toDump, Paths.get("/home/wenzel/dump" + addCount + ".dot"), false);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		addCount++;*/

	}

	/**
	 * Get the delay of a single connection
	 * @param current the current node
	 * @param next the next node
	 * @return the delay
	 */
	protected double getNodeDelay(PathfinderNode current, PathfinderNode next) {
		//TODO
		return 1;
	}

	/**
	 * Is a connection clock?
	 * @param current the current node
	 * @param sink the next node
	 * @return true if clock
	 */
	private boolean isClockConnection(PathfinderNode current, PathfinderNode sink) {
		//TODO use clock info from timing analysis
		return sink.isClockSink() || current.isClockNode();
	}

	/**
	 * Estimate the cost for getting from a current node to a sink.
	 * Used for guiding the routing in the right direction
	 *
	 * To conform to the A* algorithm, this MAY NEVER overestimate.
	 *
	 * @param current
	 * @param sink
	 * @return
	 */
	protected double estimateRemainingCost(PathfinderNode current, PathfinderNode sink) {
		//Remaining cost is related to manhattan distance. Since wires can span multiple tiles, we need to
		//divide the manhattan distance by some value.

		//The value was found by experimentation
		double divider = 1;
		//TODO recheck this for other designs

		//Clock resources span many tiles. Estimate their remaining cost as less.
		if (isClockConnection(current, sink)) {
			return 0;
		}


		//TODO incorporate node delays
		return current.getManhattanDistance(sink) / divider;
	}

	/**
	 * Total cost from start to next
	 * @param current current node
	 * @param next next node
	 * @param sink sink node
	 * @param globalIteration iteration number
	 * @return total cost
	 */
	private double getCost(NodeCost current, PathfinderNode next, PathfinderNode sink, int globalIteration) {
		double nodeCost = getNodeCost(current.getNode(), next, globalIteration);

		return current.getCost() + nodeCost;
	}

	/**
	 * Cost of the next node
	 *
	 * Formula as in the Pathfinder paper
	 * @param current current node
	 * @param next next node
	 * @param globalIteration iteration number
	 * @return node cost
	 */
	private double getNodeCost(PathfinderNode current, PathfinderNode next, int globalIteration) {
		double nodeDelay = getNodeDelay(current, next);
		//TODO play with factors...
		double bn = /*TODO baseCostMultiplier * */ nodeDelay;
		double pnIterationScale = (globalIteration + 1) / 6;
		double pn = next.getCurrentUsage() * pnIterationScale + 1;
		double hn = next.getHistoricalOveruse();
		//TODO criticality
		return (bn + hn) * pn;
	}

	private void blockResources(Net net) {
		net.getPIPs().forEach(pip -> {
			pathfinderNodeFactory.getNode(pip.getTile(), pip.getStartWire()).block();
			pathfinderNodeFactory.getNode(pip.getTile(), pip.getEndWire()).block();
		});
	}

	private void blockResources(Collection<Net> nets) {
		nets.forEach(this::blockResources);
	}

	private <T extends Collection<Net>> T findNetsToRoute(Supplier<T> collectionSupplier) {
		T nets = collectionSupplier.get();
		for (Net net : design.getNets()) {
			if (net.getPIPs().size() == 0 || !keepExistingRoutes) {
				net.getPIPs().clear();
				nets.add(net);
			} else {
				blockResources(net);
			}

		}
		return nets;
	}


	public static void main(String[] args) throws IOException, ClassNotFoundException {

		if (args.length != 3) {
			System.out.println("USAGE: Router <input.xdl> <timingCalibration.cal> <output.xdl>"); //parameter parser
			System.exit(0);
		}
		if (System.console() != null) {
			System.out.print("Waiting for return...");
			System.console().readLine();
		}

		System.out.println("Running");
		Tracer.start();
		try {


			Tracer.enter("Load Design");
			Design design = new Design();
			design.loadXDLFile(Paths.get(args[0]));
			Tracer.exit();


        /*Map<String, Set<Net>> tieoffPins = design.getInstances().stream()
				.filter(i -> i.getType() == PrimitiveType.TIEOFF)
                .flatMap(i -> i.getPins().stream())
                .collect(Collectors.groupingBy(p -> p.getName(), Collectors.mapping(p -> p.getNets(), Collectors.toSet())));
        tieoffPins.forEach((k,vs) -> {
            System.out.println(k);
            //noinspection ReplaceAllDot
            System.out.println(k.replaceAll(".","="));
            vs.forEach((x) -> System.out.println(x+" "+x.getPins().stream().filter(p->p!=x.getSource()).collect(Collectors.toSet()ca)));
            System.out.println();
        });
        System.out.println("tieoffPins = " + tieoffPins);*/


			PrimitiveDefList primitiveDefs;
			try (Tracer ignored = Tracer.enter("Load primitive Defs")) {
				primitiveDefs = DeviceDatabaseProvider.getDeviceDatabase().loadPrimitiveDefs(design.getPartName());
				PrimitiveDefDetails.insertDetails(primitiveDefs);
				PrimitiveDefDetails.checkConsistency(primitiveDefs);
			}

			TimingCalculator calculator;
			try (Tracer ignored = Tracer.enter("Load timing")) {

				TimingCalibration cal = TimingCalibration.loadFromFile(new File(args[1]));

				cal.enterToPrimitiveDefs(primitiveDefs);

				DelayModel delayModel = cal.createDelayModel();

				calculator = new TimingCalculator(primitiveDefs, delayModel, design, false);
			}

			Pathfinder router = new Pathfinder(design, calculator, primitiveDefs, true);
			router.netLog = new PrintWriter(Files.newBufferedWriter(Paths.get(args[2] + ".csv")));

			// route design
			long before = System.nanoTime();
			router.routeDesign();
			long after = System.nanoTime();

			long duration = after - before;
			System.out.format("Took %fs%n", 1E-9 * duration);


			try (Tracer ignored = Tracer.enter("Fix IOBs")) {
				design.fixSp6IOBs();
			}

			try (Tracer ignored = Tracer.enter("Save design")) {
				design.saveXDLFile(Paths.get(args[2]));
			}

			router.netLog.close();

//			for (Instance instance : design.getInstances()) {
//				if (instance.isIOB()) {
//					logger.info("IOB: " + instance.getName() + " nets: " + instance.getNetList().stream().map(Net::getName).collect(Collectors.joining(" ")));
//				}
//			}
		} finally {

			Tracer.printStats(System.out);
		}

	}

	public Map<Net, NetData> getNetDatas() {
		return netDatas;
	}

	public PathfinderNodeFactory getPathfinderNodeFactory() {
		return pathfinderNodeFactory;
	}

	/**
	 * Verify correctness of remaining cost estimator.
	 * Only works right after routing the net when parent links are still valid in the nodes.
	 */
	protected boolean verifyRemainingCostEstimate(PathfinderNode current, PathfinderNode sink, double costToEnd, BiConsumer<Double, Double> resultComparator) {
		return true;
		/*double estimatedRemaining = estimateRemainingCost(current,sink);

        boolean ok = true;
        if (estimatedRemaining>costToEnd) {
            logger.error("Estimator is not admissible. From "+current.toString(design.getWireEnumerator())+" to "+sink.toString(design.getWireEnumerator())+". Estimate: "+estimatedRemaining+" Actual: "+costToEnd);
            logger.error("current direction: "+design.getWireEnumerator().getWireDirection(current.getWire()));
            logger.error("sink direction: "+design.getWireEnumerator().getWireDirection(sink.getWire()));
            ok = false;
        } else {
            resultComparator.accept(estimatedRemaining, costToEnd);
        }


        if (current.getParent()!=null) {
            double nodeCost = getNodeCost(current.getParent(), current);
            double parentCostToEnd = costToEnd + nodeCost;

            //We don't rely on monoticity, so don't check it
//            double parentEstimated = estimateRemainingCost(current.getParent(), sink);
//
//            if (parentEstimated>nodeCost+estimatedRemaining) {
//                logger.error("Estimator is not monotonous. From "+current.toString(design.getWireEnumerator())+" to "+sink.toString(design.getWireEnumerator())+". Parent Estimate: "+parentEstimated+" child estimate: "+estimatedRemaining+" conn: "+nodeCost);
//                ok = false;
//            }

            ok &= verifyRemainingCostEstimate(current.getParent(), sink, parentCostToEnd, resultComparator);
        }
        //return ok;
        return true;*/
	}

	private boolean verifyRemainingCostEstimate(PathfinderNode sinkNode, BiConsumer<Double, Double> resultComparator) {
		return verifyRemainingCostEstimate(sinkNode, sinkNode, 0, resultComparator);
	}
}
