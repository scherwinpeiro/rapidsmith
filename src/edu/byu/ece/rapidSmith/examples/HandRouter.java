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
package edu.byu.ece.rapidSmith.examples;

import edu.byu.ece.rapidSmith.design.Design;
import edu.byu.ece.rapidSmith.design.Net;
import edu.byu.ece.rapidSmith.design.PIP;
import edu.byu.ece.rapidSmith.design.Pin;
import edu.byu.ece.rapidSmith.device.Device;
import edu.byu.ece.rapidSmith.device.WireConnection;
import edu.byu.ece.rapidSmith.device.WireEnumerator;
import edu.byu.ece.rapidSmith.router.Node;
import edu.byu.ece.rapidSmith.util.FileConverter;
import edu.byu.ece.rapidSmith.util.MessageGenerator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.util.ArrayList;

/**
 * This class is an example of how to do some simple routing by hand. It
 * is more used a demonstration of how to do real routing with RapidSmith,
 * rather than being a useful way to route.
 * @author Chris Lavin
 * Created on: Jul 15, 2010
 */
public class HandRouter{

	/** This is the current device of the design that was loaded */
	private Device dev;
	/** This is the corresponding wire enumerator for the device */
	private WireEnumerator we;
	/** This is the current design we are loading */
	private Design design;
	/** Standard Input */
	private BufferedReader br;
	
	/** 
	 * Initialize the HandRouter with the design
	 * @param inputFileName The input file to load
	 */
	public HandRouter(String inputFileName){
		design = new Design();
		
		// Check if we are loading an NCD file, convert accordingly
		if(inputFileName.toLowerCase().endsWith("ncd")){
			inputFileName = FileConverter.convertNCD2XDL(inputFileName);
		}
		
		// Load the design 
		design.loadXDLFile(Paths.get(inputFileName));
		dev = design.getDevice();
		we = design.getWireEnumerator();
		
		// Delete the temporary XDL file, if needed
		//FileTools.deleteFile(inputFileName); 
	}
	
	/**
	 * Prompt the user with options to perform routing of a particular net.
	 * @param netName Name of the net to route
	 */
	private void HandRoute(String netName){
		Net net = design.getNet(netName);
		int choice;
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

		ArrayList<PIP> path =  new ArrayList<PIP>();
		ArrayList<PIP> pipList = new ArrayList<PIP>();

		Node currNode = null;
		WireConnection currWire;
		WireConnection[] wiresList = null;
		ArrayList<Node> choices;
		
		// This keeps track of all the possible starting points (or sources) that
		// we can use to route this net.
		ArrayList<Node> sources = new ArrayList<Node>();
		
		// Add the original source from the net
		sources.add(new Node(net.getSourceTile(), // Add the tile of the source
			dev.getPrimitiveExternalPin(net.getSource()), // wire on the source
			null, // This is used for retracing the route once it is completed 
			0)); // Number of switches needed to reach this point in the route
		
		// In this loop we'll route each sink pin of the net separately 
		for(Pin sinkPin : net.getPins()){
			if(sinkPin.isOutPin()) continue; // Don't try to route the source to the source 
			boolean start = true;
			boolean finishedRoute = false;
			
			// Here is where we create the current sink that we intend to target in this 
			//routing iteration.  
			Node sink = new Node(sinkPin.getTile(), // A node is a specific tile and wire pair
							dev.getPrimitiveExternalPin(sinkPin), // we need the external pin
																  // name of the primitive pin
																  // (ex: F1_PINWIRE0 vs. F1)
							null,								  // This is a parent node
							0);									  // This value is used to keep track
																  // length in hops of the route.
			
			MessageGenerator.printHeader("Current Sink: " + sink.getTile() +
					" " + we.getWireName(sink.getWire()));

			// Almost all routes must pass through a particular switch matrix and wire to arrive
			// at the particular sink.  Here we obtain that information to help us target the routing.
			Node switchMatrixSink = sink.getSwitchBoxSink(dev);
			System.out.println("** Sink must pass through switch matrix: " + switchMatrixSink.getTile() + 
					", wire: " + we.getWireName(switchMatrixSink.getWire())+ " **");
			
			while(!finishedRoute){
				// Here we prompt the user to choose a source to start the route from.  If this
				// is the first tile we are routing in this net there will only be one choice.
				if(start){
					start = false;
					System.out.println("Sources:");
					for(int i=0; i < sources.size(); i++){
						Node src = sources.get(i);
						System.out.println("  " + i+". " + src.getTile() + " " + we.getWireName(src.getWire()));
					}
					System.out.print("Choose a source from the list above: ");
					try {
						choice = Integer.parseInt(br.readLine());
					} catch (Exception e){
						System.out.println("Error, could not get choice, defaulting to 0");
						choice = 0;
					}

					// Once we get the user's choice, we can determine what wires the source
					// can connect to by calling Tile.getWireConnections(int wire)
					currNode = sources.get(choice);
					wiresList = currNode.getTile().getWireConnections(currNode.getWire());
					if(wiresList == null || wiresList.length == 0){
						// We'll have to choose something else, this source had no other connections.
						System.out.println("Wire had no connections");
						continue;
					}
				}
				
				// Print out some information about the sink we are targeting
				if(sink.getTile().getSinks().get(sink.getWire()).switchMatrixSinkWire == -1){
					System.out.println("\n\nSINK: "
						+ sink.getTile().getName()
						+ " "
						+ we.getWireName(sink.getWire())
						+ " "
						+ net.getName());				
				}
				else{
					System.out.println("\n\nSINK: "
						+ sink.getTile().getName()
						+ " "
						+ we.getWireName(sink.getWire())
						+ " "
						+ net.getName()
						+ " thru("
						+ switchMatrixSink.getTile() + " "
						+ we.getWireName(sink.getTile().getSinks().get(sink.getWire()).switchMatrixSinkWire) + ")");
				}
				
				// Print out a part of the corresponding PIP that we have chosen
				System.out.println("  pip " + currNode.getTile().getName() + " "
						+ we.getWireName(currNode.getWire()) + " -> ");
				
				// Check if we have reached the sink node
				if (sink.getTile().equals(currNode.getTile())
						&& sink.getWire() == currNode.getWire()){
					System.out.println("You completed the route!");
					// If we have, let's print out all the PIPs we used
					for (PIP pip : path){
						System.out.print(pip.toString(we));
						pipList.add(pip);
						finishedRoute = true;
					}
				}
				if(!finishedRoute){
					// We didn't find the sink yet, let's print out the set of 
					// choices we can follow given our current wire
					choices = new ArrayList<Node>();
					for (int i = 0; i < wiresList.length; i++) {
						currWire = wiresList[i];
						choices.add(new Node(currWire.getTile(currNode.getTile()),
								currWire.getWire(), currNode, currNode.getLevel() + 1));

						System.out.println("    " + i + ". "
								+ currWire.getTile(currNode.getTile()).getName()
								+ " " + we.getWireName(currWire.getWire()) + " "
								+ choices.get(i).getCost() + " " + choices.get(i).getLevel());
					}
					System.out.print("\nChoose a route (s to start over): ");
					try {
						String cmd = br.readLine();
						if (cmd.equals("s")) {
							start = true;
							continue;
						}
						choice = Integer.parseInt(cmd);
					} catch (IOException e){
						System.out.println("Error reading response, try again.");
						continue;
					}
					if(wiresList[choice].isPIP()){
						path.add(new PIP(currNode.getTile(), currNode.getWire(), wiresList[choice].getWire()));
					}
					
					currNode = choices.get(choice);
					wiresList = currNode.getTile().getWireConnections(currNode.getWire());

					System.out.println("PIPs so far: ");
					for (PIP p : path){
						System.out.print("  " + p.toString(we));
					}	
				}
			}				
		}
		// Apply the PIPs we have choosen to the net
		net.setPIPs(pipList);
	}

	/**
	 * Saves the design to a file.
	 * @param outputFileName
	 */
	private void saveDesign(String outputFileName){
		if(outputFileName.toLowerCase().endsWith("ncd")){
			String xdlFileName = outputFileName+"temp.xdl";
			design.saveXDLFile(Paths.get(xdlFileName), true, true);
			FileConverter.convertXDL2NCD(xdlFileName, outputFileName);

			// Delete the temporary XDL file, if needed
			//FileTools.deleteFile(xdlFileName); 
		}
		else{
			design.saveXDLFile(Paths.get(outputFileName), true, true);
		}
	}
	
	public static void main(String[] args){
		if(args.length != 2){
			MessageGenerator.briefMessageAndExit("USAGE: <input.xdl|input.ncd> <output.xdl|output.ncd>");
		}
		HandRouter hr = new HandRouter(args[0]);
		
		String nl = System.getProperty("line.separator");
		MessageGenerator.printHeader("Hand Router Example");
		System.out.println("This program will read in a design and allow the user to " +
				"route (or reroute) a "+nl+"net.");
		
		
		boolean continueRouting = true;
		hr.br = new BufferedReader(new InputStreamReader(System.in));
		
		while(continueRouting){
			System.out.println(nl+"Commands:");
			System.out.println("  1: Route a net by name");
			System.out.println("  2: Route an arbitrary net (for fun)");
			System.out.println("  3: Save design");
			System.out.println("  4: Exit");
			
			try{
				System.out.print(">> ");
				Integer cmd = Integer.parseInt(hr.br.readLine().trim());
				switch(cmd){
					case 1:
						System.out.println("Enter net name:");
						hr.HandRoute(hr.br.readLine().trim());
						break;
					case 2:
						for(Net net : hr.design.getNets()){
							System.out.println("Routing net: " + net.getName());
							hr.HandRoute(net.getName());
							break;
						}
						break;
					case 3:
						hr.saveDesign(args[1]);
						System.out.println("Design saved to " + args[1]);
						break;
					case 4:
						System.exit(0);
					default:
						break;
				}
			}
			catch(IOException e){
				System.out.println("Error, try again.");	
			}
		}
	}
}