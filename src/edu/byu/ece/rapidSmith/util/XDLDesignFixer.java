package edu.byu.ece.rapidSmith.util;

import edu.byu.ece.rapidSmith.design.Design;
import edu.byu.ece.rapidSmith.design.Net;
import edu.byu.ece.rapidSmith.design.PIP;
import edu.byu.ece.rapidSmith.design.Pin;
import edu.byu.ece.rapidSmith.device.Device;
import edu.byu.ece.rapidSmith.device.Tile;
import edu.byu.ece.rapidSmith.device.WireConnection;
import edu.byu.ece.rapidSmith.device.WireEnumerator;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Check the design and the internal rapidsmith representation. 
 * Check for antennas in the design and check if there are conflicts with pips in the design and in the device
 * Walk through all nets and check every connection between all input and output pins
 * @since 10.09.2016
 * @author Stefan Klir, Felix Pels
 */

/**
 * TODO:
 * 
 */

public class XDLDesignFixer {
	/** Save all PIPs for each in the design needed Tile */
	private static HashMap<Tile, ArrayList<PIP>> tilePIPMap;
	/** Error counter */
	private static int numberOfErrors;
	/** Save error Pins with no further connection */
	private static HashMap<Net, HashMap<Pin, Boolean>> errorPins;
	/** Save all unused PIPs for the forward and the backward walk through for each NET in the design */
	private static HashMap<Net, HashMap<Boolean, ArrayList<PIP>>> unusedPIPMap;
	/** Net incomplete input, output pins -> in or output pins are missing */
	private static ArrayList<Net> incompleteNet;
	
	private static boolean debug = false;
	private static boolean minimalOutput = true;
	
	/**
	 * Main Method
	 * @param args user defined .xdl files
	 */
	public static void main(String[] args){
		if(args.length > 2 || args.length==0){
			MessageGenerator.briefMessageAndExit("USAGE: <input.xdl> or <input.xdl> [debug(true, false)]");
		}
		long before = System.nanoTime();
		
		int slash = args[0].lastIndexOf('/');
		String filePath = args[0].substring(0, slash);
		String filename = args[0].substring(slash, args[0].lastIndexOf('.'));
		
		MessageGenerator.briefMessage("== Load XDL ==");
		//Read the xdl file
		Design design = new Design();
		design.loadXDLFile(Paths.get(args[0]));
		design.fixSp6IOBs();
		
		if(args.length > 1) {
			debug = args[1].equals("true");
			minimalOutput = !debug;
		}

		//Do something
		tilePIPMap = new HashMap<Tile, ArrayList<PIP>>();
		errorPins = new HashMap<Net, HashMap<Pin, Boolean>>();
		unusedPIPMap = new HashMap<Net, HashMap<Boolean, ArrayList<PIP>>>();
		numberOfErrors = 0;
		incompleteNet = new ArrayList<Net>();

		verifyPIPs(design);
		//HashMap<Integer, ArrayList<Tile>> antennaWireUsage = checkForAntennas(design);
		checkForAntennas(design);

		//Write the xdl out
		System.out.println("");
		MessageGenerator.briefMessage("== Write XDL ==");
		System.out.println(" File: " + filePath + "/out" + filename + "_out.xdl");
		design.saveXDLFile(Paths.get(filePath + "/out" + filename + "_out.xdl"));
		
		long after = System.nanoTime();
		MessageGenerator.briefMessage("== Finished ==");
		double duration = (after - before) / 1E9;
        System.out.println("Required Time:  " + duration);

	}

	/**
	 * Check for antennas in the design. Check for connections which has no source or no sink
	 * @param design user defined design
	 */
	private static HashMap<Integer, ArrayList<Tile>> checkForAntennas(Design design) {		
		System.out.println("===================================================");
		System.out.println("=               Check for Antennas                =");
		System.out.println("===================================================");
		
		if(!minimalOutput) System.out.println("");
		if(!minimalOutput) System.out.println("=============== BACKWARD - Search =================");
		Map<Net, List<PIP>> error1 = seachFromSinkToSource(design);	//backward
		if(!minimalOutput) System.out.println("");
		if(!minimalOutput) System.out.println("================ FORWARD - Search =================");
		Map<Net, List<PIP>> error2 = seachFromSourceToSink(design);	//forward
		
		//Ausgabe der FEHLER
		HashMap<Integer, ArrayList<Tile>> antennaWireUsage;
		System.out.println("");
		System.out.println("#Backward errors: " + error1.size() + ", #Forward errors: " + error2.size() + ", #Pin errors: " + errorPins.size() + ", #Incomplete Nets: " + incompleteNet.size());
		System.out.println("");
		
		if(incompleteNet.size() > 0) {
			System.out.println("================= Error - Incomplete Nets - Summary =================");
			for(Net net : incompleteNet){				
				System.out.println(" " + net);
			}
		}
		
		if((error2.size() > 0 && errorPins.size() > 0) || (error1.size() > 0 && errorPins.size() > 0) || (error1.size() > 0 && error2.size() > 0)) {
			antennaWireUsage = printErrorSummary(error1, error2, design);
			printErrorUsage(design, antennaWireUsage);
			
			if((antennaWireUsage.size() == 0) && (incompleteNet.size() == 0)){
				System.out.println("");
				System.out.println("====================== NO Errors =======================");
			}
		}
		else {
			if(incompleteNet.size() == 0){
				System.out.println("");
				System.out.println("====================== NO Errors =======================");
			}
			antennaWireUsage = new HashMap<Integer, ArrayList<Tile>>();
		}
		
		return antennaWireUsage;
	}
	/*	
	private static boolean fixPinError(Design design, HashMap<Net, ArrayList<PIP>> forwardErrors) {
		boolean somethingFixed = false;
		//check every error pin
		for(Net net : errorPins.keySet()){
			for(Pin errorPin : errorPins.get(net).keySet()){
				
				//check every end wire connection
				for(Net net2 : forwardErrors.keySet()){
					for(PIP pip : forwardErrors.get(net2)){
						
						//if there is a hardwire with partialiy the same name
						WireConnection[] wireCons = pip.getTile().getWireConnections(pip.getEndWire());	
						if(wireCons != null){
							for(int w = 0; w < wireCons.length; w++){
								if(wireCons[w].getTile(errorPin.getTile()) != null){
									String wireName = WireEnumerator.getInstance(design.getFamilyType()).getWireName(wireCons[w].getWire());
									System.out.println(wireName);
									if(wireName.contains(errorPin.getName())) {
										System.out.println(" HIT in tile: " + errorPin.getTile().getName() + " " + wireName + " with pin " + errorPin.getName());
									}
								}
							}
						}
					}
				}
			}
		}
		return somethingFixed;
	}*/

	/**
	 * Print a usage summary on the terminal. This will summarize for each wire the number 
	 * of tiles in which the error occurs and display in how many tiles the wire is represented in total
	 * @param design user defined design
	 * @param antennaWireUsage A HashMap which stores for each wire (key) the error tiles in which it occurs
	 */
	private static void printErrorUsage(Design design, HashMap<Integer, ArrayList<Tile>> antennaWireUsage) {
		if(antennaWireUsage != null){
			HashMap<Integer, ArrayList<Tile>> designWireInTileUsage = new HashMap<Integer, ArrayList<Tile>>();
			//Check design for wire+tile usage
			for(Net net : design.getNets()){
				for(PIP pip : net.getPIPs()){
					if(antennaWireUsage.containsKey(pip.getStartWire()) || antennaWireUsage.containsKey(pip.getEndWire())) {
						if(antennaWireUsage.containsKey(pip.getStartWire())) {
								if(!designWireInTileUsage.containsKey(pip.getStartWire())) designWireInTileUsage.put(pip.getStartWire(), new ArrayList<Tile>());
								designWireInTileUsage.get(pip.getStartWire()).add(pip.getTile());
						}
						else {
							if(!designWireInTileUsage.containsKey(pip.getEndWire())) designWireInTileUsage.put(pip.getEndWire(), new ArrayList<Tile>());
							designWireInTileUsage.get(pip.getEndWire()).add(pip.getTile());
						}
					}
				}
			}
			
			//Write usage out
			System.out.println("================= Error - Usage Summary =================");
			for(Integer wire : antennaWireUsage.keySet()){
				System.out.println("");
				System.out.println("Current Wire: " + WireEnumerator.getInstance(design.getFamilyType()).getWireName(wire));
				if(designWireInTileUsage.get(wire) != null) System.out.println(" Occurence #Tiles with this wire in the design: " + designWireInTileUsage.get(wire).size());
				else System.out.println(" No Occurence #Tiles with this wire in the design: ");
				if(antennaWireUsage.get(wire).size() > 0){
					System.out.print("  Antenna in " + antennaWireUsage.get(wire).size() + " Tiles: ");
					for(Tile tile : antennaWireUsage.get(wire)) System.out.print(tile.getName() + ", ");
					System.out.println("");
				}
			}
		}
	}

	/**
	 * Print an error summary for antennas which puts the forward and backwards errors together and list it in a well structured way
	 * @param error1 Backward error map
	 * @param error2 Forward error map
	 * @return A HashMap which stores for each wire (key) the error tiles in which it occurs
	 */
	private static HashMap<Integer, ArrayList<Tile>> printErrorSummary(Map<Net, List<PIP>> error1, Map<Net, List<PIP>> error2, Design design) {
		HashMap<Net, HashMap<Pin, Boolean>> errorPinsNew = new HashMap<Net, HashMap<Pin, Boolean>>();
		//Wire number, Tile
		HashMap<Integer, ArrayList<Tile>> antennaWireUsage = new HashMap<Integer, ArrayList<Tile>>();
		
		for(Iterator<Net> iter = errorPins.keySet().iterator(); iter.hasNext(); ){
			Net net = iter.next();
			for(Pin pin : errorPins.get(net).keySet()){
				if((errorPins.get(net).get(pin) == true && !error1.containsKey(net)) || (errorPins.get(net).get(pin) == false && !error2.containsKey(net))) iter.remove();
			}
		}
		
		System.out.println("================= Error - Path Summary =================");
		
		for(Net net : error1.keySet()){	//backward search	
			if(error2.containsKey(net) || errorPins.containsKey(net)){
				System.out.println("");
				System.out.println("Current Net: " + net.getName());
				if(errorPins.containsKey(net)){
					for(Pin pin : errorPins.get(net).keySet()){
						if(errorPins.get(net).get(pin) == true){
							System.out.println("   Pin error (forward): " + pin.getName() + " " + WireEnumerator.getInstance(design.getFamilyType()).getWireName(design.getDevice().getPrimitiveExternalPin(pin)));
							errorPinsNew.put(net, errorPins.get(net));
							errorPins.remove(net);
						}
					}
				}
				if(error2.containsKey(net)){
					System.out.println(" backward search");
					for(PIP pip : error1.get(net)) {
						System.out.println(" " + pip);
						if(!antennaWireUsage.containsKey(pip.getStartWire())) antennaWireUsage.put(pip.getStartWire(), new ArrayList<Tile>());
						antennaWireUsage.get(pip.getStartWire()).add(pip.getTile());
					}
					System.out.println(" forward search");
					for(PIP pip : error2.get(net)) {
						System.out.println(" " + pip);
						if(!antennaWireUsage.containsKey(pip.getEndWire())) antennaWireUsage.put(pip.getEndWire(), new ArrayList<Tile>());
						antennaWireUsage.get(pip.getEndWire()).add(pip.getTile());
					}
					error2.remove(net);
					if(errorPins.containsKey(net)){
						for(Pin pin : errorPins.get(net).keySet()){
							if(errorPins.get(net).get(pin) == false) {
								System.out.println("   Pin error (backward): " + pin.getName() + " " + WireEnumerator.getInstance(design.getFamilyType()).getWireName(design.getDevice().getPrimitiveExternalPin(pin)));
								errorPinsNew.put(net, errorPins.get(net));
								errorPins.remove(net);
							}
						}
					}
				}
				
				//Write unused PIPs
				writeUnusedPIPs(net);
			}
		}
		for(Net net : error2.keySet()){
			System.out.println("");
			System.out.println("Current Net: " + net.getName());
			System.out.println(" forward search");
			for(PIP pip : error2.get(net)) {
				System.out.println(" " + pip);
				if(!antennaWireUsage.containsKey(pip.getEndWire())) antennaWireUsage.put(pip.getEndWire(), new ArrayList<Tile>());
				antennaWireUsage.get(pip.getEndWire()).add(pip.getTile());
			}
			if(errorPins.containsKey(net)){
				for(Pin pin : errorPins.get(net).keySet()){
					if(errorPins.get(net).get(pin) == false) {
						System.out.println("   Pin error (backward): " + pin.getName() + " " + WireEnumerator.getInstance(design.getFamilyType()).getWireName(design.getDevice().getPrimitiveExternalPin(pin)));
						errorPinsNew.put(net, errorPins.get(net));
						errorPins.remove(net);
					}
				}
			}
			//Write unused PIPs
			writeUnusedPIPs(net);
		}
		
		
		for(Net net : errorPins.keySet()){
			System.out.println("");
			System.out.println("Current Net: " + net.getName());
			for(Pin pin : errorPins.get(net).keySet()){
				if(errorPins.get(net).get(pin) == false) System.out.println(" Pin error (backward): " + pin.getName() + " " + WireEnumerator.getInstance(design.getFamilyType()).getWireName(design.getDevice().getPrimitiveExternalPin(pin)));
				else System.out.println(" Pin error (forward): " + pin.getName());
			}
			//Write unused PIPs
			writeUnusedPIPs(net);
		}
		errorPins.putAll(errorPinsNew);
		
		return antennaWireUsage;
		
	}
	
	/**
	 * Write unused pips for a given net out to the terminal
	 * @param net that will be check and the output is generated
	 */
	private static void writeUnusedPIPs(Net net){
		//Write unused PIPs
		if(unusedPIPMap.get(net).containsKey(true) && unusedPIPMap.get(net).containsKey(false)){	//use forward unused pips
			ArrayList<PIP> unusedPIPs = unusedPIPMap.get(net).get(true);
			//boolean changed = unusedPIPs.retainAll(unusedPIPMap.get(net).get(false));
			unusedPIPs.retainAll(unusedPIPMap.get(net).get(false));
			System.out.println(" #unused PIPs: " + unusedPIPs.size());	// + " list changed " + changed);
			for(PIP pip1 : unusedPIPs){
				System.out.println("  " + pip1);
			}
		}
		else System.out.println(" -> ERROR: Please move forward and backward through the design and save unused PIPs in the unusedPIPMAP");
	}

	/**
	 * Search a path in the defined design from the sourceWires to the sink wires with respect of invalid connections from pins and antennas
	 * @param design user defined design file
	 * @param netName name of the current net
	 * @param net the current viewed net
	 * @param pipArray all possible pips from a net
	 * @param sourceWire wires which should be the source of the path
	 * @param sinks wires which are the sinks and are used to start the search
	 * @param forward define if the search process is forward or backward through the design
	 * @return return the last possible pips before an error occure
	 */
	private static List<PIP> searchPath(Design design, String netName, Net net, List<PIP> pipArray, ArrayList<Integer> sourceWire, ArrayList<Pin> sinks, boolean forward){
		boolean enableWarnings = false;
		HashMap<Tile, HashMap<Integer, ArrayList<PIP>>> wire2PIP = new HashMap<Tile, HashMap<Integer, ArrayList<PIP>>>();
		ArrayList<PIP> errorPIPs = new ArrayList<PIP>();
		HashMap<PIP, Integer> pipUsage = new HashMap<PIP, Integer>();
		Device device = design.getDevice();
		
		int error = 0;
		//Store all end wires to each pip and set the initial usage to 0
		for(PIP pip : pipArray) {
			if(!wire2PIP.containsKey(pip.getTile())) wire2PIP.put(pip.getTile(), new HashMap<Integer, ArrayList<PIP>>());
			if(!wire2PIP.get(pip.getTile()).containsKey(getFurtherConnectWire(pip, !forward))) {
				wire2PIP.get(pip.getTile()).put(getFurtherConnectWire(pip, !forward), new ArrayList<PIP>());
			}
			if(!wire2PIP.get(pip.getTile()).containsKey(getFurtherConnectWire(pip, forward))) {
				if(pip.getDirection().isBidirectional) wire2PIP.get(pip.getTile()).put(getFurtherConnectWire(pip, forward), new ArrayList<PIP>());
			}
			wire2PIP.get(pip.getTile()).get(getFurtherConnectWire(pip, !forward)).add(pip);
			if(pip.getDirection().isBidirectional) {
				if(debug) System.out.println(" Bidirectional pip: " + pip);
				wire2PIP.get(pip.getTile()).get(getFurtherConnectWire(pip, forward)).add(pip);
			}
			
			pipUsage.put(pip, 0);
		}
		
		/*System.out.println(" Wire2PIP map");
		for(Tile ti : wire2PIP.keySet()){
			for( Integer wireInt : wire2PIP.get(ti).keySet()) {
				for(PIP pip2 : wire2PIP.get(ti).get(wireInt)){
					System.out.println(pip2 + " for wire: " + WireEnumerator.getInstance(design.getFamilyType()).getWireName(wireInt) + " at tile: " + ti);
				}
			}	
		}
		System.out.println(" Wire2PIP map finish");*/
		
		for(Pin sinkPin : sinks) {
			ArrayList<PIP> furtherConnections = new ArrayList<PIP>();
			int sink = device.getPrimitiveExternalPin(sinkPin);
			
			//Sonderfall Sink pin nicht im gleichen Tile wie ein PIP
			WireConnection[] wireCon_sink = sinkPin.getTile().getWireConnections(sink);
			if(debug) System.out.println(" sink pin tile: " + sinkPin.getTile() + " sinkpin: " + sinkPin);
			if(wireCon_sink != null) {	
				//check if there is an entry for the sink tile and the sinkwire in the wire2pip map, otherwise search for hardwires because no direct connection to a pip can be found
				if(wire2PIP.get(sinkPin.getTile()) == null || !wire2PIP.get(sinkPin.getTile()).containsKey(sink)){
					
					for(int i = 0 ; i < wireCon_sink.length; i++){
						Tile wireConTile = wireCon_sink[i].getTile(sinkPin.getTile());	//get the tile corresponding to the wire
						if(wire2PIP.containsKey(wireConTile)) {
							if(wire2PIP.get(wireConTile).containsKey(wireCon_sink[i].getWire())){
								ArrayList<PIP> pipList = wire2PIP.get(wireConTile).get(wireCon_sink[i].getWire());

								furtherConnections.addAll(pipList);
							}
						}
					}
				}
				else {
					furtherConnections.addAll(wire2PIP.get(sinkPin.getTile()).get(sink));
				}
			}
			else {
				if(debug) System.out.println(" Wire connection from sink pin is NULL");
			}
			if(furtherConnections.size() == 0){
				if(!minimalOutput) {
					System.out.println("");
					System.out.println("---===== ERROR - no hardwire found fom sink =====---");
					System.out.println("Current NET: " + netName);
					if(forward){
						System.out.print("Source: " + sinkPin.getName());
						System.out.print(" Sink: ");
						for(int sWire : sourceWire) System.out.print(WireEnumerator.getInstance(design.getFamilyType()).getWireName(sWire) + ", ");	
						System.out.println("");
					}
					else{
						System.out.print("Source: ");
						for(int sWire : sourceWire) System.out.print(WireEnumerator.getInstance(design.getFamilyType()).getWireName(sWire) + ", ");
						if(sinkPin.getTile().getWireConnections(sink) == null) System.out.println(" Sink: " + sinkPin.getName() + " #possible wireConnections: " + sinkPin.getTile().getWireConnections(sink));
						else System.out.println(" Sink: " + sinkPin.getName() + " #possible wireConnections: " + sinkPin.getTile().getWireConnections(sink).length);
					}
				}
				numberOfErrors++;
				HashMap<Pin, Boolean> pinErrorMap = new HashMap<Pin, Boolean>();
				pinErrorMap.put(sinkPin, forward);
				errorPins.put(net, pinErrorMap);
				error = 2;
				break;	//Go to new sink
			}
			
			
			ArrayList<PIP> newHardwiredPIPs = new ArrayList<PIP>();
			ArrayList<PIP> viewedHardwiredPIPs = new ArrayList<PIP>();
			ArrayList<Integer> unusedSourcePins = new ArrayList<Integer>();
			HashMap<PIP, Integer> bidirectionalWireViewed = new HashMap<PIP, Integer>();
			unusedSourcePins.addAll(sourceWire);
			
			do{	//check further connections from a certain point
				if(debug) System.out.println("NEWHARDWIREDPIPs");
				if(debug) for(PIP outputPIP : newHardwiredPIPs) System.out.println(outputPIP);
				
				for(PIP addNewPip : newHardwiredPIPs){
					if(!viewedHardwiredPIPs.contains(addNewPip) && !furtherConnections.contains(addNewPip)){
						furtherConnections.add(addNewPip);
						if(debug) System.out.println(" ADDED NEW PIP: " + addNewPip);
					}
				}
				
				newHardwiredPIPs = new ArrayList<PIP>();
				for(Iterator<PIP> iter = furtherConnections.iterator() ; iter.hasNext(); ){
					PIP pip = iter.next();
					viewedHardwiredPIPs.add(pip);
					boolean bidirectionalRun = false;
					int wire;
					if(bidirectionalWireViewed.containsKey(pip)){
						if(debug) System.out.println("   biPIP use other wire: " +WireEnumerator.getInstance(design.getFamilyType()).getWireName(getFurtherConnectWire(pip, forward)) + " saved: " + WireEnumerator.getInstance(design.getFamilyType()).getWireName(bidirectionalWireViewed.get(pip)) + " in pip " + pip);
						if(bidirectionalWireViewed.get(pip) == getFurtherConnectWire(pip, forward)) {
							wire = getFurtherConnectWire(pip, !forward);
							bidirectionalRun = true;
						}
						else wire = getFurtherConnectWire(pip, forward);
					} else {
						wire = getFurtherConnectWire(pip, forward);
					}
					pipUsage.put(pip, (pipUsage.get(pip) + 1));
					
					ArrayList<PIP> pipPath = new ArrayList<PIP>();
					HashMap<PIP, Boolean> bothWiresUsed = new HashMap<PIP, Boolean>();
					
					if(debug) System.out.println(" --- new pipPath + pip: " + pip);
					boolean foundConnection = true;
					if(debug) if(!(!sourceWire.contains(wire) && foundConnection)) System.out.println(" ! Pip path not further investigated !");
					ArrayList<PIP> furtherCons = new ArrayList<PIP>();
					while(!sourceWire.contains(wire) && foundConnection){
						if(pip.getDirection().isBidirectional && !bothWiresUsed.containsKey(pip)) bothWiresUsed.put(pip, false);
						if(pip.getDirection().isBidirectional && bothWiresUsed.get(pip)){
							
						}
						else furtherCons = new ArrayList<PIP>();
						
						if(debug) System.out.println("Current Wire: " + WireEnumerator.getInstance(design.getFamilyType()).getWireName(wire));
						if(debug) System.out.println(" pip: " + pip);
						if(debug) System.out.println(" already used pip at path: " + pipPath.contains(pip));
						
						//next wire is directly in the same tile
						if(wire2PIP.get(pip.getTile()).containsKey(wire)){
							if(debug) System.out.println(" # of inter-tile wires: " + wire2PIP.get(pip.getTile()).get(wire).size());
							//Check for splittpoints
								
							for(PIP newPip : wire2PIP.get(pip.getTile()).get(wire)){
								if(newPip.equals(pip)) continue;
								furtherCons.add(newPip);
							}
							
							if(debug) System.out.println(" frutherInterTileCons found: " + furtherCons.size());
						}
						else {
							if(debug) System.out.println(" -> no frutherInterTileCons found");
						}
						
						
						
						//Search next wire above hardwires						
						if(pip.getDirection().isBidirectional) {
							if(bothWiresUsed.containsKey(pip)){
								if(bothWiresUsed.get(pip)) pipPath.add(pip);
							}
						}
						else pipPath.add(pip);
						
						HashMap<PIP, Integer> furtherHardwires = new HashMap<PIP, Integer>();
						furtherHardwires = searchFurtherHardwires(design, wire2PIP, pip, forward, bidirectionalRun);
						for(PIP hardwirePIP : furtherHardwires.keySet()) {
							if(!furtherCons.contains(hardwirePIP)) furtherCons.add(hardwirePIP);
							if(hardwirePIP.getDirection().isBidirectional) bidirectionalWireViewed.put(hardwirePIP, furtherHardwires.get(hardwirePIP));
						}
						
						bidirectionalRun = false;
						
						
						
						
						if(debug) System.out.println(" -> found further wires: " + furtherCons.size());
						if(debug) for(PIP outputPIP : furtherCons) System.out.println(outputPIP);
					

						if(furtherCons.size() > 0){

							ArrayList<PIP> removeList = new ArrayList<PIP>();
							for(PIP newPip : furtherCons){
								if(pipPath.contains(newPip)) removeList.add(newPip);
							}
							
							furtherCons.removeAll(removeList);
							
							if(debug) System.out.println(" -> found further wires after remove: " + furtherCons.size());
							if(debug) for(PIP outputPIP : furtherCons) System.out.println(outputPIP);
							
							if(pip.getDirection().isBidirectional && !bothWiresUsed.get(pip)) {
								if(debug) System.out.println(" bidirektional run");
								
								if(!bothWiresUsed.get(pip) && (wire == getFurtherConnectWire(pip, forward))) {
									wire = getFurtherConnectWire(pip, !forward);
									bidirectionalRun = true;
								}
								else wire = getFurtherConnectWire(pip, forward);
								
								pipPath.add(pip);
								bothWiresUsed.put(pip, true);
							}
							else {
								if(debug) System.out.println(" normal run");
								boolean foundSomePip = false;
								for(PIP newPip : furtherCons){
									if(!pipPath.contains(newPip)) {
										pip = newPip;	//TODO eventuell einfügen, wenn es in der furtherconnections liste ist das er dann erst was anderes nimmt wenn es geht (optimierung)
										if(pip.getDirection().isBidirectional) {
											if(bothWiresUsed.containsKey(pip)){
												if(bothWiresUsed.get(pip)) pipPath.add(pip);
											}
										}
										else pipPath.add(pip);
										foundSomePip = true;
										
										if(bothWiresUsed.containsKey(newPip)) pipPath.add(newPip);
										if(newPip.getDirection().isBidirectional) bothWiresUsed.put(newPip, false);
										else bothWiresUsed.put(newPip, true);
										break;
									}
								}
								
								if(foundSomePip){
									furtherCons.remove(pip);
									newHardwiredPIPs.addAll(furtherCons);
									for(PIP newPIP : furtherCons) if(newPIP.getDirection().isBidirectional && !bidirectionalWireViewed.containsKey(newPIP)) bidirectionalWireViewed.put(newPIP, wire);
									//First try to search these end wire of the pip where we dont come from if we come from the isBidirectional end, then use the other
									if(!bothWiresUsed.get(pip) && (wire == getFurtherConnectWire(pip, forward))) {
										wire = getFurtherConnectWire(pip, !forward);
										bidirectionalRun = true;
									}
									else wire = getFurtherConnectWire(pip, forward);
																
									pipUsage.put(pip, (pipUsage.get(pip) + 1));
								}
								else {
									if(pip.getDirection().isBidirectional && !bothWiresUsed.containsKey(pip)) bothWiresUsed.put(pip, false);
									
									if(pip.getDirection().isBidirectional && !bothWiresUsed.get(pip)) {
										if(debug) System.out.println(" another bidirektional run");
										foundConnection = true;
										
										if(!bothWiresUsed.get(pip) && (wire == getFurtherConnectWire(pip, forward))) {
											wire = getFurtherConnectWire(pip, !forward);
										}
										else wire = getFurtherConnectWire(pip, forward);
										bidirectionalRun = true;
										bothWiresUsed.put(pip, true);
									}
									else {
										if(debug) System.out.println(" found with hardwireToPinWire 1");
										Integer connectionToPinWire = hardwireToPinWire(design, pip, sourceWire, wire);
										if(connectionToPinWire != null) {
											
											foundConnection = true;
											wire = connectionToPinWire;
										}
										else foundConnection = false;
									}
								}
							}
						}
						else {
							//if no connection was found on a isBidirectional pip, try the other end of the pip
							if(debug) System.out.println("Bidirektional: " + pip.getDirection() + " both used: " + bothWiresUsed);
							if(pip.getDirection().isBidirectional && !bothWiresUsed.containsKey(pip)) bothWiresUsed.put(pip, false);
							
							if(pip.getDirection().isBidirectional && !bothWiresUsed.get(pip)) {
								if(debug) System.out.println(" bidirektional run");
								foundConnection = true;
								
								if(!bothWiresUsed.get(pip) && (wire == getFurtherConnectWire(pip, forward))) {
									wire = getFurtherConnectWire(pip, !forward);
								}
								else wire = getFurtherConnectWire(pip, forward);
								bidirectionalRun = true;
								bothWiresUsed.put(pip, true);
							}
							else {
								if(debug) System.out.println(" found with hardwireToPinWire 2");
								Integer connectionToPinWire = hardwireToPinWire(design, pip, sourceWire, wire);
								if(connectionToPinWire != null) {
									
									foundConnection = true;
									wire = connectionToPinWire;
								}
								else foundConnection = false;
							}
						}
					}
					
					if(sourceWire.contains(wire)) {
						if(unusedSourcePins.contains(wire)) {
							unusedSourcePins.remove((Integer) wire);
						}
					}
					
					if(!foundConnection) {
						numberOfErrors++;
						if(!minimalOutput) {
							System.out.println("");
							System.out.println("---==== ERROR - no further connection found ====---");
							System.out.println("Current NET: " + netName);
							if(forward){
								System.out.print("Source: " + sinkPin.getName());
								System.out.print(" Sink: ");
								for(int sWire : sourceWire) System.out.print(WireEnumerator.getInstance(design.getFamilyType()).getWireName(sWire) + ", ");	
								System.out.println("");
							}
							else{
								System.out.print("Source: ");
								for(int sWire : sourceWire) System.out.print(WireEnumerator.getInstance(design.getFamilyType()).getWireName(sWire) + ", ");		
								System.out.println(" Sink: " + sinkPin.getName());
							}
							System.out.println("PIP: " + pip);
						}
						errorPIPs.add(pip);
						error = 1;
						/*if(!minimalOutput) {
							System.out.println("Current PIPs:");
							for(Tile ti : wire2PIP.keySet()){
								for( Integer wireInt : wire2PIP.get(ti).keySet()) System.out.println(" end: " + WireEnumerator.getInstance(design.getFamilyType()).getWireName(wireInt) + " tile " + ti.getName());
							}
						}*/
					}
					else {
						if(debug) {
							System.out.println("End: " + WireEnumerator.getInstance(design.getFamilyType()).getWireName(wire) + 
									" at PIP: start " + pip.getStartWireName(design.getWireEnumerator()) + " end " + pip.getEndWireName(design.getWireEnumerator()));
						}
					}
					iter.remove();
				}
			} while(newHardwiredPIPs.size() > 0);
			
			
			if(!minimalOutput) { 
				if(unusedSourcePins.size() > 0) {
					System.out.println("Current NET: " + netName);
					System.out.println("===> UNUSED PINS: " + unusedSourcePins.size());
					for(int sWire : unusedSourcePins) System.out.print(WireEnumerator.getInstance(design.getFamilyType()).getWireName(sWire) + ", ");	
					System.out.println("");
				}
			}
			
		}		
		if(error < 2 && enableWarnings) {
			ArrayList<PIP> unusedPIPs = returnUnusedPIPs(pipUsage);
			if(unusedPIPs.size() != 0) {
				System.out.println("");
				System.out.println("---==== Warning - not all PIPs used ====---");
				System.out.println("Current NET: " + netName);
				System.out.println(" Unused PIPs: ");
				for(PIP pip1 : unusedPIPs){
					System.out.println("  " + pip1);
				}
			}
		}
		if(!unusedPIPMap.containsKey(net)) unusedPIPMap.put(net, new HashMap<Boolean, ArrayList<PIP>>());	
		unusedPIPMap.get(net).put(forward, returnUnusedPIPs(pipUsage));
		
		return errorPIPs;
	}
	
	/**
	 * Check if there is a hardwire directly to a pinwire
	 * @param design user defined design
	 * @param pip start to find some hard wired connection from wire
	 * @param sourceWire destination which needs to be reached
	 * @param wire pip start/end to search from
	 */
	private static Integer hardwireToPinWire(Design design, PIP pip, ArrayList<Integer> sourceWire, int wire) {
		Integer foundWire = null;
		
		WireConnection[] wireCon = pip.getTile().getWireConnections(wire);
		if(wireCon != null){
			if(debug) System.out.println(" wire con length: " + wireCon.length);
			for(int i = 0; i < wireCon.length; i++){
				if(debug) System.out.println(WireEnumerator.getInstance(design.getFamilyType()).getWireName(wireCon[i].getWire()) + " searched wire: " + WireEnumerator.getInstance(design.getFamilyType()).getWireName(wire));
				if(sourceWire.contains(wireCon[i].getWire())) {
					foundWire = wireCon[i].getWire();
					break;	//TODO maybe several wires to a pin from the last pip?
				}
			}
		}
		
		return foundWire;
	}

	/**
	 * Return the next connection which is used to search further in the user defined design
	 * @param pip wire number outgoing from this pip
	 * @param forward select if the end or the start wire is returned
	 * @return return the start or the end wire number of this pip
	 */
	private static int getFurtherConnectWire(PIP pip, boolean forward){
		if(forward) return pip.getEndWire();
		else return pip.getStartWire();
	}
	
	/**
	 * Search if there are further wires from a given pip to a pip which is also used in the user design
	 * @param wire2pip Map which defines all possible PIPs which are in the current net
	 * @param frompip pip which is the source. Search from this PIP a hardwired connection to another pip
	 * @param forward define if the search should be forward or backward through the pips
	 * @return a ArrayList which includes all possible PIPs which can be reached with a hardwire from the frompip
	 */
	private static HashMap<PIP, Integer> searchFurtherHardwires(Design design, HashMap<Tile, HashMap<Integer, ArrayList<PIP>>> wire2pip, PIP frompip, boolean forward, boolean bidirectionalRun){
		HashMap<PIP, Integer> possibleFurtherCons = new HashMap<PIP, Integer>();
		int wire;
		if(bidirectionalRun) wire = getFurtherConnectWire(frompip, !forward);
		else wire = getFurtherConnectWire(frompip, forward);
		
		WireConnection[] wireCon = frompip.getTile().getWireConnections(wire);
		if(debug) System.out.println(" search further hardwire " + WireEnumerator.getInstance(design.getFamilyType()).getWireName(wire));
		
		if(wireCon != null){
			for(int i = 0 ; i < wireCon.length; i++){
				if(debug) System.out.println(" try wireCon: " + WireEnumerator.getInstance(design.getFamilyType()).getWireName(wireCon[i].getWire()) + " at tile " + frompip.getTile().getName());
				if(wireCon[i].isPIP()) continue;
				
				Tile wireConTile = wireCon[i].getTile(frompip.getTile());	//get the tile corresponding to the wire
				if(debug) System.out.println(" get from Tile: " + frompip.getTile() + " to new tile: " + wireConTile);
				if(wire2pip.containsKey(wireConTile)) {
					if(wire2pip.get(wireConTile).containsKey(wireCon[i].getWire())){
						
						for(PIP foundPip : wire2pip.get(wireConTile).get(wireCon[i].getWire())){
							possibleFurtherCons.put(foundPip, wireCon[i].getWire());
						}
					}
				}
			}
			if(possibleFurtherCons.containsKey(frompip)) possibleFurtherCons.remove(frompip);	//possible at isBidirectional pips (2 times in the wire2pip map)
			for(int i = 0; i< wireCon.length;i++){
				if(wireCon[i].isPIP()) continue;
				Tile wireConTile = wireCon[i].getTile(frompip.getTile());
				HashMap<Tile, ArrayList<Integer>> viewedWires = new HashMap<Tile, ArrayList<Integer>>();
				possibleFurtherCons.putAll(deepHardwireSearch(design, wire2pip, frompip, wireCon[i], forward, bidirectionalRun, wireConTile, viewedWires));
			}

		}
		else if(debug) System.out.println(" no Hardwires");
		if(possibleFurtherCons.containsKey(frompip)) possibleFurtherCons.remove(frompip);	//possible at isBidirectional pips (2 times in the wire2pip map)
		return possibleFurtherCons;
	}
	
	
	private static HashMap<PIP, Integer> deepHardwireSearch(Design design, HashMap<Tile, HashMap<Integer, ArrayList<PIP>>> wire2pip, PIP fromPip, WireConnection hardwire, boolean forward, boolean bidirectionalRun, Tile tile, HashMap<Tile, ArrayList<Integer>> viewedWires){
		HashMap<PIP, Integer> possibleFurtherCons = new HashMap<PIP, Integer>();
		if(debug) System.out.println(" deep start->");
		if(tile.getWireConnections(hardwire.getWire()) != null) {
			WireConnection[] wireCon = tile.getWireConnections(hardwire.getWire());
			if(debug) System.out.println("  deep: from: " + tile + " from Pip: " + fromPip);
			if(debug) System.out.println("  wire: " + WireEnumerator.getInstance(design.getFamilyType()).getWireName(hardwire.getWire()));
			
			if(!viewedWires.containsKey(tile)) viewedWires.put(tile, new ArrayList<Integer>());
			viewedWires.get(tile).add(hardwire.getWire());
			
			if(wireCon != null){
				if(debug) System.out.println("  #wirecons: " + wireCon.length);
				for(int i = 0 ; i < wireCon.length; i++){
					if(wireCon[i].isPIP()) continue;
					
					Tile wireConTile = wireCon[i].getTile(tile);	//get the tile corresponding to the wire					
					if(wire2pip.containsKey(wireConTile)) {
						if(wire2pip.get(wireConTile).containsKey(wireCon[i].getWire())){
							if(!wire2pip.get(wireConTile).get(wireCon[i].getWire()).equals(fromPip)){
								
								for(PIP foundPip : wire2pip.get(wireConTile).get(wireCon[i].getWire())){
									possibleFurtherCons.put(foundPip, wireCon[i].getWire());
								}
							}
						}
					}
				}
			
				if(possibleFurtherCons.containsKey(fromPip)) possibleFurtherCons.remove(fromPip);	//possible at isBidirectional pips (2 times in the wire2pip map)
				for(int i = 0; i< wireCon.length;i++){
					if(wireCon[i].isPIP()) continue;
					Tile wireConTile = wireCon[i].getTile(tile);
					if(debug) System.out.println("  ->deep: from: " + tile + " to: " + wireConTile);
					if(debug) System.out.println("  ->wire: " + WireEnumerator.getInstance(design.getFamilyType()).getWireName(wireCon[i].getWire()));
					if(viewedWires.containsKey(wireConTile)){	//prevent loops
						if(!viewedWires.get(wireConTile).contains(wireCon[i].getWire())){
							
							if(!viewedWires.containsKey(wireConTile)) viewedWires.put(wireConTile, new ArrayList<Integer>());
							viewedWires.get(wireConTile).add(wireCon[i].getWire());
							if(debug) System.out.println(wireConTile + " " + WireEnumerator.getInstance(design.getFamilyType()).getWireName(wireCon[i].getWire()));
							
							possibleFurtherCons.putAll(deepHardwireSearch(design, wire2pip, fromPip, wireCon[i], forward, bidirectionalRun, wireConTile, viewedWires));
						}
					}
				}
			}
		}
		if(possibleFurtherCons.containsKey(fromPip)) possibleFurtherCons.remove(fromPip); //possible at isBidirectional pips (2 times in the wire2pip map)
		return possibleFurtherCons;
	}

	/**
	 * Go through all Nets and search each net from sink to source if there are antennas where no further movement is possible
	 * @param design the user loaded xdl design file
	 * @return a error Map which stores for each net the last possible PIP which can be reached
	 */
	private static Map<Net, List<PIP>> seachFromSinkToSource(Design design) {
		numberOfErrors = 0;
		
		Device device = design.getDevice();
		Map<Net, List<PIP>> errorMap = new HashMap<Net, List<PIP>>();
		for(Net net : design.getNets()){
			if(net.getPins().size() == 0) {
				continue;
			}

			if(debug) System.out.println("");
			if(debug) System.out.println("NET: " + net.getName());
			if(debug) System.out.println("Source: " + net.getSource().getName() + " pin: " + WireEnumerator.getInstance(design.getFamilyType()).getWireName(device.getPrimitiveExternalPin(net.getSource())));
		
			
			//Search sink pins
			if((net.getSource() == null) || (net.getPins() == null)) {
				if(!incompleteNet.contains(net)) incompleteNet.add(net);
			}
			else {
				ArrayList<Pin> sinks = new ArrayList<Pin>();
				
				sinks.addAll(net.getPins());
	
				sinks.remove(net.getSource());
				
					ArrayList<Integer> sourcePin = new ArrayList<Integer>();
				
				
					sourcePin.add(device.getPrimitiveExternalPin(net.getSource()));
				
					List<PIP> errorList = searchPath(design, net.getName(), net, net.getPIPs(), sourcePin, sinks, false);
					if(errorList.size() > 0) errorMap.put(net, errorList);
			}
			
		}
		if(debug) System.out.println("");
		if(debug) System.out.println("#Errors: " + numberOfErrors);
		return errorMap;
	}
	
	/**
	 * Go through all Nets and search each net from source to sink if there are antennas where no further movement is possible
	 * @param design the user loaded xdl design file
	 * @return a error Map which stores for each net the last possible PIP which can be reached
	 */
	private static Map<Net, List<PIP>> seachFromSourceToSink(Design design) {
		numberOfErrors = 0;
		Map<Net, List<PIP>> errorMap = new HashMap<>();
		Device device = design.getDevice();
		
		for(Net net : design.getNets()){
			if(net.getPins().size() == 0) {
				continue;
			}
	
			if(debug) System.out.println("");
			if(debug) System.out.println("NET: " + net.getName());
			if(debug) System.out.println("Source: " + net.getSource().getName() + " pin: " + WireEnumerator.getInstance(design.getFamilyType()).getWireName(device.getPrimitiveExternalPin(net.getSource())));
			 

			//Search sink pins
			if((net.getSource() == null) || (net.getPins() == null)) {
				if(!incompleteNet.contains(net)) incompleteNet.add(net);
			}
			else {
				ArrayList<Pin> sinks = new ArrayList<Pin>();
				sinks.add(net.getSource());
				
				
				ArrayList<Integer> sourcePin = new ArrayList<Integer>();
				for(Pin pins : net.getPins()) {
					sourcePin.add(device.getPrimitiveExternalPin(pins));
					if(debug) System.out.println(" source pin: " + pins);
				}
				sourcePin.remove(device.getPrimitiveExternalPin(net.getSource()));
				
					if(debug) for(Integer wire : sourcePin) System.out.println(WireEnumerator.getInstance(design.getFamilyType()).getWireName(wire));
						
					List<PIP> errorList = searchPath(design, net.getName(), net, net.getPIPs(), sourcePin, sinks, true);
					if(errorList.size() > 0) errorMap.put(net, errorList);
			
			}
			
		}
		if(debug) System.out.println("");
		if(debug) System.out.println("#Errors: " + numberOfErrors);
		return errorMap;
	}
	
	/**
	 * Method to return all PIPs which are not used an the integer value is zero
	 * @param pipUsage Map with a number which stores the usage of a PIP (Key)
	 * @return a ArrayList with unused PIPs
	 */
	private static ArrayList<PIP> returnUnusedPIPs(HashMap<PIP, Integer> pipUsage) {
		ArrayList<PIP> unusedPIPs = new ArrayList<PIP>();
		for(PIP pip : pipUsage.keySet()){
			if(pipUsage.get(pip) == 0) unusedPIPs.add(pip);
		}
		return unusedPIPs;
	}

	/**
	 * Verify the PIPs from the design .xdl with the loaded device database.
	 * Check if all used PIPs are available in the device
	 * @param design used design .xdl file
	 */
	private static void verifyPIPs(Design design){
		System.out.println("===================================================");
		System.out.println("=    Verify PIPs from Design with the Database    =");
		System.out.println("===================================================");
		
		for(Net net : design.getNets()){
			for(PIP pip : net.getPIPs()){
				Tile tile = pip.getTile();
				boolean pipContainsInDevice = verifyDesignPIPWithDevice(tile, pip);

				if(!pipContainsInDevice) {
					System.out.println("---===== ERROR - PIP in Database not Found =====---");
					System.out.println("PIP: tile " +pip.getTile().getName() +  " start " + pip.getStartWireName(design.getWireEnumerator()) + 
						" end " + pip.getEndWireName(design.getWireEnumerator())); // + " available in device file: " + pipContainsInDevice);
				}
			}
		}
	}

	/**
	 * Create the Tile to PIPs map. For all used Tiles the corrospondend pips are saved as value
	 * @param tile from the design which is used with the pip 
	 * @param pip from the design
	 * @return if the pip is available in the device database
	 */
	private static boolean verifyDesignPIPWithDevice(Tile tile, PIP pip) {
		if(!tilePIPMap.containsKey(tile)) {
			tilePIPMap.put(tile, tile.getPIPs());
		}
		return tilePIPMap.get(tile).contains(pip);
	}

}
