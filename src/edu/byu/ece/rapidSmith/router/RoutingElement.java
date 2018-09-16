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

import edu.byu.ece.rapidSmith.design.Instance;
import edu.byu.ece.rapidSmith.primitiveDefs.PrimitiveDefList;
import edu.byu.ece.rapidSmith.timing.routing.DelayModel;

import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Created by wenzel on 31.07.14.
 */
public abstract class RoutingElement {
	public abstract Set<RoutingElement> getConnectedForward(PrimitiveDefList primitives);
	public abstract Set<RoutingElement> getConnectedBackward(PrimitiveDefList primitives);

	public abstract int getNonClockConnectedBackwardCount(PrimitiveDefList primitives);
	public abstract int getNonClockConnectedForwardCount(PrimitiveDefList primitives);


	private double requireTime;
	private double arrivalTime;

	private boolean isClock;

	private boolean fakeRegister;

	private boolean isStatic;

	/**
	 * Arrival time at the input.
	 * For registers, this means that the output delay of 0 will not be saved.
	 * Note that all delays are modeled to occur on the connections and none internal to the elements.
	 * @return arrival time
	 */
	public double getArrivalTimeInput() {
		return arrivalTime;
	}

	public abstract double getDelayToSuccessor(RoutingElement successor, DelayModel delayModel, PrimitiveDefList primitives, BiConsumer<RoutingElement, RoutingElement> unknownDelayConsumer);

	public void setArrivalTimeInput(double arrivalTime) {
		this.arrivalTime = arrivalTime;
	}

	public double getArrivalTimeOutput(PrimitiveDefList primitives) {
		if (this.isRegister(primitives)==RegisterType.REGISTER)
			return 0;
		return arrivalTime;
	}

	/**
	 * Require time at output.
	 * For registers, this means that the input require time of maxDelay will not be saved.
	 * Note that all delays are modeled to occur on the connections and none internal to the elements.
	 * @return
	 */
	public double getRequireTimeOutput() {
		return requireTime;
	}

	public double getRequireTimeInput(PrimitiveDefList primitives, double maxDelay) {
		if (this.isRegister(primitives)==RegisterType.REGISTER)
			return maxDelay;
		return requireTime;
	}

	public void setRequireTimeOutput(double requireTime) {
		this.requireTime = requireTime;
	}


	public enum RegisterType {
		COMBINATORIAL,
		LATCH,
		REGISTER,
		OFF;
	}

	public abstract RegisterType isRegister(PrimitiveDefList primitives);

	public double getSlackToSuccessor(RoutingElement successor, DelayModel delayModel, PrimitiveDefList primitives, double maxDelay) {
		double arrivalTime = this.getArrivalTimeOutput(primitives);
		double requireTime = successor.getRequireTimeInput(primitives,maxDelay);
		return requireTime-arrivalTime-getDelayToSuccessor(successor,delayModel,primitives, (x,y)->{});
	}


	public boolean isClock() {
		return isClock;
	}

	public void setIsClock(boolean isClock) {
		this.isClock = isClock;
	}

	public abstract Instance getInstance();

	public boolean isFakeRegister() {
		return fakeRegister;
	}

	public void setFakeRegister(boolean fakeRegister) {
		this.fakeRegister = fakeRegister;
	}


	public boolean isStatic() {
		return isStatic;
	}

	public void setStatic(boolean aStatic) {
		isStatic = aStatic;
	}

	public void clearConnectedCache() {

	}
}
