package edu.byu.ece.rapidSmith.timing.logic;

import edu.byu.ece.rapidSmith.design.Design;
import edu.byu.ece.rapidSmith.design.Instance;
import edu.byu.ece.rapidSmith.design.Pin;
import edu.byu.ece.rapidSmith.device.PrimitiveType;
import edu.byu.ece.rapidSmith.primitiveDefs.Element;
import edu.byu.ece.rapidSmith.primitiveDefs.PrimitiveDef;
import edu.byu.ece.rapidSmith.primitiveDefs.PrimitiveDefList;
import edu.byu.ece.rapidSmith.primitiveDefs.PrimitiveDefPin;
import edu.byu.ece.rapidSmith.router.InstanceElement;
import edu.byu.ece.rapidSmith.router.RoutingElement;
import edu.byu.ece.rapidSmith.timing.Path;
import edu.byu.ece.rapidSmith.timing.PathDelay;
import edu.byu.ece.rapidSmith.timing.PathElement;
import edu.byu.ece.rapidSmith.timing.RegisterFinder;
import edu.byu.ece.rapidSmith.util.SetHelpers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by jakobw on 03.07.15.
 */
public class LogicTimingAnalyzer {

	private static final Logger logger = LoggerFactory.getLogger(LogicTimingAnalyzer.class);
	/**
	 * If true, there is only one timing value generated for numbered inputs, e.g. for LUT inputs.
	 */
	public boolean combineNumberedElements;
	/**
	 * If true, there is only one timing value generated for repeated elements in Slices.
	 * These elements are combined: (? is [A-D])
	 * - ?[1-6]
	 * - ?X
	 * - ?5LUT
	 * - ?6LUT
	 * - ?USED
	 * - ?FFMUX
	 * - ?
	 * - ?MUX
	 * - ?FF
	 * - ?5FF
	 * - ?OUTMUX
	 */
	public boolean combineLUTs;
	/**
	 * If true, there is only one timing value generated for elements named the same in different primitives.
	 */
	public boolean combineSameName;

	/**
	 * Strategy by which multiple differing delays are aggregated into a single one
	 */
	public AverageStrategy averageStrategy;

	final PrimitiveDefList primitiveDefs;

	public LogicTimingAnalyzer(PrimitiveDefList primitiveDefs) {
		this.primitiveDefs = primitiveDefs;
	}

	/**
	 * Find Paths through Primitives and Instances containing those paths
	 *
	 * @param design
	 * @return
	 */
	public Map<LogicDelayPath, Set<Instance>> findPaths(Design design) {

		Map<LogicDelayPath, Set<Instance>> instancePaths = new HashMap<>();
		Map<PrimitiveType, Set<Instance>> primitiveTypes = SetHelpers.group(design.getInstances(), Instance::getType);


		analyzeCombinatorialDelays(primitiveTypes, instancePaths);
		analyzeRegisters(primitiveTypes, instancePaths);

		return instancePaths;
	}

	private void analyzeRegisters(Map<PrimitiveType, Set<Instance>> primitiveTypes, Map<LogicDelayPath, Set<Instance>> instancePaths) {
		for (PrimitiveType primitiveType : primitiveTypes.keySet()) {
			Set<Instance> instances = primitiveTypes.get(primitiveType);

			RegisterFinder w = new RegisterFinder(primitiveDefs);

			//Find registers and group instances by them
			Map<Element, Set<Instance>> registerElements = SetHelpers.multigroup(instances, (inst) -> {
				Set<InstanceElement> regs = w.findActiveInstanceElementRegisters(inst);
				//regs.removeIf(ie->ie.getAttribute()==null);
				return regs;
			}, InstanceElement::getElement);

			for (Element register : registerElements.keySet()) {
				findPath(register, registerElements.get(register), new ArrayList<>(), PathType.REG_TO_OUT, primitiveType, instancePaths);
				findPath(register, registerElements.get(register), new ArrayList<>(), PathType.IN_TO_REG, primitiveType, instancePaths);
			}
		}
	}

	private enum PathType {
		COMBINATORIAL,
		REG_TO_OUT,
		IN_TO_REG;

		public boolean isRegisterSearch() {
			return this == IN_TO_REG || this == REG_TO_OUT;
		}

		public boolean isBackward() {
			return this == COMBINATORIAL || this == IN_TO_REG;
		}
	}

	private void analyzeCombinatorialDelays(Map<PrimitiveType, Set<Instance>> primitiveTypes, Map<LogicDelayPath, Set<Instance>> instancePaths) {
		for (PrimitiveType primitiveType : primitiveTypes.keySet()) {

			PrimitiveDef def = primitiveDefs.getPrimitiveDef(primitiveType);

			for (PrimitiveDefPin primitiveDefPin : def.getPins()) {
				if (primitiveDefPin.isOutput()) {
					Set<Instance> instances = primitiveTypes.get(primitiveType);
					Set<Instance> instancesWithPin = filterInstancesForUsedPin(primitiveDefs, primitiveDefPin, instances);


					Element element = def.getElement(primitiveDefPin.getInternalName());
					if (element == null) {
						throw new RuntimeException("Element not found");
					}
					findPath(element, instancesWithPin, new ArrayList<>(), PathType.COMBINATORIAL, primitiveType, instancePaths);
				}
			}
		}
	}

	private Set<Instance> filterInstancesForUsedPin(PrimitiveDefList primitiveDefs, PrimitiveDefPin primitiveDefPin, Set<Instance> instances) {
		Set<Instance> instancesWithPin = new HashSet<>();

		for (Instance instance : instances) {
			Pin pin = instance.getPin(primitiveDefPin.getExternalName());
			if (pin == null) {
				continue;
			}
			if (pin.getConnectedForward(primitiveDefs).size() > 0) {
				instancesWithPin.add(instance);
			}
		}
		return instancesWithPin;
	}

	void findPath(Element current, Set<Instance> instances, List<Element> path, PathType pathType, PrimitiveType primitiveType, Map<LogicDelayPath, Set<Instance>> instancePaths) {
		Map<Element, Set<Instance>> connectedElements = new HashMap<>();
		Map<Element, Set<Instance>> pathFinishElements = new HashMap<>();

		for (Instance instance : instances) {

			Set<RoutingElement> connected = pathType.isBackward()?
					instance.getInstanceElement(current, primitiveDefs).getConnectedBackward(primitiveDefs) :
					instance.getInstanceElement(current, primitiveDefs).getConnectedForward(primitiveDefs);

			for (RoutingElement routingElement : connected) {
				if (routingElement instanceof InstanceElement) {
					InstanceElement instanceElement = (InstanceElement) routingElement;

					InstanceElement.RegisterType reg = instanceElement.isRegister(primitiveDefs);
					if (reg == InstanceElement.RegisterType.OFF) {
						continue;
					}

					if (reg == InstanceElement.RegisterType.REGISTER) {
						if (pathType != PathType.COMBINATORIAL) { //Found a path from a register to another one
							logger.warn("discarding internal reg to reg in {}: {}", instance.getName(), Stream.concat(path.stream(), Stream.of(current, instanceElement.getElement())).map(Element::getName).collect(Collectors.joining("->")));
							//SetHelpers.putIntoSet(pathFinishElements, instanceElement.getElement(), instance);
						} // else discard it. Paths crossing a register cannot be combinatorial,
					} else { //Combinatorial or latch
						if (instanceElement.isPin() != InstanceElement.IsPin.NO_PIN) {
							SetHelpers.putIntoSet(pathFinishElements, instanceElement.getElement(), instance);
						} else {
							SetHelpers.putIntoSet(connectedElements, instanceElement.getElement(), instance);
						}
					}

				} else {
					throw new RuntimeException("Can only process InstanceElements! " + routingElement + " " + pathType);
				}
			}
		}

		List<Element> newPath = new ArrayList<>(path);
		newPath.add(current);

		for (Element connectedElement : connectedElements.keySet()) {
			findPath(connectedElement, connectedElements.get(connectedElement), newPath, pathType, primitiveType, instancePaths);
		}
		for (Element inPinElement : pathFinishElements.keySet()) {
			finishPath(inPinElement, pathFinishElements.get(inPinElement), new ArrayList<>(newPath), pathType, primitiveType, instancePaths);
		}
	}


	public static class LogicDelayPath {

		public final String start;
		public final String end;
		public final List<String> path;
		public final PathType type;
		public final PrimitiveType primitiveType;

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (o == null || getClass() != o.getClass()) {
				return false;
			}

			LogicDelayPath path1 = (LogicDelayPath) o;

			if (!start.equals(path1.start)) {
				return false;
			}
			if (!end.equals(path1.end)) {
				return false;
			}
			if (!path.equals(path1.path)) {
				return false;
			}
			if (type != path1.type) {
				return false;
			}
			return primitiveType == path1.primitiveType;

		}

		@Override
		public int hashCode() {
			int result = start.hashCode();
			result = 31 * result + end.hashCode();
			result = 31 * result + path.hashCode();
			result = 31 * result + type.hashCode();
			result = 31 * result + (primitiveType != null? primitiveType.hashCode() : 0);
			return result;
		}

		LogicDelayPath(List<String> path, PathType type, PrimitiveType primitiveType) {
			this.start = path.get(0);
			this.end = path.get(path.size() - 1);
			this.path = path;
			this.type = type;
			this.primitiveType = primitiveType;

		}


		private String pathStr() {

			return path.stream().reduce((a, b) -> a + "->" + b).get();

		}

		@Override
		public String toString() {
			return "LogicDelayPath{" +
					"path=" + pathStr() +
					", type=" + type +
					", primitiveType=" + primitiveType +
					'}';
		}
	}


	private void finishPath(Element finishElement, Set<Instance> instances, List<Element> path, PathType pathType, PrimitiveType primitiveType, Map<LogicDelayPath, Set<Instance>> instancePaths) {
		path.add(finishElement);
		if (pathType.isBackward()) {
			Collections.reverse(path);
		}


		List<String> strPath = toStringPath(path);

		LogicDelayPath pathInst;
		if (combineSameName)
		//If we combine paths with elements of the same name, do not save the primitive type, so it gets merged with others.
		{
			pathInst = new LogicDelayPath(strPath, pathType, null);
		} else {
			pathInst = new LogicDelayPath(strPath, pathType, primitiveType);
		}

		for (Instance instance : instances) {
			instancePaths.computeIfAbsent(pathInst, k -> new HashSet<>()).add(instance);
		}
	}

	private List<String> toStringPath(List<Element> path) {
		//First, make everything to strings
		List<String> res = path.stream().map(Element::getName).collect(Collectors.toList());

		if (combineLUTs) {
			List<String> combined = new ArrayList<>();
			Character lutName = null;
			for (String elem : res) {
				if (elem.matches("^[A-D]([1-6]|X|[5-6]LUT|USED|FFMUX|MUX|5?FF|OUTMUX|)$")) {
					if (lutName == null) {
						lutName = elem.charAt(0);
					} else if (lutName != elem.charAt(0)) {
						combined = null;
						break;
					}
					combined.add("$" + elem.substring(1));
				} else {
					combined = null;
					break;
				}
			}
			if (combined != null) {
				res = combined;
			}
		}
		if (combineNumberedElements) {
			//OLOGIC2 also contains inputs named D1 and D2. We cannot combine those elements, as they are not connected
			//to the same things. We detect that case by looking for elements named OMUX or OUTFF in the path.
			if (!path.stream().anyMatch(element -> element.getName().equals("OMUX") || element.getName().equals("OUTFF"))) {
				res.replaceAll(s -> s.replaceAll("^([A-D$])[0-9]+$", "$1?"));
			}
		}
		return res;
	}

	/**
	 * Get Delays for paths
	 *
	 * @param design
	 * @param paths
	 * @param delays
	 * @return
	 */
	public Map<LogicDelayPath, InstancesAndDelay> getPathTimings(Design design, Map<LogicDelayPath, Set<Instance>> paths, Collection<PathDelay> delays) {

		Map<LogicDelayPath, InstancesAndDelay> result = new HashMap<>();


		Map<Instance, Set<PathDelay>> delaysContainingInstance = SetHelpers.multigroup(delays, Path::getMaxDataPath, pathElement -> pathElement.getPin().getInstance());

		paths.forEach((path, instances) -> {

			Iterable<LogicDelayPath> realPaths = toRealPaths(path);
			List<Float> pathDelays = new ArrayList<>();
			Set<String> delayTypes = new HashSet<>();
			instances.forEach(instance -> {
				realPaths.forEach(realPath -> putInstanceDelay(design, realPath, instance, delaysContainingInstance.get(instance), pathDelays, delayTypes));
			});

			if (pathDelays.isEmpty()) {
				result.put(path, new InstancesAndDelay(instances, null));
			} else {
				if (delayTypes.size() > 1) {
					logger.warn("Found delays of multiple types for " + path + ": " + delayTypes);
				}
				result.put(path, new InstancesAndDelay(instances, pathDelays));
			}
		});
		return result;
	}

	private static LogicDelayPath replaceInPath(LogicDelayPath p, String c, String replace) {
		List<String> elements = new ArrayList<>(p.path);
		elements.replaceAll(s -> s.replace(c, replace));
		return new LogicDelayPath(elements, p.type, p.primitiveType);
	}

	public static Collection<LogicDelayPath> toRealPaths(LogicDelayPath path) {
		String pathStr = path.pathStr();

		Collection<LogicDelayPath> withNumbers = new ArrayList<>();
		boolean genNumbers = pathStr.contains("?");
		if (genNumbers) {
			int max = 6;
			if (path.path.get(1).contains("5LUT")) {
				max = 5;
			}
			for (int i = 1; i <= max; i++) {
				withNumbers.add(replaceInPath(path, "?", String.valueOf(i)));
			}
		} else {
			withNumbers.add(path);
		}
		boolean genLUTs = pathStr.contains("$");
		if (genLUTs) {
			Collection<LogicDelayPath> result = new ArrayList<>();
			for (LogicDelayPath withNumber : withNumbers) {
				for (char c = 'A'; c <= 'D'; c++)
					result.add(replaceInPath(withNumber, "$", String.valueOf(c)));
			}
			return result;
		} else {
			return withNumbers;
		}
	}

	private PathElement getInstanceDelayCombinatoric(LogicDelayPath path, Instance instance, PathDelay delay) {

		PathElement lastElement = null;
		Pin startPin = instance.getPin(path.start);
		Pin endPin = instance.getPin(path.end);

		if (startPin == null || endPin == null) {
			return null;
		}

		for (PathElement pathElement : delay.getMaxDataPath()) {

			if (lastElement != null) {
				if (lastElement.getPin().equals(startPin) &&
						pathElement.getPin().equals(endPin)) {

					return pathElement;
				}
			}
			lastElement = pathElement;
		}
		return null;
	}

	private PathElement getInstanceDelayInToReg(Design design, LogicDelayPath path, Instance instance, PathDelay delay) {
		Map<String, InstanceElement> logicalNames = design.getRegisterLogicalNameMap(primitiveDefs);
		InstanceElement end = logicalNames.get(delay.getDestination());
		if (end.getElement().getName().endsWith("LUT") && end.getAttribute().getValue().contains("RAM")) {
			end = instance.getInstanceElement(end.getElement().getName()+"WRITE",primitiveDefs);
		}
		if (end == null) {
			logicalNames.keySet().stream().filter(s -> s.startsWith(delay.getDestination())).forEach(s -> System.err.println("possible match: " + s));
			logger.error("Could not find element for logical name \"" + delay.getDestination() + "\", at end in " + design.getName());
			return null;
		}

		if (end.getInstance() != instance) {
			return null;
		}


		if (!end.getElement().getName().equals(path.end)) {
			return null;
		}


		if (!delay.getMaxDataPath().get(delay.getMaxDataPath().size() - 2).getPin().getName().equals(path.path.get(0))) {
			return null;
		}

		return delay.getMaxDataPath().get(delay.getMaxDataPath().size() - 1);
	}

	private PathElement getInstanceDelayRegToOut(Design design, LogicDelayPath path, Instance instance, PathDelay delay) {
		Map<String, InstanceElement> logicalNames = design.getRegisterLogicalNameMap(primitiveDefs);


		InstanceElement start = logicalNames.get(delay.getSource());
		if (start == null) {
			logicalNames.keySet().stream().filter(s -> s.startsWith(delay.getSource())).forEach(s -> System.out.println("possible match: " + s));
			logger.error("Could not find element for logical name \"" + delay.getSource() + "\", at start in " + design.getName());
			return null;
		}

		if (start.getInstance() != instance) {
			return null;
		}

		if (!start.getElement().getName().equals(path.start)) {
			return null;
		}

		PathElement firstElem = delay.getMaxDataPath().get(0);
		if (firstElem.getPin().getName().equals(path.end)) {
			return firstElem;
		} else {
			return null;
		}
	}

	private void putInstanceDelay(Design design, LogicDelayPath path, Instance instance, Collection<PathDelay> delays, List<Float> pathDelays, Set<String> delayTypes) {

		//Tieoffs always have a delay of 0.
		if (instance.getType() == PrimitiveType.TIEOFF) {
			pathDelays.add(0f);
			delayTypes.add("TIEOFF");
			return;
		}

		if (delays == null) {
			return;
		}


		for (PathDelay delay : delays) {

			PathElement delayElement = null;
			switch (path.type) {
				case COMBINATORIAL:
					delayElement = getInstanceDelayCombinatoric(path, instance, delay);
					break;
				case REG_TO_OUT:
					delayElement = getInstanceDelayRegToOut(design, path, instance, delay);
					break;
				case IN_TO_REG:
					delayElement = getInstanceDelayInToReg(design, path, instance, delay);
					break;
			}
			if (delayElement != null) {
				pathDelays.add(delayElement.getDelay());
				delayTypes.add(delayElement.getType());
			}
		}
	}

	public static class InstancesAndDelay {

		public final Set<Instance> instances;
		public final List<Float> delays;

		public InstancesAndDelay(Set<Instance> instances, List<Float> delays) {
			this.instances = instances;
			this.delays = delays;
		}
	}
}
