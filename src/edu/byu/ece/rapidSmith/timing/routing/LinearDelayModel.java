package edu.byu.ece.rapidSmith.timing.routing;

import edu.byu.ece.rapidSmith.design.PIP;
import edu.byu.ece.rapidSmith.design.Pin;
import edu.byu.ece.rapidSmith.device.WireConnection;
import edu.byu.ece.rapidSmith.device.WireEnumerator;
import edu.byu.ece.rapidSmith.router.Node;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;



public class LinearDelayModel extends DelayModel {
    
    //private final double R_pass = 1.0;
    //private final double R_wire = 2.0;
    //private final double C_pass = 1.2;
    //private final double C_wire = 1.3;
    

    private double successorLength = 1; //getLengthFactor(successor, we);
    private double nodeLength = 1;
    
    private final double RpCp;
    private final double RpCw;
    private final double RwCp;
    private final double RwCw;
    
    
    private final Map<WireDimension, Double> wireFactors;

    /**
    * RpCp = 1.2
    * RpCw = 1.3
    * RwCp = 2.4
    * RwCw = 2.6
    **/
   
    
    public LinearDelayModel()
    {
	this.RpCp = 1.2;
	this.RpCw = 1.3;
	this.RwCp = 2.4;
	this.RwCw = 2.6;
	
	Map<WireDimension, Double> basicFactors = new EnumMap<>(WireDimension.class);
	for(WireDimension dim : WireDimension.values())
	    basicFactors.put(dim, 1.0);
	
	wireFactors = basicFactors;
	
    }
    
    public LinearDelayModel(double RpCp, double RpCw, double RwCp, double RwCw, Map<WireDimension, Double> wireFactors)
    {
	this.RpCp = RpCp;
	this.RpCw = RpCw;
	this.RwCp = RwCp;
	this.RwCw = RwCw;
	
	this.wireFactors = wireFactors;
	
    }
    
    @Override
    protected double getSubPathDelay( List<PIP> pipList, WireConnection w, Node parent, Node sinkNode, Set<Node> seenNodes)
    {
	Node curr_node = new Node(w.getTile(parent.getTile()), w.getWire(), parent, parent.getLevel() + 1, w.isPIP());
	//Node ref_node = new Node(w.getTile(parent.getTile()), w.getWire(), null, 0, w.isPIP());
	
	if(curr_node.isPIP())
	{
	    PIP curr_pip = new PIP(curr_node.getTile(), parent.getWire(), curr_node.getWire());
	    PIP other_pip = new PIP(curr_node.getTile(), curr_node.getWire(), parent.getWire());
	    if(!pipList.contains(curr_pip) && !pipList.contains(other_pip))
		return -1;
	}
	
	if (!seenNodes.add(curr_node))
		return -1;
	
	
	double subDelay = -1;
	double tmpDelay =  -1;
	
	if(curr_node.equals(sinkNode))
	{
	    tmpDelay = evaluateSinkDelay(curr_node, we);
	    //evaluateNodeDelay must not be called if parent == source_node!!! 
	    if(parent.getLevel() > 0)
		return tmpDelay + evaluateNodeDelay(parent, curr_node, we);
	    else
		return tmpDelay;
	}
	
	if(curr_node.getConnections() != null)
	{
	    for(WireConnection con : curr_node.getConnections())
	    {
		tmpDelay = getSubPathDelay(pipList, con, curr_node, sinkNode, seenNodes);
		if(tmpDelay >= 0)
		{
		    //evaluateNodeDelay must not be called if parent == source_node!!!
		    if(parent.getLevel()> 0 )
			subDelay = tmpDelay + evaluateNodeDelay(parent, curr_node, we);
		    else
			subDelay = tmpDelay;
		    return subDelay;
		}
	    }
	}
	
	return subDelay;
    }
    
    @Override
    protected Map<Pin, Double> getSubPathDelay(List<PIP> pipList, WireConnection w, Node parent, List<Pin> sinkPins, Set<Node> seenNodes) {
	
	Node curr_node = new Node(w.getTile(parent.getTile()), w.getWire(), parent, parent.getLevel() + 1, w.isPIP());
	
	if(curr_node.isPIP())
	{
	    PIP curr_pip = new PIP(curr_node.getTile(), parent.getWire(), curr_node.getWire());
	    PIP other_pip = new PIP(curr_node.getTile(), curr_node.getWire(), parent.getWire());
	    if(!pipList.contains(curr_pip) && !pipList.contains(other_pip))
		return  null;
	}
	String wireName = we.getWireName(w.getWire());
	List<String> connectionNames = new ArrayList<>();
	if(curr_node.getConnections() != null)
	{
	    for (WireConnection con : curr_node.getConnections()) {
		connectionNames.add(we.getWireName(con.getWire()));
	    }
	}
	
	if (!seenNodes.add(curr_node))
		return new HashMap<>();
	
	
	double subDelay = -1;
	double ownDelay = 0;
	Map<Pin, Double> tmpDelay;
	Map<Pin, Double> retVal = new HashMap<>();
	
	
	
	for (Pin pin : sinkPins) {
	    if(curr_node.equals(new Node(pin.getTile(), curr_node.getTile().getDevice().getPrimitiveExternalPin(pin), null, 0)))
            {
                subDelay = evaluateSinkDelay(curr_node, we);  
                //evaluateNodeDelay must not be called if parent == source_node!!! 
                if(parent.getLevel() > 0)
            	retVal.put(pin, subDelay + evaluateNodeDelay(parent, curr_node, we));
                else
            	retVal.put(pin, subDelay);
            	
            	return retVal;
            }
	}
	
	
	if(curr_node.getConnections() != null)
	{
	    for(WireConnection con : curr_node.getConnections())
	    {
		tmpDelay = getSubPathDelay(pipList, con, curr_node, sinkPins, seenNodes);
		if(tmpDelay != null)
		{
		    //evaluateNodeDelay must not be called if parent == source_node!!!
		    if(parent.getLevel()> 0 )
			ownDelay = evaluateNodeDelay(parent, curr_node, we);
		    
		    for (Pin pin : tmpDelay.keySet()) {
			retVal.put(pin, ownDelay+tmpDelay.get(pin));
		    }
		}
	    }
	}
	
	return retVal;
    }
    
    private double evaluateNodeDelay(Node node, Node successor, WireEnumerator we)
    {
	//System.out.println("Calculating delay " + parent.toString(we) + " -> " + node.toString(we));
	//+1: since this is a factor it should be greater than 0
	Map<WireDimension, Integer> lFSuccessor = new HashMap<>(); 
	Map<WireDimension, Integer> lFNode = getLengthFactor(node, we);          
	
	if(!successor.isPIP())
	    lFSuccessor = getLengthFactor(successor, we);
	
	successorLength = 0; //getLengthFactor(successor, we);
	nodeLength = 0; //getLengthFactor(node, we);
		    
	lFNode.forEach((k,V) -> nodeLength+=wireFactors.get(k)*V);
	if(!successor.isPIP())
	    lFSuccessor.forEach((k,V) -> successorLength+=wireFactors.get(k)*V);
	
	double delay = 0;
	if(node != null)
	{
	    if(node.isPIP())
	    {
		if(successor.isPIP())
		{
		    //System.out.print("+R_pass*2*C_pass");
		    delay =  RpCp + RpCw*nodeLength + RwCw*(nodeLength*nodeLength) + RwCp*nodeLength; 
		}
		else
		{
		    //System.out.print("+R_pass*(C_pass+C_wire)");
		    delay =   RpCp + RpCw*nodeLength + RwCw*nodeLength*nodeLength + RwCw*nodeLength*successorLength; 
		}
	    }
	    else
	    {
		if(successor.isPIP())
		{
		    //System.out.print("+R_wire*(C_wire+C_pass)");
		    delay =   RwCw*nodeLength*nodeLength+RwCp*nodeLength; 
		}
		else
		{
		    //System.out.print("+R_wire*2*C_wire");
		    delay =  RwCw*nodeLength*nodeLength+ RwCw*nodeLength*successorLength;// R_wire*2*C_wire;;
		    
		}
	    }
	}
	//
	return delay;//*lengthFactor;
    }
    
    private double evaluateSinkDelay(Node sinkNode, WireEnumerator we)
    {
	//System.out.println("Sink node is: " + sinkNode.toString(we));
	nodeLength = 0;
	Map<WireDimension, Integer> lFNode = getLengthFactor(sinkNode, we);  
	lFNode.forEach((k,V) -> nodeLength+=wireFactors.get(k)*V);
	
	if(sinkNode.isPIP())
	{
	    //Pip + Wire
	    //= RpCp+RpCw*Lw+Lw*RwCw*Lw (Rp, Cp don't have a length factor)
	    return RpCp + RpCw*nodeLength + nodeLength*RwCw*nodeLength; //C_pass*R_pass;

	}
	else
	{
	    //Wire
	    //System.out.print("C_wire*R_wire");
	    return RwCw*nodeLength*nodeLength; //C_wire*R_wire;

	}
    }

    @Override
    protected NetFunction getSubPathFunction( List<PIP> pipList, WireConnection w, Node parent, Node sinkNode, Set<Node> seenNodes)
    {
	//Node ref_node = new Node(w.getTile(parent.getTile()), w.getWire(), null, 0, w.isPIP());
	Node curr_node = new Node(w.getTile(parent.getTile()), w.getWire(), parent, parent.getLevel() + 1, w.isPIP());
	
	if(curr_node.isPIP())
	{
	    PIP curr_pip = new PIP(curr_node.getTile(), parent.getWire(), curr_node.getWire());
	    PIP other_pip = new PIP(curr_node.getTile(), curr_node.getWire(), parent.getWire());
	    if(!pipList.contains(curr_pip) && !pipList.contains(other_pip))
		return null;
	}
	
	if (!seenNodes.add(curr_node))
		return null;

	
	
	
	if(curr_node.equals(sinkNode))
	{
	    NetFunction subFunction = evaluateSinkFunction(curr_node, we);
	    
	    
	    if(parent.getLevel() > 0)
	    {
		NetFunction tmpFunction = evaluateNodeFunction(parent, curr_node, we);
	    
		if(subFunction != null && tmpFunction != null)
		{
		    subFunction.sumNetFunction(tmpFunction);
		}
	    }
	    
	    return subFunction;
	}
	
	if(curr_node.getConnections() != null)
	{
	    for(WireConnection con : curr_node.getConnections())
	    {
		NetFunction subFunction = getSubPathFunction(pipList, con, curr_node, sinkNode, seenNodes);
		
		if(parent.getLevel()> 0 )
		{
		    NetFunction tmpFunction = evaluateNodeFunction(parent, curr_node, we);
                    if(tmpFunction != null && subFunction != null)
                    {
                	subFunction.sumNetFunction(tmpFunction);	             
                    }
		}
		if(subFunction != null)
		    return subFunction; 
	    }
	}
	
	return null;
    }
    
    private NetFunction evaluateNodeFunction(Node node, Node successor, WireEnumerator we)
    {
	Map<WireDimension, Integer> lFSuccessor = new HashMap<>();
	Map<WireDimension, Integer> lFNode = getLengthFactor(node, we);
	
	if(!successor.isPIP())
	    lFSuccessor = getLengthFactor(successor, we);
	
	NetFunction retVal = new NetFunction();

	if(node != null)
	{
	    if(node.isPIP())
	    {
		if(successor.isPIP())
		{
		   
                   retVal.addLengthFunction(RCType.RpCp, null, null);
                   retVal.addLengthFunction(RCType.RpCw, null, lFNode);
                   retVal.addLengthFunction(RCType.RwCw, lFNode, lFNode);
                   retVal.addLengthFunction(RCType.RwCp, lFNode, null); 
		}
		else
		{
		    //System.out.print("+R_pass*(C_pass+C_wire)");
                    retVal.addLengthFunction(RCType.RpCp, null, null);
                    retVal.addLengthFunction(RCType.RpCw, null, lFNode);
                    retVal.addLengthFunction(RCType.RwCw, lFNode, lFNode);
                    retVal.addLengthFunction(RCType.RwCw, lFNode, lFSuccessor);
		}
	    }
	    else
	    {
		if(successor.isPIP())
		{
		    //System.out.print("+R_wire*(C_wire+C_pass)");
		    retVal.addLengthFunction(RCType.RwCw, lFNode, lFNode);
                    retVal.addLengthFunction(RCType.RwCp, lFNode, null);
		    
		}
		else
		{

		    //retVal.put(RCType.RwCw, lFNode*(lFNode+lFSuccessor));

		    retVal.addLengthFunction(RCType.RwCw, lFNode, lFNode);
		    retVal.addLengthFunction(RCType.RwCw, lFNode, lFSuccessor);
		}
	    }
	}

	return retVal;
    }
    
    private NetFunction evaluateSinkFunction(Node sinkNode, WireEnumerator we)
    {

	NetFunction retVal = new NetFunction();

	Map<WireDimension, Integer> lFNode = getLengthFactor(sinkNode,  we);
	
	if(sinkNode.isPIP())
	{
	    //Pip + Wire
	    //= RpCp+RpCw*Lw+Lw*RwCw*Lw ->(Rp, Cp don't have a length factor)
	    retVal.addLengthFunction(RCType.RpCp, null, null);
	    retVal.addLengthFunction(RCType.RpCw, null, lFNode);
	    retVal.addLengthFunction(RCType.RwCw, lFNode, lFNode);
	    return retVal;
	}
	else
	{	
	    retVal.addLengthFunction(RCType.RwCw, lFNode, lFNode);
	    return retVal;
	}
    }
 
    @Override
    protected Map<Pin, NetFunction> getSubPathFunction(List<PIP> pipList, WireConnection w, Node parent, List<Pin> sinkPins, Set<Node> seenNodes) {
	
	//Node ref_node = new Node(w.getTile(parent.getTile()), w.getWire(), null, 0, w.isPIP());
	Node curr_node = new Node(w.getTile(parent.getTile()), w.getWire(), parent, parent.getLevel() + 1, w.isPIP());
	if(curr_node.isPIP())
	{
	    PIP curr_pip = new PIP(curr_node.getTile(), parent.getWire(), curr_node.getWire());
	    PIP other_pip = new PIP(curr_node.getTile(), curr_node.getWire(), parent.getWire());
	    if(!pipList.contains(curr_pip) && !pipList.contains(other_pip))
		return null;
	}
	
	if (!seenNodes.add(curr_node))
		return null;

	
	Map<Pin, NetFunction> retVal = new HashMap<>();
	
	
	
	for (Pin pin : sinkPins) {
	    if(curr_node.equals(new Node(pin.getTile(), curr_node.getTile().getDevice().getPrimitiveExternalPin(pin), null, 0)))
            {
		NetFunction subFunction = evaluateSinkFunction(curr_node, we);
		if(parent.getLevel() > 0)
		{
		    NetFunction tmpFunction = evaluateNodeFunction(parent, curr_node, we);
	    
		    if(subFunction != null && tmpFunction != null)
		    {
			subFunction.sumNetFunction(tmpFunction);
		    }
		}
		retVal.put(pin, subFunction);
		return retVal;
            }
	}
	
	if(curr_node.getConnections() != null)
	{
	    for(WireConnection con : curr_node.getConnections())
	    {
		Map<Pin,NetFunction> subFunction = getSubPathFunction(pipList, con, curr_node, sinkPins, seenNodes);
		
		if(parent.getLevel()> 0 )
		{
		    NetFunction tmpFunction = evaluateNodeFunction(parent, curr_node, we);
                    if(tmpFunction != null && subFunction != null)
                    {
                	for (Pin pin : subFunction.keySet()) {
                	    subFunction.get(pin).sumNetFunction(tmpFunction);
                	    retVal.put(pin, subFunction.get(pin));
			}
                		             
                    }
		}
		if(subFunction != null)
		    retVal.putAll(subFunction);
	    }
	}
	
	return retVal;
    }
}

