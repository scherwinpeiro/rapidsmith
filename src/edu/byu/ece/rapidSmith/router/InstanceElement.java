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

package edu.byu.ece.rapidSmith.router;

import edu.byu.ece.rapidSmith.design.Attribute;
import edu.byu.ece.rapidSmith.design.Design;
import edu.byu.ece.rapidSmith.design.Instance;
import edu.byu.ece.rapidSmith.design.NetType;
import edu.byu.ece.rapidSmith.design.Pin;
import edu.byu.ece.rapidSmith.primitiveDefs.*;
import edu.byu.ece.rapidSmith.timing.routing.DelayModel;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Created by wenzel on 31.07.14.
 */
public class InstanceElement extends RoutingElement {

	private final Instance instance;
	private final Element element;
	private final PrimitiveDef primitiveDef;
	private final Design design;
	private int connectedBackwardNonClockCount;
	private int connectedForwardNonClockCount;

	public boolean isDisabled() {
		return disabled;
	}

	public void setDisabled(boolean disabled) {
		this.disabled = disabled;
	}

	private boolean disabled;

	public Design getDesign() {
		return design;
	}

	public Element getElement() {
		return element;
	}


	public Instance getInstance() {
		return instance;
	}

	public Attribute getAttribute() {
		if (instance == null) {
			return null;
		}
		return instance.getAttribute(element.getName());
	}


	@Override
	public String toString() {
		Attribute a = instance.getAttribute(element.getName());
		if (element.getName().contains("LUT")) {
			a = null; //Do not show lut contents, as they are sooo long
		}
		if (a != null) {
			return instance.getName() + "(" + instance.getType() + ")." + element.getName() + "(" + (a.isMultiValueAttribute()? a.getMultiValueValues() : a.getValue()) + ", " + a.getLogicalName() + ")";
		} else {
			return instance.getName() + "(" + instance.getType() + ")." + element.getName();
		}

	}

	@Override
	public boolean equals(Object o) {

		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}

		InstanceElement that = (InstanceElement) o;

		if (element != null? !element.equals(that.element) : that.element != null) {
			return false;
		}
		if (instance != null? !instance.equals(that.instance) : that.instance != null) {
			return false;
		}

		return true;
	}

	@Override
	public int hashCode() {
		int result = element != null? element.hashCode() : 0;
		result = 31 * result + (instance != null? instance.hashCode() : 0);
		return result;
	}


	public InstanceElement(Element element, PrimitiveDef primitiveDef, PrimitiveDefList primitives, Design design) {
		this.instance = null;
		this.element = element;
		this.primitiveDef = primitiveDef;
		this.design = design;
	}

	public InstanceElement(Instance instance, Element element, PrimitiveDefList primitives, Design design) {
		this.instance = instance;
		this.element = element;
		this.primitiveDef = primitives.getPrimitiveDef(instance.getType());
		this.design = design;
	}


	public enum IsPin {
		NO_PIN,
		INPIN,
		OUTPIN
	}

	public IsPin isPin() {

		for (PrimitiveDefPin pin : primitiveDef.getPins()) {
			if (pin.getInternalName().equals(element.getName())) {
				IsPin res = pin.isOutput()? IsPin.OUTPIN : IsPin.INPIN;
				return res;
			}
		}
		return IsPin.NO_PIN;
	}

	public Pin getExternalPin() {
		return instance.getPin(element.getName());
	}

	private boolean addExternalPin(Set<RoutingElement> set) {
		Pin p = getExternalPin();
		if (p != null && !p.isDeadEnd()) {
			set.add(p);
			return true;
		}
		return false;
	}

	private Set<RoutingElement> connectedForward;
	private Set<RoutingElement> connectedBackward;

	public void clearConnectedCache() {
		connectedBackward = null;
		connectedForward = null;
	}

	private int getConnected(PrimitiveDefList primitives, boolean isForward, Set<RoutingElement> connected) {
		int connectedNonClockCount = 0;

		//Check if the element is a pin
		if ((isPin() == IsPin.INPIN && !isForward) || (isPin() == IsPin.OUTPIN && isForward)) {
			if (addExternalPin(connected)) {
				if (!getExternalPin().isClock()) {
					connectedNonClockCount++;
				}
			}
		} else {

			for (Connection connection : element.getConnections()) {
				if ((connection.isForwardConnection() == isForward)) {
					InstanceElement elem = instance.getInstanceElement(connection.getElement1(primitiveDef), primitives);
					boolean en0 = isPinEnabled(connection.getPin0(primitiveDef), primitives);
					boolean en1 = elem.isPinEnabled(connection.getPin1(primitiveDef), primitives);
					if (en0 && en1) {
						if (connected.add(elem) && !elem.isClock()) {
							connectedNonClockCount++;
						}
					}
				}
			}
		}
		if (connected.size() < connectedNonClockCount) {
			throw new RuntimeException("error");
		}
		return connectedNonClockCount;
	}

	public Set<RoutingElement> getConnectedBackward(PrimitiveDefList primitives, boolean forceRecalc) {
		if (connectedBackward == null || forceRecalc) {
			Set<RoutingElement> res = new HashSet<>();
			connectedBackwardNonClockCount = getConnected(primitives, false, res);
			connectedBackward = Collections.unmodifiableSet(res);
		}
		return connectedBackward;
	}

	public Set<RoutingElement> getConnectedForward(PrimitiveDefList primitives, boolean forceRecalc) {
		if (connectedForward == null || forceRecalc) {
			Set<RoutingElement> res = new HashSet<>();
			connectedForwardNonClockCount = getConnected(primitives, true, res);
			connectedForward = Collections.unmodifiableSet(res);
		}
		return connectedForward;
	}

	@Override
	public Set<RoutingElement> getConnectedForward(PrimitiveDefList primitives) {
		return getConnectedForward(primitives, false);
	}

	/**
	 * Some primitives have elements with pins that can be disabled (e.g. MUXes). This checks for
	 * them.
	 *
	 * @param pin
	 * @return
	 */
	public boolean isPinEnabled(PrimitiveDefPin pin, PrimitiveDefList primitives) {

		if (disabled) {
			return false;
		}

		Attribute a = getAttribute();

		String name = element.getName();

		if (a != null && a.getValue().equals("#OFF")) {
			return false;
		}

		if (a == null && disabledWithoutAttribute.contains(name)) {
			return false;
		}

		//Is it a mux?
		if (name.endsWith("MUX") || name.endsWith("CY0") || name.equals("PRECYINIT")) {
			//Some mux definitions are incomplete, and we don't know anything.
			//Defaulting to pin enabled
			if (element.getCfgOptions() == null) {
				return true;
			}
			//Is it a configurable pin?
			if (!element.getCfgOptions().contains(pin.getInternalName())) {
				return true;
			}
			if (a == null) {
				return true;
			}
			return a.getValue().equals(pin.getInternalName());
		}

		//Is it a pass element?
		if (name.endsWith("USED")) {
			if (a == null) {
				return true;
			}
			return !a.getValue().equals("#OFF");
		}

		//Is it a lookup table?
		if (name.endsWith("LUT")) {
			if (a == null) {
				return true;
			}
			//If the lut is unused, all pins are unused
			if (a.getValue().equals("#OFF")) {
				return false;
			}
			//Check if the pin name is mentioned in the LUT's function or if it's a RAM
			return a.getValue().contains(pin.getInternalName()) || a.getValue().contains("RAM");
		}

		IsPin p = isPin();
		if (p != IsPin.NO_PIN) {
			return getExternalPin() != null;
		}

		//No longer needed because we are now inferring unused stuff!
		/*if (isNextDisabledMux(primitives))
			return false;*/

		return true; //If something else, it's always enabled
	}

	/*private boolean isNextDisabledMux(PrimitiveDefList primitives) {

		boolean successorFound = false;
		boolean res = false;
		for (Connection connection : element.getConnections()) {
			if (connection.isForwardConnection() && connection.getPin0(primitiveDef).isOutput()) {

				//Already saw a successor
				if (successorFound)
					return false;
				successorFound=true;

				InstanceElement elem = instance.getInstanceElement(connection.getElement1(primitiveDef), primitives);

				if (elem.element.getName().endsWith("MUX") && !elem.isPinEnabled(connection.getPin1(primitiveDef),primitives))
					res=true;
			}
		}
		return res;
	}*/

	@Override
	public Set<RoutingElement> getConnectedBackward(PrimitiveDefList primitives) {
		return getConnectedBackward(primitives, false);
	}

	@Override
	public int getNonClockConnectedBackwardCount(PrimitiveDefList primitives) {
		if (connectedBackward == null) {
			getConnectedBackward(primitives);
		}
		return connectedBackwardNonClockCount;
	}


	@Override
	public int getNonClockConnectedForwardCount(PrimitiveDefList primitives) {
		if (connectedForward == null) {
			getConnectedForward(primitives);
		}
		return connectedForwardNonClockCount;
	}


	public final static Set<String> failedPaths = new HashSet<>();

	Map<RoutingElement, Double> delayCache = null;

	private double calcDelayToSuccessor(RoutingElement successor, PrimitiveDefList primitives, BiConsumer<RoutingElement, RoutingElement> unknownDelayConsumer) {
		if (successor instanceof Pin) {
			if (isPin() == IsPin.OUTPIN && successor == getExternalPin()) {
				return 0;
			} else {
				throw new RuntimeException(this + " is not connected to " + successor);
			}
		} else if (successor instanceof InstanceElement) {
			if (((InstanceElement) successor).getInstance() != getInstance()) {
				throw new RuntimeException("Successor does not belong to the same instance!");
			}
			PrimitiveDef prim = primitives.getPrimitiveDef(instance.getType());
			for (Connection connection : element.getConnections()) {
				if (connection.isForwardConnection()
						&& connection.getElement0(prim) == this.getElement()
						&& connection.getElement1(prim) == ((InstanceElement) successor).getElement()) {
					double delay = connection.getDelay();
					if (delay == Float.POSITIVE_INFINITY) {
						if (isClock()) {
							throw new RuntimeException("tried to get delay of clock element!");
						}
						//System.err.println("Path with unknown delay found: " + this + " to " + successor);
						String p = this.getInstance().getType() + "." + this.getElement().getName() + "->" + successor.getInstance().getType() + "." + ((InstanceElement) successor).getElement().getName();
						/*if (failedPaths.add(p))
							System.err.println("unknown delay: "+p);*/
						unknownDelayConsumer.accept(this, successor);
						return 100000;
					}
					return delay;
				}
			}
			throw new RuntimeException("Did not find a connection to successor: " + this + "->" + successor);

		} else {
			throw new RuntimeException("Invalid successor: " + successor);
		}
	}

	@Override
	public double getDelayToSuccessor(RoutingElement successor, DelayModel delayModel, PrimitiveDefList primitives, BiConsumer<RoutingElement, RoutingElement> unknownDelayConsumer) {
		if (isStatic())
			return 0;

		if (delayCache == null) {
			delayCache = getConnectedForward(primitives).stream().collect(Collectors.toMap(Function.identity(), s -> calcDelayToSuccessor(s, primitives, unknownDelayConsumer)));
		}

		return delayCache.get(successor);
	}


	private static List<String> alwaysRegister = Arrays.asList(
			//IOB
			//"OUTBUF","INBUF",
			"PAD",
			//TIEOFF
			"HARD0GND", "HARD1VCC", "KEEP1VCC",
			//Complex components that only have registered outputs
			"MCB", "RAMB8BWER", "RAMB16BWER",

			"IODRP2_MCB", "IODRP2" //TODO Really???

	);

	private static List<String> disabledWithoutAttribute = Arrays.asList(
			//IOB
			"INBUF", "OUTBUF", "PULL"
	);

	private static String replaceAllUntilNoChange(String in, String regex, String replace) {
		while (true) {
			String r = in.replaceAll(regex, replace);
			if (r.equals(in)) {
				return r;
			}
			in = r;
		}

	}

	public static String simplifyLutConfigString(String config) {
		//Replace input or'd with itself (like A6+~A6) with 1
		String r = config.replaceAll("(A[1-6])\\+~\\1", "1");
		//Remove parentheses around 1 and 0
		r = r.replaceAll("\\(([01])\\)", "$1");
		//Replace 1*1 with 1
		//We need to keep replacing, otherwise 1*1*1 will only get reduced to 1*1, not 1
		r = replaceAllUntilNoChange(r, "1\\*1", "1");
		//Replace 1*0, 0*1 and 0*0 with 0
		//Also matches 1*1, but those have already been replaced
		r = r.replaceAll("[01]\\*[01]", "0");
		return r;
	}

	public RegisterType isRegister(PrimitiveDefList primitives) {

		Attribute attr = getAttribute();
		if (attr != null && attr.getValue().equals("#OFF")) {
			return RegisterType.OFF;
		}

		if (isFakeRegister()) {
			return RegisterType.REGISTER;
		}

		//Is it a flipflop?
		if (element.getName().matches("(([A-D]5?)|OUT|I)FF")) {
			if (attr == null) {
				return RegisterType.OFF;
			}
			String val = attr.getValue();
			if (val.equals("#FF") || val.isEmpty()) {
				return RegisterType.REGISTER;
			}
			if (val.equals("#OFF")) {
				return RegisterType.OFF;
			}
			if (val.equals("#LATCH")) {
				return RegisterType.LATCH;
			}
			if (val.equals("#AND2L") || val.equals("#OR2L")) {
				return RegisterType.COMBINATORIAL;
			}
			throw new RuntimeException("Unknown FF configuration found. it is a \"" + attr.getValue() + "\" in " + instance.getName() + ", " + instance.getType().toString() + "." + element.getName());
		}

		//Distributed ram
		if (element.getName().endsWith("LUTWRITE")) {
			if (getInstance() == null) {
				return RegisterType.REGISTER;
			}
			final String lutAttrName = getElement().getName().replace("WRITE", "");
			final Attribute lutAttr = getInstance().getAttribute(lutAttrName);
			if (lutAttr == null) {
				return RegisterType.OFF;
			}

			if (lutAttr.getValue().contains("RAM")) {
				return RegisterType.REGISTER;
			} else {
				return RegisterType.OFF;
			}
		}
		if (element.getName().endsWith("LUT")) {
			if (getAttribute() != null) {
				//LUTs can be sources for static 0/1
				if (simplifyLutConfigString(getAttribute().getValue()).matches("#LUT:O[56]=[01]")) {
					return RegisterType.REGISTER;
				}
			}
			return RegisterType.COMBINATORIAL;
		}


		//TODO model DSP48A1 in more detail, it can have both combinatorial and registered paths
		if (element.getName().equals("DSP48A1")) {
			final Pin clkPin = instance.getPin("CLK");
			boolean hasClock = clkPin != null && clkPin.getNet() != null && clkPin.getNet().getType() == NetType.WIRE;
			return hasClock? RegisterType.REGISTER : RegisterType.COMBINATORIAL;
		}

		//Check in list
		return alwaysRegister.contains(element.getName())? RegisterType.REGISTER : RegisterType.COMBINATORIAL;
	}
}
