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
package edu.byu.ece.rapidSmith.util;

import edu.byu.ece.rapidSmith.design.Design;
import edu.byu.ece.rapidSmith.design.Net;
import edu.byu.ece.rapidSmith.design.NetType;
import edu.byu.ece.rapidSmith.design.Pin;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;


public class UnrouteNets {
	
	public static ArrayList<Net> combineStaticNets(Collection<Net> nets){
		ArrayList<Net> gndNets = new ArrayList<>();
		ArrayList<Net> vccNets = new ArrayList<>();
		for(Net net : nets){
			if(net.getType().equals(NetType.GND)) {
				//System.out.println("unrouting gnd "+net);
				gndNets.add(net);
			}
			else if(net.getType().equals(NetType.VCC)) {
				//System.out.println("unrouting vcc " + net);
				vccNets.add(net);
			}
		}

		nets.removeAll(gndNets);
		nets.removeAll(vccNets);


		Net gndNet = new Net();
		gndNet.setName("GLOBAL_LOGIC0");
		gndNet.setType(NetType.GND);
		for (Net net : gndNets) {
			for (Pin pin : net.getPins()) {
				if (!pin.isOutPin()) {
					gndNet.addPin(pin);
				}
			}
		}
		
		Net vccNet = new Net();
		vccNet.setName("GLOBAL_LOGIC1");
		vccNet.setType(NetType.VCC);
		for (Net net : vccNets) {
			for (Pin pin : net.getPins()) {
				if (!pin.isOutPin()) {
					vccNet.addPin(pin);
				}
			}
		}

		ArrayList<Net> newNets = new ArrayList<>(nets);
		newNets.add(gndNet);
		newNets.add(vccNet);
		return newNets;
	}

	public static void main(String[] args){
		if(args.length != 2){
			System.out.println("USAGE: <input.xdl> <output.xdl>");
			System.exit(0);
		}
		Design design = new Design();
		
		design.loadXDLFile(Paths.get(args[0]));
		
		design.unrouteDesign();

		design.setNets(combineStaticNets(design.getNets()));

		design.fixSp6IOBs();
		
		design.saveXDLFile(Paths.get(args[1]), true, true);
	}
	
}
