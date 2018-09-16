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

import edu.byu.ece.rapidSmith.design.Attribute;
import edu.byu.ece.rapidSmith.design.Design;
import edu.byu.ece.rapidSmith.design.Instance;
import edu.byu.ece.rapidSmith.design.Pin;
import edu.byu.ece.rapidSmith.device.PrimitiveType;
import edu.byu.ece.rapidSmith.primitiveDefs.Connection;
import edu.byu.ece.rapidSmith.primitiveDefs.PrimitiveDef;
import edu.byu.ece.rapidSmith.primitiveDefs.PrimitiveDefList;
import edu.byu.ece.rapidSmith.router.InstanceElement;
import edu.byu.ece.rapidSmith.router.RoutingElement;
import edu.byu.ece.rapidSmith.timing.logic.LogicPathElement;
import edu.byu.ece.rapidSmith.timing.routing.DelayModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Calculate timings
 */
public class TimingCalculator {

	private final PrimitiveDefList primitives;
	private final DelayModel delayModel;
	private final Design design;
	RegisterFinder registerFinder;
	private Set<RoutingElement> registers;
	private double maxDelay;

	private final boolean verbose;

	public TimingCalculator(PrimitiveDefList primitives, DelayModel delayModel, Design design, boolean verbose) {
		this.primitives = primitives;
		this.delayModel = delayModel;
		this.design = design;
		this.verbose = verbose;
		registerFinder = new RegisterFinder(primitives);
	}

	public TimingCalculator(PrimitiveDefList primitives, DelayModel delayModel, Design design) {
		this(primitives, delayModel, design, false);
	}


	public Set<RoutingElement> getRegisters() {
		return registers;
	}

	public double getMaxDelay() {
		return maxDelay;
	}

	public void calculateTimings(BiConsumer<RoutingElement, RoutingElement> unknownDelayConsumer) {
		if (registers == null) {
			init();
		}


		maxDelay = calculateDelay(registers, unknownDelayConsumer);
		calculateRequire(registers, maxDelay, unknownDelayConsumer);
	}

	//TODO make private
	public void init() {
		findStaticSources();
		disableNoSinkPins();
		inferDisabledElements();

		registers = registerFinder.findActiveDesignRegisters(design);

		inferClockElements();
		clearConnectedCache();
		inferRegsClockToNonClock();
		clearConnectedCache();

		//Research for regs, because we added fake ones.
		registers = registerFinder.findActiveDesignRegisters(design);

		buildStatic();

	}

	private static final Logger logger = LoggerFactory.getLogger(TimingCalculator.class);


	private void clearConnectedCache() {
		DesignWalker.routingElementStream(design, primitives).forEach(RoutingElement::clearConnectedCache);
	}

	private Stream<RoutingElement> inferDisabledPins(Pin pin) {
		boolean hasIn = !pin.getConnectedBackward(primitives).isEmpty();
		boolean hasOut = !pin.getConnectedForward(primitives).isEmpty();
		boolean hasAtMostOne = !(hasIn && hasOut);
		if (hasAtMostOne && !pin.isDeadEnd()) {

			Set<RoutingElement> neighbours = Stream.concat(pin.getConnectedBackward(primitives).stream(),
					pin.getConnectedForward(primitives).stream())
					.collect(Collectors.toSet());
			//We need to get the neighbours before setting dead end, because otherwise the lists are already empty

			pin.setIsDeadEnd(true);

			return neighbours.stream();
		}
		return Stream.empty();
	}

	private Stream<RoutingElement> inferDisabledInstanceElements(InstanceElement ie) {
		boolean hasIn = !ie.getConnectedBackward(primitives, true).isEmpty();
		boolean hasOut = !ie.getConnectedForward(primitives, true).isEmpty();
		boolean hasAtMostOne = !(hasIn && hasOut);
		boolean hasNone = !hasIn && !hasOut;

		boolean isReg = ie.isRegister(primitives) == RoutingElement.RegisterType.REGISTER;

		//Registers have to be totally unconnected, for other elements only one side needs to be unconnected
		if ((
				hasNone
						|| (!isReg && hasAtMostOne)
		)
				&& !ie.getElement().getConnections().isEmpty()) {
			//Check if it's already off
			if (ie.getAttribute() != null && ie.getAttribute().getValue().equals("#OFF")) {
				return Stream.empty();
			}
			//Do not disable constant sources
			if (ie.getElement().getName().endsWith("LUT") && hasOut && ie.getAttribute() != null) {
				return Stream.empty();
			}

			if (ie.getAttribute() != null && verbose) {
				logger.warn("Marking dead end " + ie + " as off but has attr: " + ie.getAttribute());
			}


			Stream<RoutingElement> res = Stream.empty();
			if (hasIn) {
				res = Stream.concat(res, ie.getConnectedBackward(primitives).stream());
			}
			if (hasOut) {
				res = Stream.concat(res, ie.getConnectedForward(primitives).stream());
			}

			if (ie.isPin() != InstanceElement.IsPin.NO_PIN && ie.getExternalPin() != null && ie.getExternalPin().getNet().getPins().size() > 1 && verbose) {
				logger.warn("disabling a used pin! " + ie);
			}

			ie.setDisabled(true);

			return res;
		}
		return Stream.empty();
	}

	private void inferDisabledElements() {
		DesignWalker.visitAllElements(design, primitives, this::inferDisabledPins, this::inferDisabledInstanceElements);
	}


	/**
	 * Some nets have no sink. Disable those source pins.
	 */
	private void disableNoSinkPins() {
		DesignWalker.pinStream(design).forEach(pin -> {
			InstanceElement ie = pin.getInternalPin(primitives);
			//Sometimes, getPins is empty, sometimes it contains the source...
			if (pin.getNet().getPins().isEmpty()
					|| (pin.getNet().getPins().size() == 1 && pin.getNet().getPins().get(0).equals(pin)) && ie.getAttribute() == null) {
				if (verbose) {
					logger.info("Disabling pin on net without sinks " + pin);
				}

				pin.setIsDeadEnd(true);
			}
		});
	}


	private enum SourceType {
		VCC(1),
		GND(0);

		public int val;

		SourceType(int val) {
			this.val = val;
		}
	}

	/**
	 * Find static source LUTs and mark them
	 */
	private void findStaticSources() {
		for (Instance inst : design.getInstances()) {
			if (inst.getType() == PrimitiveType.SLICEL || inst.getType() == PrimitiveType.SLICEM || inst.getType() == PrimitiveType.SLICEX) {
				List<Attribute> toAdd = new ArrayList<>();
				for (Attribute attribute : inst.getAttributes()) {
					SourceType sourceType;


					switch (attribute.getPhysicalName()) {
						case "_GND_SOURCE":
							sourceType = SourceType.GND;
							break;
						case "_VCC_SOURCE":
							sourceType = SourceType.VCC;
							break;
						default:
							continue;
					}


					for (String val : attribute.getMultiValueValues()) {
						addOrChangeAttribute(inst, new Attribute(val + "6LUT", sourceType + "Source", "#LUT:O6=" + sourceType.val), toAdd);
						addOrChangeAttribute(inst, new Attribute(val + "USED", "", "0"), toAdd);
						if (verbose) {
							logger.info("found " + sourceType + " source: " + val + "6LUT in " + inst.getName());
						}

					}
				}
				for (Attribute attribute : toAdd) {
					inst.addAttribute(attribute);
					if (verbose) {
						logger.info("added " + attribute);
					}
				}
			}
		}
	}


	private Stream<RoutingElement> addPredsToRes(RoutingElement re, boolean successorsNeedToBeClock) {
		return re.getConnectedBackward(primitives).stream().filter(pred -> {
			if (!pred.isClock()) {
				if (canBeClock(pred, successorsNeedToBeClock)) {
					pred.setIsClock(true);
					return true;
				}
			}
			return false;
		});
	}

	static Set<PrimitiveType> clockInfrastructure = EnumSet.of(
			PrimitiveType.DCM,
			PrimitiveType.DCM_ADV,
			PrimitiveType.DCM_CLKGEN,
			PrimitiveType.PLL_ADV,
			PrimitiveType.BUFG,
			PrimitiveType.BUFPLL_MCB);

	private void inferClockElements() {
		//Mark predecessors of clock inputs as clock
		DesignWalker.visitAllElements(design, primitives,
				pin -> {
					if (pin.isClock()) {
						return addPredsToRes(pin, false);

					} else {
						return Stream.empty();
					}
				},
				this::inferClockElements);


		final String clock = "________________________________CLOCK_______________";
		//if a register output is connected to both clock and non-clock, the part from the reg to the split must
		//be marked as non clock
		registers.stream()
				//we need successors
				.filter(reg -> reg.getConnectedForward(primitives).size() > 0)
				//that are clock
				.filter(reg -> reg.getConnectedForward(primitives).stream().allMatch(RoutingElement::isClock))
				//Real clock nets start at iobs, discard those
				.filter(reg -> !reg.getInstance().isIOB())
				.filter(reg -> reg.getInstance().getType() != PrimitiveType.TIEOFF)
				.forEach(reg -> {
					DesignWalker.<RoutingElement, String>walkPath(reg,
							//Terminal
							(elem, first) -> !first && ((elem.isRegister(primitives) != RoutingElement.RegisterType.COMBINATORIAL)
									|| !elem.isClock() || clockInfrastructure.contains(elem.getInstance().getType())),
							//Next elements
							(elem, depth) -> elem.getConnectedForward(primitives).stream(),
							//Accumulator
							(b, elem) -> {
								if (b == null) {
									if (elem.isClock() || elem.isRegister(primitives) != RoutingElement.RegisterType.COMBINATORIAL) {
										return clock;
									} else {
										return elem.toString().replace('\n', '_');
									}
								}
								if (!b.equals(clock)) {
									if (elem.isClock()) {
										logger.info("made non clock: " + elem + " reg: " + reg + " b: " + b);
									}
									elem.setIsClock(false);
									return elem.toString().replace('\n', '_') + "->" + b;
								}
								return b;
							},
							//Reducer
							(a, b) -> {
								if (a.equals(clock) && b.equals(clock)) {
									return clock;
								}

								if (a.equals(clock)) {
									return b;
								} else {
									return a;
								}
							},
							//Do nothing on loops. DCMs and PLLs have feedback loops, that get detected here.
							x -> clock
					).forEach(x -> {
						if (!x.equals(clock)) {
							logger.info("made successors non clock: " + x);
						}
					});
				});
	}


	private Stream<RoutingElement> inferClockElements(InstanceElement ie) {
		if (!ie.isClock()) {

			//Clocking infrastructure
			if (ie.getElement().getName().contains("CLK") && canBeClock(ie, false)) {
				ie.setIsClock(true);
				return addPredsToRes(ie, false);
				//Regs with clock pin
			} else if (ie.getElement().getPin("CLK") != null) {
				RoutingElement pred = getPredecessorForPin(ie, "CLK");
				if (canBeClock(pred, false)) {
					pred.setIsClock(true);
					return Stream.of(pred);
				}
				;
			}
			return Stream.empty();

		} else {
			return addPredsToRes(ie, true);
		}

	}

	private RoutingElement getPredecessorForPin(InstanceElement ie, String name) {
		if (ie.isPin() != InstanceElement.IsPin.NO_PIN && ie.getExternalPin() != null) {
			return ie.getExternalPin();
		} else {

			PrimitiveDef primitiveDef = primitives.getPrimitiveDef(ie.getInstance().getType());
			//Get connected element
			for (Connection connection : ie.getElement().getConnections()) {
				if (!connection.isForwardConnection() && !connection.getPin0(primitiveDef).isOutput() && connection.getPin1(primitiveDef).isOutput() && connection.getPin0().equals(name)) {
					InstanceElement elem = ie.getInstance().getInstanceElement(connection.getElement1(primitiveDef), primitives);
					return elem;
				}
			}

		}
		return null;
	}


	private boolean canBeClock(RoutingElement routingElement, boolean successorsNeedToBeClock) {
		//TODO allow iob registers
		if (routingElement.isRegister(primitives) == RoutingElement.RegisterType.REGISTER) {
			if (verbose) {
				logger.info("regs cannot be clock! " + routingElement);
			}
			return false;
		}

		return (!successorsNeedToBeClock) || routingElement.getConnectedForward(primitives).stream().allMatch(RoutingElement::isClock);
	}


	private void addOrChangeAttribute(Instance inst, Attribute attr, List<Attribute> toAdd) {
		Attribute existing = inst.getAttribute(attr.getPhysicalName());
		if (existing != null) {
			existing.setLogicalName(attr.getLogicalName());
			existing.setValue(attr.getValue());
		} else {
			toAdd.add(attr);
		}
	}

	/**
	 * When clocks are connected to data inputs of other things, we need to fake a register at that transition
	 */
	private void inferRegsClockToNonClock() {
		DesignWalker.routingElementStream(design, primitives).forEach(ie -> {
			if (!ie.isClock()
					&& !ie.getConnectedBackward(primitives).isEmpty()
					&& ie.getNonClockConnectedBackwardCount(primitives) == 0
					&& ie.isRegister(primitives) != RoutingElement.RegisterType.REGISTER) {
				ie.setFakeRegister(true);
			}
		});
	}


	private double calculateDelay(Set<RoutingElement> registers, BiConsumer<RoutingElement, RoutingElement> unknownDelayConsumer) {
		//We declare maxDelay as an array to be able to access its content from a function
		double[] maxDelay = new double[1];
		maxDelay[0] = 0;
		Consumer<RoutingElement> updateMaxDelay = (RoutingElement elem) -> {
			if (elem.getArrivalTimeInput() > maxDelay[0]) {
				maxDelay[0] = elem.getArrivalTimeInput();
			}
		};


		DesignWalker.walkDesignForward(registers, primitives,
				//Init registers
				re -> re.setArrivalTimeInput(0),
				//Get ArrivalTime
				elem -> elem.getArrivalTimeOutput(primitives),
				//When seeing a successor for the first time, its arrival time is undefined. Always set it in this case
				(elem, next, prevDelay) -> {
					next.setArrivalTimeInput(prevDelay + elem.getDelayToSuccessor(next, delayModel, primitives, unknownDelayConsumer));
					updateMaxDelay.accept(next);
				},
				//When seeing a successor again, check if the arrival time via this path is larger
				(elem, next, prevDelay) -> {
					double nextDelay = prevDelay + elem.getDelayToSuccessor(next, delayModel, primitives, unknownDelayConsumer);
					if (nextDelay > next.getArrivalTimeInput()) {
						next.setArrivalTimeInput(nextDelay);
						updateMaxDelay.accept(next);
					}
				}, "Delay analysis");
		return maxDelay[0];
	}

	private void calculateRequire(Set<RoutingElement> registers, double maxDelay, BiConsumer<RoutingElement, RoutingElement> unknownDelayConsumer) {
		DesignWalker.walkDesignBackward(registers, primitives,
				//Init registers
				re -> re.setRequireTimeOutput(maxDelay),
				//Get ArrivalTime
				elem -> elem.getRequireTimeInput(primitives, maxDelay),
				//When seeing a successor for the first time, its require time is undefined. Always set it in this case
				(elem, next, prevRequire) -> {
					next.setRequireTimeOutput(prevRequire - next.getDelayToSuccessor(elem, delayModel, primitives, unknownDelayConsumer));
				},
				//When seeing a successor again, check if the require time via this path is smaller
				(elem, next, prevRequire) -> {
					double nextRequire = prevRequire - next.getDelayToSuccessor(elem, delayModel, primitives, unknownDelayConsumer);
					if (nextRequire < next.getRequireTimeOutput()) {
						next.setRequireTimeOutput(nextRequire);
					}
				}, "Require analysis");
	}


	//We need to remember the PathDelay's top RoutingElement. Therefore, we use this anonymous class internally
	class PathDelayWithElem {

		PathDelay pathDelay;
		RoutingElement topElement;

		public PathDelayWithElem(PathDelay pathDelay, RoutingElement topElement) {
			this.pathDelay = pathDelay;
			this.topElement = topElement;
		}
	}

	private Stream<PathDelay> pathDelayWalk(RoutingElement current,
											BiPredicate<RoutingElement, Boolean> isTerminal,
											BiFunction<RoutingElement, Integer, Stream<? extends RoutingElement>> nextElements,
											BinaryOperator<PathDelayWithElem> reducer) {


		return DesignWalker.walkPath(current, isTerminal, nextElements,
				(PathDelayWithElem next, RoutingElement element) -> {
					if (next == null) { //Are we at the end?
						return new PathDelayWithElem(addElementToPathDelay(element, null, null, 0), element);
					} else {
						double toSuccessor = element.getDelayToSuccessor(next.topElement, delayModel, primitives, (x, y) -> {
						});
						next.pathDelay = addElementToPathDelay(element, next.topElement, next.pathDelay, toSuccessor);
						next.topElement = element;
						return next;
					}
				}, reducer,
				loop -> {
					throw new RuntimeException("found loop: " + loop);
				})
				.map(x -> {
					x.pathDelay.setSource(ieWithType(x.topElement));
					return x.pathDelay;
				});
	}

	public Stream<PathDelay> getCriticalPaths(double epsilon) {
		//For all registers
		return registers.stream()
				.filter(r -> !isStaticReg(r))
				//Walk from reg to next reg
				.flatMap(reg -> pathDelayWalk(reg,

						//We end at registers that are not the start.
						(elem, isFirst) -> (elem.isRegister(primitives) == RoutingElement.RegisterType.REGISTER) && !isFirst,

						//Include all successors that are not clock and have a slack of (almost) zero
						(elem, depth) -> elem.getConnectedForward(primitives).stream().filter(next -> {
							if (next.isClock()) {
								return false;
							}

							double slack = elem.getSlackToSuccessor(next, delayModel, primitives, maxDelay);
							//Error detection
							if (slack < -epsilon) {
								logger.warn("slack from " + elem + " to " + next + " is " + slack);
								return false;
							} else {
								//Is slack (almost) zero?
								return slack < epsilon;
							}
						}), null
				));
	}

	private Map<RoutingElement, Set<RoutingElement>> reachables;

	public Map<RoutingElement, Set<RoutingElement>> getReachables() {
		if (reachables == null) {
			buildReachables();
		}
		return reachables;
	}

	public void buildReachables() {
		if (reachables != null) {
			return;
		}
		if (registers == null) {
			init();
		}
		reachables = new HashMap<>();
		DesignWalker.walkDesignBackward(registers, primitives,
				//Initialize regs with themselves
				register -> reachables.put(register, Collections.singleton(register)),
				//No preprocessing
				element -> null,
				//When seeing an element for the first time, copy the set
				(current, next, userData) -> {
					boolean isReg = next.isRegister(primitives) == RoutingElement.RegisterType.REGISTER;
					if (!isReg) {
						reachables.put(next, new HashSet<>(reachables.get(current)));
					}
				},
				//When seeing an element again, add all set elements
				(current, next, userData) -> {
					boolean isReg = next.isRegister(primitives) == RoutingElement.RegisterType.REGISTER;
					if (!isReg) {
						reachables.get(next).addAll(reachables.get(current));
					}
				},
				"Reachables");
	}

	private boolean isStaticReg(RoutingElement reg) {
		if (!(reg instanceof InstanceElement)) {
			return false;
		}
		final InstanceElement ie = (InstanceElement) reg;
		if (ie.isRegister(primitives) != RoutingElement.RegisterType.REGISTER) {
			return false;
		}

		if (ie.getInstance().getType() == PrimitiveType.TIEOFF) {
			return true;
		}

		if (ie.getElement().getName().endsWith("LUT")) {
			return true;
		}

		return false;
	}

	private void buildStatic() {
		final Stream<RoutingElement> staticRegisters = registers.stream().filter(this::isStaticReg);
		DesignWalker.visitElements(staticRegisters, elem -> {
			//If already handled, do nothing
			if (elem.isStatic()) {
				return Stream.empty();
			}

			if (isStaticReg(elem) || elem.getConnectedBackward(primitives).stream().allMatch(RoutingElement::isStatic)) {
				elem.setStatic(true);
				return elem.getConnectedForward(primitives).stream();
			}

			return Stream.empty();
		});
	}

	private PathDelay addElementToPathDelay(RoutingElement current, RoutingElement next, PathDelay nextDelay, double toSuccessor) {
		if (nextDelay == null) {
			if (next != null) {
				return addElementToPathDelay(current, next, addElementToPathDelay(next, null, null, 0), toSuccessor);
			} else {
				PathDelay res = new PathDelay(0);
				LogicPathElement elem = new LogicPathElement();
				InstanceElement ie = ((InstanceElement) current);
				Pin p = ie.getInstance().getPin("CLK");
				//TODO use the right clock here
				//Find Clock for BRAM
				if (p == null) {
					p = ie.getInstance().getPin("CLKA");
				}
				if (p == null) {
					p = ie.getInstance().getPin("CLKB");
				}

				//Find clock for ologic2
				if (p == null) {
					p = ie.getInstance().getPin("CLK0");
				}
				if (p == null) {
					p = ie.getInstance().getPin("CLK1");
				}
				elem.setPin(p);
				elem.setType("logic");
				elem.setInstance(ie.getInstance());

				if (ie.getAttribute() != null) {
					elem.setLogicalResources(Collections.singletonList(ie.getAttribute().getLogicalName() + " (" + ie.getElement().getName() + ")"));
				} else {
					elem.setLogicalResources(Collections.singletonList(ie.getElement().getName()));
				}

				res.getMaxDataPath().add(elem);
				res.setDestination(ieWithType(ie));
				return res;
			}
		} else {
			PathDelay res = new PathDelay();
			res.setMaxDataPath(nextDelay.getMaxDataPath());

			double totalNext = nextDelay.getDelay() + toSuccessor;

			res.setDelay((float) totalNext);
			res.setDataPathDelay(nextDelay.getDataPathDelay());
			res.setRoutingDelay(nextDelay.getRoutingDelay());

			res.setDestination(nextDelay.getDestination());
			res.setLevelsOfLogic(nextDelay.getLevelsOfLogic());

			if (current instanceof Pin && next instanceof Pin) {
				RoutingPathElement element = new RoutingPathElement();
				element.setNet(((Pin) current).getNet());
				element.setDelay((float) toSuccessor);
				element.setType("net");
				element.setPin((Pin) next);
				res.getMaxDataPath().add(0, element);
				res.setRoutingDelay((float) (toSuccessor + nextDelay.getRoutingDelay()));
			} else {
				res.setDataPathDelay((float) (toSuccessor + nextDelay.getDataPathDelay()));

				if (!res.getMaxDataPath().isEmpty() && res.getMaxDataPath().get(0) instanceof LogicPathElement) {
					LogicPathElement element = (LogicPathElement) res.getMaxDataPath().get(0);
					element.setDelay((float) (element.getDelay() + toSuccessor));
				} else {
					if (!(next instanceof Pin) || !(current instanceof InstanceElement)) {
						throw new RuntimeException("Invalid combination of Path Elements!");
					}

					res.setLevelsOfLogic(nextDelay.getLevelsOfLogic() + 1);

					LogicPathElement element = new LogicPathElement();
					element.setPin((Pin) next);
					element.setDelay((float) toSuccessor);
					element.setType("logic");
					element.setInstance(((InstanceElement) current).getInstance());
					res.getMaxDataPath().add(0, element);
				}
			}
			return res;
		}
	}

	private String ieWithType(RoutingElement re) {
		if (re instanceof InstanceElement) {
			InstanceElement ie = (InstanceElement) re;
			if (ie.getAttribute() != null) {
				return ie.getAttribute().getLogicalName() + " (" + ie.getInstance().getName() + "." + ie.getElement().getName() + ")";
			} else {
				return ie.getElement().getName();
			}
		}
		return re.toString().replace('\n', '_');
	}

	public Optional<PathDelay> getPathTimingNew(InstanceElement start, InstanceElement target) {
		if (reachables == null) {
			buildReachables();
		}

		if (start == null) {
			throw new NullPointerException();
		}

		return pathDelayWalk(start,
				(current, isFirst) -> current == target && !isFirst,
				//Get all successors where the target is reachable
				(elem, depth) -> elem.getConnectedForward(primitives).stream().filter(next -> {
					if (next.isClock()) {
						return false;
					}

					if (reachables.get(next) == null) {
						throw new RuntimeException("we have no reachables for " + next);
					} else {
						return reachables.get(next).contains(target);
					}
				}),
				//Get the one with the biggest delay
				(a, b) -> {
					if (a == null) {
						return b;
					}
					if (b == null) {
						return a;
					}
					if (a.pathDelay.getDelay() > b.pathDelay.getDelay()) {
						return a;
					}
					return b;
				}
		).reduce((a, b) -> {
			throw new RuntimeException("we have multiple results. Reduction did not work.");
		});

	}
}
