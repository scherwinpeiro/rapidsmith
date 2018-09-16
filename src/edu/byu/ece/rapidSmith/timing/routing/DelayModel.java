package edu.byu.ece.rapidSmith.timing.routing;

import edu.byu.ece.rapidSmith.design.Net;
import edu.byu.ece.rapidSmith.design.PIP;
import edu.byu.ece.rapidSmith.design.Pin;
import edu.byu.ece.rapidSmith.device.*;
import edu.byu.ece.rapidSmith.router.Node;
import edu.byu.ece.rapidSmith.timing.TimingCalibration;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

public abstract class DelayModel {
    
    	protected WireEnumerator we;
    	public List<PIP> seenPIP;
    	
    	/**
    	 * Calculates the delay for all sinks of a net
    	 * @param net the net
    	 * @return a map of sink Pin to delay
    	 */
    	public Map<Pin, Double> getNetDelay(Net net)
    	{
    	    Map<Pin, Double> delays, tmpDelays;
            Pin source = net.getSource();
            
            if(source == null)
        	return new HashMap<>();
            
            seenPIP = new ArrayList<>();
            
            Device device = source.getTile().getDevice();
            List<PIP> pathToSink;
            Node source_node = new Node(source.getTile(), device.getPrimitiveExternalPin(source), null, 0);
            WireConnection[] connections = source_node.getConnections();
            List<Pin> sinks = new ArrayList<>(net.getPins());
            sinks.remove(net.getSource());
            
            we = device.getWireEnumerator();
    
            pathToSink = net.getPIPs();
            delays = new HashMap<>();
            if(connections != null)
            {
        	Set<Node> seenNodes = new HashSet<Node>();
                for(WireConnection con : connections)
                {
                    tmpDelays = getSubPathDelay(pathToSink, con, source_node, sinks, seenNodes);
                    if(tmpDelays != null)
                    {
                	tmpDelays.forEach((k,v)->delays.put(k,v));
                    }
                }
            }
            
            return delays;
   
    	}
    
	/**
	 * Calculate the delay from the source to a specified sink of a routed net.
	 * @param net The net to use
	 * @param sink the sink to use
	 * @return delay in ns. -1 if no connection is found
	 */
	public double getDelay(Net net, Pin sink){
	    double delay, tmpDelay;
            Pin source = net.getSource();
            Device device = source.getTile().getDevice();
            List<PIP> pathToSink;
            Node source_node = new Node(source.getTile(), device.getPrimitiveExternalPin(source), null, 0);
            Node sink_node = new Node(sink.getTile(), device.getPrimitiveExternalPin(sink), null, 0); //parent is null?
            WireConnection[] connections = source_node.getConnections();
            
            we = device.getWireEnumerator();
            delay = -1;

            pathToSink = net.getPIPs();
     
            if(connections != null)
            {
        	Set<Node> seenNodes = new HashSet<Node>();
                for(WireConnection con : connections)
                {
                    tmpDelay = getSubPathDelay(pathToSink, con, source_node, sink_node, seenNodes);
                    if(tmpDelay > 0)
                    {
//                	System.out.println("\n");
                	return tmpDelay;
                    }
                }
            }
            
            return delay;
	}
	
	/**
	 * Calculates the delay Function for a given net and a sink
	 * @param net the net
	 * @param sink the sink
	 * @return the resulting Function.
	 */
	public NetFunction getNetFunction(Net net, Pin sink)
	{
	               
            Pin source = net.getSource();
            List<PIP> pathToSink;
            Device device = source.getTile().getDevice();
            Node source_node = new Node(source.getTile(), device.getPrimitiveExternalPin(source), null, 0);
            Node sink_node = new Node(sink.getTile(), device.getPrimitiveExternalPin(sink), null, 0); //parent is null?
            WireConnection[] connections = source_node.getConnections();
            
            we = device.getWireEnumerator();

            pathToSink = net.getPIPs();
            
            
            if(connections != null)
            {
               Set<Node> seenNodes = new HashSet<Node>();
               for(WireConnection con : connections)
               { 
                   NetFunction subPathFunction = getSubPathFunction(pathToSink, con, source_node, sink_node, seenNodes);
                   if(subPathFunction != null)
                   {
            	   	//System.out.println("\n");
            	   	return subPathFunction;
                   }
               }
            }
            	
            //return empty Function
            return new NetFunction();
	}
	
	/**
	 * Calculates all delay Function for a given net
	 * @param net the net
	 * @return a map of Functions associated with the propriate sink.
	 */
	public Map<Pin, NetFunction> getNetFunction(Net net)
	{
	    Pin source = net.getSource();
	    if(source == null)
		return new HashMap<>();
            
	    List<PIP> pathToSink;
            Device device = source.getTile().getDevice();
            Node source_node = new Node(source.getTile(), device.getPrimitiveExternalPin(source), null, 0);
            WireConnection[] connections = source_node.getConnections();
            List<Pin> sinks = new ArrayList<>(net.getPins());
            sinks.remove(net.getSource());
            
            we = device.getWireEnumerator();

            pathToSink = net.getPIPs();
            
            Map<Pin, NetFunction> subPathFunction = new HashMap<>();
            
            if(connections != null)
            {
               Set<Node> seenNodes = new HashSet<Node>();
               for(WireConnection con : connections)
               { 
                   Map<Pin, NetFunction> tmpPathFunction = getSubPathFunction(pathToSink, con, source_node, sinks, seenNodes);
                   
                   if(tmpPathFunction != null)
            	   	tmpPathFunction.forEach((k,v)->subPathFunction.put(k, v));
               }
            }
            	
            //return empty Function
            return subPathFunction;
	}
	
	/**
	 * Evaluates Distance for (parent)-&gt;wire-&gt;(node). Parent is calculated internally
	 * @param node SinkNode to a wire
	 * @param we Wire Enumerator
	 * @return Manhattan Distance
	 */
	protected Map<WireDimension, Integer> getLengthFactor(Node node, WireEnumerator we){
	    Map<WireDimension, Integer> retVal = new EnumMap<>(WireDimension.class);
	    
	    Node parent = node.getParent();

	    Tile parentTile = parent.getTile();
	    Tile nodeTile = node.getTile();
	    
	    Device dev = nodeTile.getDevice();
	    
	    TileType typeN = nodeTile.getType();
	    TileType typeP = parentTile.getType();
	    	    
	    int rowP = parentTile.getRow();
	    int columnP = parentTile.getColumn();
	    int rowN = nodeTile.getRow();
	    int columnN = nodeTile.getColumn();
	    int tmpRow;
	    int tmpColumn;
	    
	    if(parentTile.equals(nodeTile))
	    {
		switch(typeN){
			case INT:
			    retVal.put(WireDimension.INT_Internal, 1);
			    break;
			case CLEXL:
			    retVal.put(WireDimension.CLEXL_Internal, 1);
			    break;
			case CLEXM:
			    retVal.put(WireDimension.CLEXM_Internal, 1);
			    break;
			case BIOI_INNER:
			    retVal.put(WireDimension.BIOI_INNER_Internal, 1);
			    break;
			case BIOI_OUTER:
			    retVal.put(WireDimension.BIOI_OUTER_Internal, 1);			    
			    break;
			case BIOB:
			    retVal.put(WireDimension.BIOB_Internal, 1);
			    break;
			case IOI_INT:
			    retVal.put(WireDimension.IOI_INT_Internal, 1);
			    break;
			default:
			    retVal.put(WireDimension.Other_Internal, 1);
			    break;
			    
		}
	    }
	    else
	    {
		//START NODE
		switch(typeP){
        		case INT:
        		    retVal.merge(WireDimension.INT_Start, 1, Integer::sum);
        		    break;
        		case CLEXL:
        		    retVal.merge(WireDimension.CLEXL_Start, 1, Integer::sum);
        		    break;
        		case CLEXM:
        		    retVal.merge(WireDimension.CLEXM_Start, 1, Integer::sum);
        		    break;
        		case BIOI_INNER:
        		    retVal.merge(WireDimension.BIOI_INNER_Start, 1, Integer::sum);
        		    break;
        		case BIOI_OUTER:
        		    retVal.merge(WireDimension.BIOI_OUTER_Start, 1, Integer::sum);			    
        		    break;
        		case BIOB:
        		    retVal.merge(WireDimension.BIOB_Start, 1, Integer::sum);
        		    break;
        		case IOI_INT:
        		    retVal.merge(WireDimension.IOI_INT_Start, 1, Integer::sum);
        		    break;
        		default:
        		    retVal.merge(WireDimension.Other_Start, 1, Integer::sum);
        		    break;  
		}
		
		//PATH first row then column
		for(tmpRow = rowP + 1; tmpRow < rowN; tmpRow++)
		{
		    Tile newTile = dev.getTile(tmpRow, columnP);
		    TileType typeTmp = newTile.getType();
		    switch(typeTmp){
        		case INT:
        		    retVal.merge(WireDimension.INT_Length, 1, Integer::sum);
        		    break;
        		case CLEXL:
        		    retVal.merge(WireDimension.CLEXL_Length, 1, Integer::sum);
        		    break;
        		case CLEXM:
        		    retVal.merge(WireDimension.CLEXM_Length, 1, Integer::sum);
        		    break;
        		case BIOI_INNER:
        		    retVal.merge(WireDimension.BIOI_INNER_Length, 1, Integer::sum);
        		    break;
        		case BIOI_OUTER:
        		    retVal.merge(WireDimension.BIOI_OUTER_Length, 1, Integer::sum);			    
        		    break;
        		case BIOB:
        		    retVal.merge(WireDimension.BIOB_Length, 1, Integer::sum);
        		    break;
        		case IOI_INT:
        		    retVal.merge(WireDimension.IOI_INT_Length, 1, Integer::sum);
        		    break;
        		default:
        		    retVal.merge(WireDimension.Other_Length, 1, Integer::sum);
        		    break;  
		    }
		
		}
		
		for(tmpColumn = columnN + 1; tmpColumn < columnN; tmpColumn++)
		{
		    Tile newTile = dev.getTile(tmpRow, tmpColumn);
		    TileType typeTmp = newTile.getType();
		    switch(typeTmp){
        		case INT:
        		    retVal.merge(WireDimension.INT_Width, 1, Integer::sum);
        		    break;
        		case CLEXL:
        		    retVal.merge(WireDimension.CLEXL_Width, 1, Integer::sum);
        		    break;
        		case CLEXM:
        		    retVal.merge(WireDimension.CLEXM_Width, 1, Integer::sum);
        		    break;
        		case BIOI_INNER:
        		    retVal.merge(WireDimension.BIOI_INNER_Width, 1, Integer::sum);
        		    break;
        		case BIOI_OUTER:
        		    retVal.merge(WireDimension.BIOI_OUTER_Width, 1, Integer::sum);			    
        		    break;
        		case BIOB:
        		    retVal.merge(WireDimension.BIOB_Width, 1, Integer::sum);
        		    break;
        		case IOI_INT:
        		    retVal.merge(WireDimension.IOI_INT_Width, 1, Integer::sum);
        		    break;
        		default:
        		    retVal.merge(WireDimension.Other_Width, 1, Integer::sum);
        		    break;  
		    }
		
		}
		
		//END NODE
		switch(typeN){
        		case INT:
        		    retVal.merge(WireDimension.INT_End, 1, Integer::sum);
        		    break;
        		case CLEXL:
        		    retVal.merge(WireDimension.CLEXL_End, 1, Integer::sum);
        		    break;
        		case CLEXM:
        		    retVal.merge(WireDimension.CLEXM_End, 1, Integer::sum);
        		    break;
        		case BIOI_INNER:
        		    retVal.merge(WireDimension.BIOI_INNER_End, 1, Integer::sum);
        		    break;
        		case BIOI_OUTER:
        		    retVal.merge(WireDimension.BIOI_OUTER_End, 1, Integer::sum);			    
        		    break;
        		case BIOB:
        		    retVal.merge(WireDimension.BIOB_End, 1, Integer::sum);
        		    break;
        		case IOI_INT:
        		    retVal.merge(WireDimension.IOI_INT_End, 1, Integer::sum);
        		    break;
        		default:
        		    retVal.merge(WireDimension.Other_End, 1, Integer::sum);
        		    break;  
		}
		
	    }
	    
	    //System.out.println("(" + columnP + "," + rowP+ ") -> (" + columnN + ","+ rowN + ")");
	    //System.out.println(retVal.toString());
	    
	    //int distance = node.getTile().getManhattanDistance(parent.getTile()) + 1;

	    //return distance;
	    return retVal;
	}
	
	protected ArrayList<PIP> findPath(Net net, Node sink)
	{
           Pin source = net.getSource();
            
           Node startNode = new Node(source.getTile(), source.getTile().getDevice().getPrimitiveExternalPin(source));
           ArrayList<PIP> retVal;
            
           if(startNode.getConnections() != null)
           {
           	Set<Node> seenNodes = new HashSet<Node>();
           	for(WireConnection con : startNode.getConnections())
           	{
           	    retVal = findSubPath(net, sink, con, startNode, seenNodes);
           	    if(retVal != null)
           		return retVal;
           	}
           }
           retVal = new ArrayList<PIP>();
           return retVal;
	}
	    
	private ArrayList<PIP> findSubPath(Net net, Node sink, WireConnection w, Node parent, Set<Node> seenNodes)
	{
        	//Node ref_node = new Node(w.getTile(parent.getTile()), w.getWire(), null, 0, w.isPIP());
        	Node curr_Node = new Node(w.getTile(parent.getTile()), w.getWire(), parent, parent.getLevel()+1, w.isPIP());
        	if (!seenNodes.add(curr_Node))
        		return null; //return new ArrayList<PIP>();
        	
        	ArrayList<PIP> subPath;
        
        
        	if(sink.equals(curr_Node))
        	{
        	    if(curr_Node.isPIP())
        	    {
        		subPath = new ArrayList<PIP>();
        		subPath.add(new PIP(curr_Node.getTile(), parent.getWire(), curr_Node.getWire()));
        		return subPath;
        	    }
        	    else
        		return new ArrayList<PIP>();
        	}
        	if(curr_Node.isPIP())
        	{
        	    PIP curr_PIP = new PIP(curr_Node.getTile(), parent.getWire(), curr_Node.getWire());
        	    if(net.getPIPs().contains(curr_PIP))
        	    {
        		if(curr_Node.getConnections()!= null)
        		{
        		    for(WireConnection con : curr_Node.getConnections())
        		    {
        			subPath = findSubPath(net, sink, con, curr_Node, seenNodes);
        			if(subPath != null)
        			{
        			    subPath.add(curr_PIP);
        			    return subPath;
        			}
        				
        		    }
        		}
        	    }
        	}
        	else
        	{
        	    if(curr_Node.getConnections()!= null)
        	    {
        		for(WireConnection con : curr_Node.getConnections())
        		{
        		    subPath = findSubPath(net, sink, con, curr_Node, seenNodes);
        		    if(subPath != null)
        			return subPath;	
        	            }
        	        }
        	}
        
        	return null;
	}
	    
	/**
	* Calculates the delay for a SubPath.
	* @param pipList List of pips that belong to the path
	* @param w wire connection as starting point
	* @param parent parent node to w
	* @param sinkNode sink node to the net
	* @param seenNodes seen nodes so far
	* @return the delay of the path to the sink. -1 if there is no connection between w and sink
	*/
	protected abstract double getSubPathDelay( List<PIP> pipList, WireConnection w, Node parent, Node sinkNode, Set<Node> seenNodes);
	
	/**
	* Calculates the delay for all sinks of a SubPath.
	* @param pipList List of pips that belong to the path
	* @param w wire connection as starting point
	* @param parent parent node to w
	* @param sinkPins all Sinks to the net
	* @param seenNodes seen nodes so far
	* @return a map of the delays to the sinks. -1 if there is no connection between w and sink
	*/
	protected abstract Map<Pin, Double> getSubPathDelay( List<PIP> pipList, WireConnection w, Node parent, List<Pin> sinkPins, Set<Node> seenNodes);
	
	/**
	 * Calculates the function for a SubPath.
	 * @param pipList List of pips that belong to the path
	 * @param w wire connection as starting point
	 * @param parent parent node to w
	 * @param sinkNode sink node to the net
	 * @param seenNodes seen nodes so far
	 * @return a map of RCType to multiplication factor of the path to the sink. null if there is no connection between w and sink
	 */
	protected abstract NetFunction getSubPathFunction( List<PIP> pipList, WireConnection w, Node parent, Node sinkNode, Set<Node> seenNodes);
	
	/**
	 * Calculates the function for a SubPath.
	 * @param pipList List of pips that belong to the path
	 * @param w wire connection as starting point
	 * @param parent parent node to w
	 * @param sinkPins a list of all sinks
	 * @param seenNodes seen nodes so far
	 * @return a map of sinks to NetFunctions
	 */
	protected abstract Map<Pin, NetFunction> getSubPathFunction( List<PIP> pipList, WireConnection w, Node parent, List<Pin> sinkPins, Set<Node> seenNodes);

	public static DelayModel fromTimingCalibration(TimingCalibration cal) {
		try {
			//Crazy reflection stuff :D
			Constructor<?> constructor = Class.forName(cal.delayModel).getConstructor(Double.TYPE, Double.TYPE, Double.TYPE, Double.TYPE, Map.class);
			return (DelayModel) constructor.newInstance(cal.RpCp,cal.RpCw,cal.RwCp,cal.RwCw, cal.wireFactors);
		} catch (ClassNotFoundException | IllegalAccessException | InstantiationException | InvocationTargetException | NoSuchMethodException e) {
			e.printStackTrace();
		}
		return null;
	}
}
