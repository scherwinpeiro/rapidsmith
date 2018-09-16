package edu.byu.ece.rapidSmith.timing;

import edu.byu.ece.rapidSmith.design.Design;
import edu.byu.ece.rapidSmith.design.Pin;
import edu.byu.ece.rapidSmith.primitiveDefs.PrimitiveDef;
import edu.byu.ece.rapidSmith.primitiveDefs.PrimitiveDefList;
import edu.byu.ece.rapidSmith.router.InstanceElement;
import edu.byu.ece.rapidSmith.router.RoutingElement;
import edu.byu.ece.rapidSmith.util.SetHelpers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.util.*;
import java.util.function.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DesignWalker {

	private static final Logger logger = LoggerFactory.getLogger(DesignWalker.class);


	@FunctionalInterface
	public interface VisitSuccessor<T> {

		void accept(RoutingElement current, RoutingElement next, T userData);
	}

	/**
	 * Walk the design, starting at the registers and call some function for each element, after all its predecessors
	 * have been visited.
	 * This will not visit elements that have isClock set to true.
	 *
	 * @param registers All active design registers, as returned by RegisterFinder
	 * @param registerInitializer Function to call for each register before visiting anything else
	 * @param visitElement called once for each element after all its predecessors have been visited. Can return some
	 * user data that will be passed to successorFirstSeen and successorAgainSeen
	 * @param nextElements function to get successors (should be either RoutingElement::getConnectedForward or
	 * RoutingElement::getConnectedBackward)
	 * @param prevElements function to get predecessors (should be either RoutingElement::getConnectedForward or
	 * RoutingElement::getConnectedBackward)
	 * @param nextNonClockSize number of non clock successors of an element. (should be either
	 * RoutingElement::getNonClockConnectedBackwardCount or
	 * RoutingElement::getNonClockConnectedForwardCount)
	 * @param prevNonClockSize number of non clock predecessors of an element. (should be either
	 * RoutingElement::getNonClockConnectedBackwardCount or
	 * RoutingElement::getNonClockConnectedForwardCount)
	 * @param successorFirstSeen Method to be called when a successor is first encountered
	 * @param successorAgainSeen Method to be called when a successor is seen again
	 */
	public static <T> void walkDesign(
			Set<RoutingElement> registers,
			PrimitiveDefList primitives,
			Consumer<RoutingElement> registerInitializer,
			Function<RoutingElement, T> visitElement,
			VisitSuccessor<T> successorFirstSeen,
			VisitSuccessor<T> successorAgainSeen,
			BiFunction<RoutingElement, PrimitiveDefList, Set<RoutingElement>> nextElements,
			BiFunction<RoutingElement, PrimitiveDefList, Set<RoutingElement>> prevElements,
			ToIntBiFunction<RoutingElement, PrimitiveDefList> nextNonClockSize,
			ToIntBiFunction<RoutingElement, PrimitiveDefList> prevNonClockSize,
			String analysisName
	) {

		//Inside this function, predecessor and successor are used in walking direction, not in data flow direction.

		//Set initial value on registers
		registers.forEach(registerInitializer);

		Queue<RoutingElement> ready = new ArrayDeque<>(registers);

		//TODO do we want to keep this debugging stuff?
		Map<RoutingElement, Set<RoutingElement>> debugSeenPredecessors = new HashMap<>();


		Map<RoutingElement, Integer> seenSuccessors = new HashMap<>();
		Set<RoutingElement> visitedElements = new HashSet<>();

		while (!ready.isEmpty()) {
			RoutingElement elem = ready.poll();

			visitedElements.add(elem);

			if (elem.isClock()) {
				throw new RuntimeException("got a clock element: " + elem);
			}

			T elemData = visitElement.apply(elem);

			for (RoutingElement next : nextElements.apply(elem, primitives)) {

				if (next.isClock()) {
					continue;
				}

				Integer seen = seenSuccessors.get(next);

				if (seen == null) {
					seen = 0;
					successorFirstSeen.accept(elem, next, elemData);
				} else {
					successorAgainSeen.accept(elem, next, elemData);
				}

				if (nextElements.apply(next, primitives).size() == 0 && (next.isRegister(primitives) == RoutingElement.RegisterType.COMBINATORIAL
						|| next.isRegister(primitives) == RoutingElement.RegisterType.LATCH)) {
					if (next instanceof InstanceElement) {
						try {
							DotDumper.dumpInstanceOnce(next.getInstance(), primitives);
						} catch (FileNotFoundException e) {
							e.printStackTrace();
						}
					}

					if (elem instanceof Pin) {
						logger.warn("Predecessor: " + elem);
						logger.warn("Sink of " + ((Pin) elem).getNet());
						logger.warn("Net's source: " + ((Pin) elem).getNet().getSource());
						logger.warn("Number of sinks: " + (((Pin) elem).getNet().getPins().size() - (((Pin) elem).getNet().getSource() != null? 1 : 0)));
						logger.warn("Net type: " + ((Pin) elem).getNet().getType());
						logger.warn("Dead end: " + ((Pin) elem).isDeadEnd());
					}
					if (next instanceof Pin) {

						logger.warn("Next Dead end: " + ((Pin) next).isDeadEnd());
					}

					try {
						DotDumper.dumpInstanceOnce(next.getInstance(), primitives);
					} catch (FileNotFoundException e) {
						e.printStackTrace();
					}
					logger.error(next + " is a dead end in " + analysisName + "!!!");
				}


				seen++;
				seenSuccessors.put(next, seen);
				SetHelpers.putIntoSet(debugSeenPredecessors, next, elem);

				final int predCount = prevNonClockSize.applyAsInt(next, primitives);
				if (!(next.isRegister(primitives) == RoutingElement.RegisterType.REGISTER)) {
					if (predCount == seen) {
						ready.add(next);
					}
				}
			}
		}

		//Error check
		Set<RoutingElement> fail = new HashSet<>(seenSuccessors.keySet());
		fail.removeAll(visitedElements);
		if (!fail.isEmpty()) {
			System.err.println("only saw some elements partially in " + analysisName + "!");

			dumpFailing(fail, debugSeenPredecessors, visitedElements,
					seenSuccessors,
					prevElements,
					analysisName,
					prevNonClockSize,
					primitives);

		}
	}

	/**
	 * Walk the design forward, starting at the registers and call some function for each element, after all its predecessors
	 * have been visited.
	 * This will not visit elements that have isClock set to true.
	 *
	 * @param registers All active design registers, as returned by RegisterFinder
	 * @param registerInitializer Function to call for each register before visiting anything else
	 * @param visitElement called once for each element after all its predecessors have been visited. Can return some
	 * user data that will be passed to successorFirstSeen and successorAgainSeen
	 * @param successorFirstSeen Method to be called when a successor is first encountered
	 * @param successorAgainSeen Method to be called when a successor is seen again
	 */
	public static <T> void walkDesignForward(
			Set<RoutingElement> registers,
			PrimitiveDefList primitives,
			Consumer<RoutingElement> registerInitializer,
			Function<RoutingElement, T> visitElement,
			VisitSuccessor<T> successorFirstSeen,
			VisitSuccessor<T> successorAgainSeen,
			String analysisName
	) {
		walkDesign(registers,
				primitives,
				registerInitializer,
				visitElement,
				successorFirstSeen,
				successorAgainSeen,
				RoutingElement::getConnectedForward,
				RoutingElement::getConnectedBackward,
				RoutingElement::getNonClockConnectedForwardCount,
				RoutingElement::getNonClockConnectedBackwardCount,
				analysisName
		);
	}

	/**
	 * Walk the design backward, starting at the registers and call some function for each element, after all its predecessors
	 * have been visited.
	 * This will not visit elements that have isClock set to true.
	 *
	 * @param registers All active design registers, as returned by RegisterFinder
	 * @param registerInitializer Function to call for each register before visiting anything else
	 * @param visitElement called once for each element after all its predecessors have been visited. Can return some
	 * user data that will be passed to successorFirstSeen and successorAgainSeen
	 * @param successorFirstSeen Method to be called when a successor is first encountered
	 * @param successorAgainSeen Method to be called when a successor is seen again
	 */
	public static <T> void walkDesignBackward(
			Set<RoutingElement> registers,
			PrimitiveDefList primitives,
			Consumer<RoutingElement> registerInitializer,
			Function<RoutingElement, T> visitElement,
			VisitSuccessor<T> successorFirstSeen,
			VisitSuccessor<T> successorAgainSeen,
			String analysisName
	) {
		walkDesign(registers,
				primitives,
				registerInitializer,
				visitElement,
				successorFirstSeen,
				successorAgainSeen,
				RoutingElement::getConnectedBackward,
				RoutingElement::getConnectedForward,
				RoutingElement::getNonClockConnectedBackwardCount,
				RoutingElement::getNonClockConnectedForwardCount,
				analysisName
		);
	}

	/**
	 * Dump the elements that were not completely visited while walking the design
	 *
	 * @param fail The elements that were not completely seen
	 * @param debugSeenPredecessors Seen predecessors
	 * @param visited The elements that were completely seen
	 * @param seenSuccessors
	 * @param prevElements function to get predecessors (should be either RoutingElement::getConnectedForward or
	 * RoutingElement::getConnectedBackward)
	 * @param analysisName Name of the analysis
	 * @param prevNonClockSize
	 * @param primitives Primitive Definitions
	 */
	private static void dumpFailing(Set<RoutingElement> fail, Map<RoutingElement, Set<RoutingElement>> debugSeenPredecessors, Set<RoutingElement> visited,
									Map<RoutingElement, Integer> seenSuccessors, BiFunction<RoutingElement, PrimitiveDefList, Set<RoutingElement>> prevElements,
									String analysisName,
									ToIntBiFunction<RoutingElement, PrimitiveDefList> prevNonClockSize, PrimitiveDefList primitives) {

		boolean[] dump = new boolean[1];
		fail.stream().sorted(Comparator.comparing(Object::toString)).forEach(routingElement -> {

			if (otherFailed(routingElement, visited, fail, prevElements, Collections.emptyList(), analysisName, primitives)) {
				return;
			}

			dump[0] = true;

			Set<RoutingElement> missing = new HashSet<>(prevElements.apply(routingElement, primitives));
			missing.removeAll(debugSeenPredecessors.get(routingElement));


			System.err.println(routingElement + " seen: " + debugSeenPredecessors.get(routingElement));
			if (missing.isEmpty()) {
				System.err.println("but none missing! " + seenSuccessors.get(routingElement) + " of " + prevNonClockSize.applyAsInt(routingElement, primitives));
				routingElement.clearConnectedCache();
				System.err.println("but none missing after clear! " + prevElements.apply(routingElement, primitives) + " of " + prevNonClockSize.applyAsInt(routingElement, primitives));
			}

			for (RoutingElement miss : missing) {
				if (miss.isClock()) {
					continue;
				}
				System.err.println("    missing: " + miss + ", seen neighbours: " + findSeenConnected(miss, visited, prevElements, miss.toString(), primitives));
				System.err.println();
			}

		});
		if (!dump[0]) {
			throw new RuntimeException("LOOP?");
		}
	}

	/**
	 * Check if an element was not completely seen because a predecessor was also not completely seen
	 *
	 * @param elem The current element
	 * @param visitedElements The elements that were completely seen
	 * @param fail The elements that were not completely seen
	 * @param prevElements function to get predecessors (should be either RoutingElement::getConnectedForward or
	 * RoutingElement::getConnectedBackward)
	 * @param failedVisited Failed elements that have been traversed
	 * @param analysisName Name of the analysis
	 * @param primitives Primitive Definitions
	 * @return true, if another element caused this one to fail
	 */
	private static boolean otherFailed(RoutingElement elem, Set<RoutingElement> visitedElements, Set<RoutingElement> fail,
									   BiFunction<RoutingElement, PrimitiveDefList, Set<RoutingElement>> prevElements,
									   List<RoutingElement> failedVisited,
									   String analysisName,
									   PrimitiveDefList primitives) {

		Set<RoutingElement> nexts = prevElements.apply(elem, primitives);
		for (RoutingElement next : nexts) {
			if (next.isClock()) {
				continue;
			}


			List<RoutingElement> failedVisitedNext = new ArrayList<>(failedVisited);
			if (failedVisited.contains(next)) {
				throw new RuntimeException("Loop!!" + analysisName + " already saw " + next + ": " + failedVisited.stream().map(s -> s.toString().replace('\n', '_') + " " + s.isClock()).collect(Collectors.joining("\n")));
			}
			failedVisitedNext.add(next);
			if (visitedElements.contains(next)) {
				continue;
			}
			if (fail.contains(next)) {
				return true;
			}


			if (next.isRegister(primitives) != RoutingElement.RegisterType.COMBINATORIAL) {
				throw new RuntimeException("found reg: " + elem + "->" + next);
			}

			if (otherFailed(next, visitedElements, fail, prevElements, failedVisitedNext, analysisName, primitives)) {
				return true;
			}


		}
		return false;
	}

	/**
	 * Get the paths to completely seen predecessors
	 *
	 * @param elem The current element
	 * @param visitedElements The elements that were completely seen
	 * @param prevElements function to get predecessors (should be either RoutingElement::getConnectedForward or
	 * RoutingElement::getConnectedBackward)
	 * @param primitives Primitive Definitions
	 * @return The paths to completely seen predecessors
	 */
	private static Set<String> findSeenConnected(RoutingElement elem, Set<RoutingElement> visitedElements,
												 BiFunction<RoutingElement, PrimitiveDefList, Set<RoutingElement>> prevElements,
												 String path,
												 PrimitiveDefList primitives) {

		if (elem.isClock()) {
			throw new RuntimeException("trying to find seen connected of clock element: " + elem);
		}
		Set<String> res = new HashSet<>();

		Set<RoutingElement> nexts = prevElements.apply(elem, primitives);
		boolean allSeen = true;
		boolean hasNext = false;

		for (RoutingElement next : nexts) {
			if (next.isClock()) {
				continue;
			}

			hasNext = true;

			String nextStr = next.toString().replace("\r", "").replace("\n", "") + ((next instanceof Pin)? ((Pin) next).isDeadEnd() : "");
			if (path.contains(nextStr + "->")) {
				throw new RuntimeException("loop! " + path + ", to add: " + nextStr);
			}
			String p = path + "->" + nextStr;
			if (visitedElements.contains(next)) {
				res.add(p + "\n");
			} else {
				res.addAll(findSeenConnected(next, visitedElements, prevElements, p, primitives));
				allSeen = false;
			}
		}


		try {
			DotDumper.dumpInstanceOnce(elem.getInstance(), primitives);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		if (!hasNext) {
			throw new RuntimeException("Found dead end! " + elem + " num Connected clocks: " + nexts.size() + " elem.fakeReg: " + elem.isFakeRegister() + ", isClock: " + elem.isClock() + ", path: " + path);
		}
		/*if (allSeen) {
			res.clear();
			res.add(path + "->Net");
		}*/
		return res;
	}

	private static <T> Stream<T> streamIfNotNull(Collection<T> c) {
		if (c != null) {
			return c.stream();
		}
		return Stream.empty();
	}

	/**
	 * Annotate some info to all InstanceElements and Pins, and propagate that info to their neighbours.
	 *
	 * visitPin will be called once for every Pin. visitIE will be called once for each InstanceElement. After that,
	 * visitPin and visitIE will be called again for every RoutingElement returned by the previous calls to the visit
	 * methods.
	 *
	 * @param design The design
	 * @param visitPin the function to call for each Pin
	 * @param visitIE the function to call for each InstanceElement
	 */
	public static void visitAllElements(Design design, PrimitiveDefList primitives, Function<? super Pin, Stream<RoutingElement>> visitPin,
										Function<? super InstanceElement, Stream<RoutingElement>> visitIE
	) {

		final Stream<RoutingElement> startElements = Stream.concat(pinStream(design), instanceElementStream(design, primitives));

		visitElements(startElements, visitPin, visitIE);
	}

	/**
	 * Annotate some info to the start elements, and propagate that info to their neighbours.
	 *
	 * visitPin will be called once for every Pin in startElements. visitIE will be called once for each InstanceElement
	 * in startElements. After that, visitPin and visitIE will be called again for every RoutingElement returned by
	 * the previous calls to the visit methods.
	 *
	 * @param startElements the elements to start at
	 * @param visitPin the function to call for each Pin
	 * @param visitIE the function to call for each InstanceElement
	 */
	public static void visitElements(Stream<RoutingElement> startElements, Function<? super Pin, Stream<RoutingElement>> visitPin, Function<? super InstanceElement, Stream<RoutingElement>> visitIE) {
		Queue<RoutingElement> toHandle = new ArrayDeque<>();
		startElements.forEach(toHandle::add);

		while (!toHandle.isEmpty()) {
			RoutingElement re = toHandle.poll();
			Stream<RoutingElement> newHandle;
			if (re instanceof InstanceElement) {
				newHandle = visitIE.apply((InstanceElement) re);
			} else if (re instanceof Pin) {
				newHandle = visitPin.apply((Pin) re);
			} else {
				throw new RuntimeException("Unknown element: " + re);
			}
			newHandle.forEach(toHandle::add);
		}
	}

	/**
	 * Annotate some info to the start elements, and propagate that info to their neighbours.
	 *
	 * visitElement will be called once element in startElements. After that, visitElement will be called again for
	 * every RoutingElement returned by the previous calls to the visit method.
	 *
	 * @param startElements the elements to start at
	 * @param visitElement the function to call for each element
	 */
	public static void visitElements(Stream<RoutingElement> startElements, Function<RoutingElement, Stream<RoutingElement>> visitElement) {
		visitElements(startElements, visitElement, visitElement);
	}

	/**
	 * Stream of all InstanceElements in a design
	 *
	 * @param design
	 * @param primitives
	 * @return
	 */
	public static Stream<InstanceElement> instanceElementStream(Design design, PrimitiveDefList primitives) {
		return design.getInstances().stream().flatMap(i -> {
			PrimitiveDef prim = primitives.getPrimitiveDef(i.getType());
			return prim.getElements().stream().map(e -> i.getInstanceElement(e, primitives));
		});
	}

	/**
	 * Stream of all Pins in a Design
	 *
	 * @param design
	 * @return
	 */
	public static Stream<Pin> pinStream(Design design) {
		return design.getInstances().stream().flatMap(i -> i.getPins().stream());
	}

	/**
	 * Stream of all pins and InstanceElements in a design
	 *
	 * @param design
	 * @param primitives
	 * @return
	 */
	public static Stream<RoutingElement> routingElementStream(Design design, PrimitiveDefList primitives) {
		return Stream.concat(pinStream(design), instanceElementStream(design, primitives));
	}

	/**
	 * Walk along a path, starting at current and choosing successors by calling nextElements, until isTerminal is Set.
	 * When isTerminal returns true, a result path will be built by calling accumulator for all elements along the path, in backward
	 * order. If nextElements returns an empty stream, the partial path will be discarded.
	 *
	 * @param current
	 * @param isTerminal
	 * @param nextElements
	 * @param accumulator Accumulate all elements. Will be called with null for the accumulated value on the end of the paths
	 * @param <R>
	 * @return
	 */
	private static <T, R> Stream<R> walkPath(T current,
											 BiPredicate<T, Integer> isTerminal,
											 BiFunction<T, Integer, Stream<? extends T>> nextElements,
											 BiFunction<R, T, R> accumulator,
											 BinaryOperator<R> reducer,
											 Function<List<T>, R> loopHandler,
											 List<T> onPath,
											 int depth) {


		if (isTerminal.test(current, depth)) {
			return Stream.of(accumulator.apply(null, current));
		} else {
			Stream<? extends T> nexts = nextElements.apply(current, depth);


			Stream<R> nextRes;

			List<T> nextOnPath = new ArrayList<>(onPath);
			if (nextOnPath.contains(current)) {
				nextOnPath.add(current);
				nextRes = Stream.of(loopHandler.apply(nextOnPath));
			} else {
				nextOnPath.add(current);
				nextRes = nexts
						.flatMap((T next) -> walkPath(next, isTerminal, nextElements, accumulator, reducer, loopHandler, nextOnPath, depth + 1))
						.map(r -> accumulator.apply(r, current));
			}

			if (reducer != null) {
				return nextRes.reduce(reducer).map(Stream::of).orElse(Stream.empty());
			} else {
				return nextRes;
			}
		}
	}

	/**
	 * Walk along a path, starting at current and choosing successors by calling nextElements, until isTerminal is Set.
	 * When isTerminal returns true, a result path will be built by calling accumulator for all elements along the path, in backward
	 * order. If nextElements returns an empty stream, the partial path will be discarded.
	 *
	 * @param current
	 * @param isTerminal Check if the supplied element is the end of the path. the second parameter indicates the depth of the current element.
	 * @param nextElements Get next elements. called with current element and depth.
	 * @param accumulator Accumulate all elements. Will be called with null for the accumulated value on the end of the paths
	 * @param reducer function to call with all subresults, that will build a single result. Can be null if all results should be returned
	 * @param loopHandler gets called if a loop is found in the path. Most often, you want to throw an exception here
	 * @param <R>
	 * @return
	 */
	public static <T, R> Stream<R> walkPathDepth(T current,
												 BiPredicate<T, Integer> isTerminal,
												 BiFunction<T, Integer, Stream<? extends T>> nextElements,
												 BiFunction<R, T, R> accumulator,
												 BinaryOperator<R> reducer,
												 Function<List<T>, R> loopHandler) {


		return walkPath(current, isTerminal, nextElements, accumulator, reducer, loopHandler, new ArrayList<>(), 0);
	}

	/**
	 * Walk along a path, starting at current and choosing successors by calling nextElements, until isTerminal is Set.
	 * When isTerminal returns true, a result path will be built by calling accumulator for all elements along the path, in backward
	 * order. If nextElements returns an empty stream, the partial path will be discarded.
	 *
	 * @param current
	 * @param isTerminal Check if the supplied element is the end of the path. the second parameter indicates if the element
	 * is the first of the path
	 * @param nextElements Get next elements. called with current element and depth.
	 * @param accumulator Accumulate all elements. Will be called with null for the accumulated value on the end of the paths
	 * @param reducer function to call with all subresults, that will build a single result. Can be null if all results should be returned
	 * @param loopHandler gets called if a loop is found in the path. Most often, you want to throw an exception here
	 * @param <R>
	 * @return
	 */
	public static <T, R> Stream<R> walkPath(T current,
											BiPredicate<T, Boolean> isTerminal,
											BiFunction<T, Integer, Stream<? extends T>> nextElements,
											BiFunction<R, T, R> accumulator,
											BinaryOperator<R> reducer,
											Function<List<T>, R> loopHandler) {


		return walkPath(current, (element, depth) -> isTerminal.test(element, depth == 0), nextElements, accumulator, reducer, loopHandler, new ArrayList<>(), 0);
	}

}
