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
package edu.byu.ece.rapidSmith.design;

/**
 * This enum is simply a way to check net types easier than using Strings.
 * @author Chris Lavin
 * Created on: Jun 22, 2010
 */
public enum NetType{
	WIRE("wire"),
	GND("gnd"),
	VCC("vcc"),
	UNKNOWN("?????");

	private final String xdlName;

	NetType(String xdlName) {
		this.xdlName = xdlName;
	}

	@Override
	public String toString() {
		return xdlName;
	}
}
