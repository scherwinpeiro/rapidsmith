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
 * 
 */
package edu.byu.ece.rapidSmith.timing;


import edu.byu.ece.rapidSmith.design.Instance;
import edu.byu.ece.rapidSmith.design.Net;
import edu.byu.ece.rapidSmith.timing.logic.LogicPathElement;

import java.util.stream.Collectors;

public class PathDelay extends Path {

	/**
	 * Delay or offset in nanoseconds (data path - clock path skew + uncertainty)
	 */
	private float delay;
	/**
	 * Skew estimated for the clock path
	 */
	private float clockPathSkew;
	/**
	 * The net driving the clock on the source register
	 */
	private Net sourceClock;
	/**
	 * Slack in nanoseconds
	 */
	private float slack;

	private float delayConstraint;

	public PathDelay(float delay) {
		this.delay = delay;
	}

	public PathDelay() {

	}


	/**
	 * @return the delay
	 */
	public float getDelay() {
		return delay;
	}

	/**
	 * @param delay the delay to set
	 */
	public void setDelay(float delay) {
		this.delay = delay;
	}

	/**
	 * @return the clockPathSkew
	 */
	public float getClockPathSkew() {
		return clockPathSkew;
	}

	/**
	 * @param clockPathSkew the clockPathSkew to set
	 */
	public void setClockPathSkew(float clockPathSkew) {
		this.clockPathSkew = clockPathSkew;
	}

	/**
	 * @return the sourceClock
	 */
	public Net getSourceClock() {
		return sourceClock;
	}

	/**
	 * @param sourceClock the sourceClock to set
	 */
	public void setSourceClock(Net sourceClock) {
		this.sourceClock = sourceClock;
	}

	public float getSlack() {
		return slack;
	}

	public void setSlack(float slack) {
		this.slack = slack;
	}

	public float getDelayConstraint() {
		return delayConstraint;
	}

	public void setDelayConstraint(float delayConstraint) {
		this.delayConstraint = delayConstraint;
	}

	private static String padRight(String s, int n) {
		return String.format("%1$-" + n + "s", s);
	}

	@Override
	public String toString() {
		StringBuilder res = new StringBuilder();

		res.append("Source:               ");
		res.append(getSource());
		res.append('\n');
		res.append("Destination:          ");
		res.append(getDestination());
		res.append('\n');
		res.append("Data Path Delay:      ");
		res.append(getDelay());
		res.append(" (Levels of Logic = ");
		res.append(getLevelsOfLogic());
		res.append(")\n");
		res.append('\n');

		for (PathElement element : getMaxDataPath()) {
			res.append("  ");
			String pinName = element.getLogicalName();

			Instance inst = null;
			if (element.getPin() != null) {
				inst = element.getPin().getInstance();
			} else if (element instanceof LogicPathElement) {
				inst = ((LogicPathElement) element).getInstance();

			}

			if (element.getPin() == null) {
				pinName = inst != null? inst.getPrimitiveSiteName() : "NULL";
			} else {
				pinName = inst.getPrimitiveSiteName() + '.' + element.getPin().getName();
			}
			res.append(padRight(pinName, 24));
			res.append(padRight(element.getType(), 10));
			res.append(String.format("%7.3f", element.getDelay()));
			res.append("   ");
			if (element.getPin() != null) {
				res.append(element.getPin().getNet().getName());
			} else if (element instanceof LogicPathElement && ((LogicPathElement) element).getLogicalResources() != null) {
				res.append(((LogicPathElement) element).getLogicalResources().stream().collect(Collectors.joining(" ")));
			} else {
				res.append("Null");
			}
			res.append('\n');
		}
		res.append("  -----------------------------------------------------------------------\n");
		res.append("  Total                             ");
		res.append(String.format("%7.3fns (%.3fns logic, %.3fns route)", getDelay(), getDataPathDelay(), getRoutingDelay()));

		res.append('\n');

		return res.toString();
	}

}
