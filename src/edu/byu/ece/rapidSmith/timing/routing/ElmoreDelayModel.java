package edu.byu.ece.rapidSmith.timing.routing;

import edu.byu.ece.rapidSmith.design.PIP;
import edu.byu.ece.rapidSmith.design.Pin;
import edu.byu.ece.rapidSmith.device.WireConnection;
import edu.byu.ece.rapidSmith.device.WireEnumerator;
import edu.byu.ece.rapidSmith.router.Node;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ElmoreDelayModel extends DelayModel {
 
    private enum CType{Cp, Cw};
    
    private final double RpCp;
    private final double RpCw;
    private final double RwCp;
    private final double RwCw;
    
    private final Map<WireDimension, Double> wireFactors;
   
    
   // private Map<CType, Map<WireDimension, Double>> cCountMap;
    
    public ElmoreDelayModel()
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

    public ElmoreDelayModel(double RpCp, double RpCw, double RwCp, double RwCw, Map<WireDimension, Double> wireFactors)
    {
	this.RpCp = RpCp;
	this.RpCw = RpCw;
	this.RwCp = RwCp;
	this.RwCw = RwCw;

	
	this. wireFactors = wireFactors;
    }
    
    @Override
    protected double getSubPathDelay( List<PIP> pipList, WireConnection w,  Node parent, Node sinkNode, Set<Node> seenNodes)
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
	
	Set<Node> seenNodesCpy = new HashSet<>(seenNodes);

	
	double subDelay = -1;
	double tmpDelay =  -1;
	
	
	if(curr_node.equals(sinkNode))
	{
	    tmpDelay = evaluateNodeDelay(curr_node, we, seenNodesCpy, pipList);
	    return tmpDelay;
	}
	
	if(curr_node.getConnections() != null)
	{
	    for(WireConnection con : curr_node.getConnections())
	    {
		tmpDelay = getSubPathDelay(pipList, con, curr_node, sinkNode, seenNodes);
		if(tmpDelay >= 0)
		{
		    subDelay  = tmpDelay + evaluateNodeDelay(curr_node, we, seenNodesCpy, pipList);
		    return subDelay;
		}
	    }
	}
	
	return -1;
    }
  
    /**
     * (wire1)->(node)->(wire2)
     * Evaluates the delay for the wire element that leads TO the node element (wire1)!
     * @param node Node
     * @param we WireEnumerator
     * @return the delay of wire1
     */
    private double evaluateNodeDelay(Node node, WireEnumerator we, Set<Node> seenNode, List<PIP> pipList)
    {
	Map<CType, Double> cCount = new EnumMap<>(CType.class);
	
	Map<WireDimension, Integer> lengthMap = getLengthFactor(node, we);
	double lFRValue = 0;
	for (WireDimension dim : lengthMap.keySet()) {
	    lFRValue +=wireFactors.get(dim)*lengthMap.get(dim);
	}
	
	//double lFCValue = lFRValue;
	Set<Node> newSeenNode = new HashSet<Node>(seenNode);
	newSeenNode.remove(node);
	Map<CType, Map<WireDimension, Integer>> subTreeCapac = getRecursiveSubtreeCapacity(node, newSeenNode, pipList);
	
	double value = 0;
	
	Map<WireDimension, Integer> subMap = subTreeCapac.getOrDefault(CType.Cw, new HashMap<>());
	for(WireDimension dim : subMap.keySet())
	{
	    value +=subMap.get(dim)*wireFactors.getOrDefault(dim, 0.0);
	}
	cCount.put(CType.Cw, value);
	
	cCount.put(CType.Cp, subTreeCapac.getOrDefault(CType.Cp, new HashMap<>()).getOrDefault(WireDimension.PassTransistor, 0)*1.0);
	
	double delay = 0;
	
	if(node.isPIP())
	{
	    delay = RpCw*cCount.get(CType.Cw)+RpCp*(cCount.get(CType.Cp));
	    delay += RwCw*lFRValue*cCount.get(CType.Cw)+RwCp*lFRValue*(cCount.getOrDefault(CType.Cp, 1.0)-1.0);
	}
	else
	
	    delay = RwCw*lFRValue*cCount.get(CType.Cw)+RwCp*lFRValue*cCount.get(CType.Cp); 
	
	return delay;
    }
    
    private Map<CType, Map<WireDimension, Integer>> getRecursiveSubtreeCapacity(Node node, Set<Node> seenNodes, List<PIP> pipList)
    {
	Map<CType, Map<WireDimension, Integer>> retVal = new EnumMap<>(CType.class);
	Map<WireDimension, Integer> lF = getLengthFactor(node, we);
	
	retVal.put(CType.Cp, new EnumMap<>(WireDimension.class));
	retVal.put(CType.Cw, new EnumMap<>(WireDimension.class));
	
	if(node.isPIP())
	{
	    PIP curr_PIP = new PIP(node.getTile(), node.getParent().getWire(), node.getWire());
	    PIP other_PIP = new PIP(node.getTile(), node.getParent().getWire(), node.getWire());
	    if(!pipList.contains(curr_PIP) && !pipList.contains(other_PIP))
		return retVal;
	}
	
	if(seenNodes.contains(node))
	    return retVal;
	else
	    seenNodes.add(node);
	
	if(node.isPIP())
	    retVal.get(CType.Cp).put(WireDimension.PassTransistor, 1);
	
	lF.forEach((k,v)-> retVal.get(CType.Cw).put(k, v));
	if(node.getConnections()!= null)
	{
            for(WireConnection con : node.getConnections())
            {
                Node newNode = new Node(con.getTile(node.getTile()), con.getWire(), node, node.getLevel() + 1, con.isPIP());
                Map<CType, Map<WireDimension, Integer>> tmpMap = getRecursiveSubtreeCapacity(newNode, seenNodes, pipList);
                for (CType type : tmpMap.keySet()) {
                    Map<WireDimension, Integer> lengthTmp = tmpMap.getOrDefault(type, new EnumMap<>(WireDimension.class));
                    Map<WireDimension, Integer> oldLength = retVal.getOrDefault(type, new EnumMap<>(WireDimension.class));
                    lengthTmp.forEach((k, v) -> oldLength.merge(k, v, Integer::sum));
                    retVal.put(type, oldLength);
                }
            }	
	}	
	return retVal;
    }
    
    @Override
    protected NetFunction getSubPathFunction( List<PIP> pipList, WireConnection w, Node parent, Node sinkNode, Set<Node> seenNodes)
    {
	Node curr_node = new Node(w.getTile(parent.getTile()), w.getWire(), parent, parent.getLevel() + 1, w.isPIP());
	//Node ref_node = new Node(w.getTile(parent.getTile()), w.getWire(), null, 0, w.isPIP());
	
	if(curr_node.isPIP())
	{
	    PIP curr_pip = new PIP(curr_node.getTile(), parent.getWire(), curr_node.getWire());
	    PIP other_pip = new PIP(curr_node.getTile(), curr_node.getWire(), parent.getWire());
	    if(!pipList.contains(curr_pip) && !pipList.contains(other_pip))
		return null;
	}
	
	if (!seenNodes.add(curr_node))
		return null;
	
	Set<Node> seenNodesCpy = new HashSet<>(seenNodes);

	
	NetFunction tmpFunction;
	
	
	if(curr_node.equals(sinkNode))
	{
	    tmpFunction = evaluateNodeFunction(curr_node, we, seenNodesCpy, pipList);
	    return tmpFunction;
	    
	}
	
	if(curr_node.getConnections() != null)
	{
	    for(WireConnection con : curr_node.getConnections())
	    {
		NetFunction subFunction = getSubPathFunction(pipList, con, curr_node, sinkNode, seenNodes);	
		if(subFunction != null && !subFunction.isEmpty())
                {
		    	tmpFunction = evaluateNodeFunction(curr_node, we, seenNodesCpy, pipList);
            		subFunction.sumNetFunction(tmpFunction);
            		return subFunction;
                }
	    }
	}
	
	return null;
    }
       
    /**
     * (wire1)->(node)->(wire2)
     * Evaluates the delay for the wire element that leads TO the node element (wire1)!
     * @param node Node
     * @param we WireEnumerator
     * @return  map of RCType to multiplication factor of the node element.
     */
    private NetFunction evaluateNodeFunction(Node node, WireEnumerator we, Set<Node> seenNode, List<PIP> pipList)
    {
	Map<CType, Map<WireDimension, Integer>> cCountMap;
	
	//Map<WireDimension, Integer> lFR =  new EnumMap<>(WireDimension.class);

	Map<WireDimension, Integer> lFR = getLengthFactor(node, we);
	
	//cCountMap.put(CType.Cp, 0.0);
	//cCountMap.put(CType.Cw, 0.0);
	
	
	Set<Node> newSeenNode = new HashSet<Node>(seenNode);
	newSeenNode.remove(node);
	cCountMap = getRecursiveSubtreeCapacity(node, newSeenNode, pipList);
	
	
	NetFunction nodeFunction = new NetFunction();
	
	if(node.isPIP())
	{   
	    Map<WireDimension, Integer> countCw = cCountMap.getOrDefault(CType.Cw, new EnumMap<>(WireDimension.class));
	    if(!countCw.isEmpty())
		nodeFunction.addLengthFunction(RCType.RpCw, null, countCw);
	    
	    Map<WireDimension, Integer> countCp = cCountMap.getOrDefault(CType.Cp, new EnumMap<>(WireDimension.class));
	    if(!countCp.isEmpty() && countCp.getOrDefault(WireDimension.PassTransistor, 0) > 0)
		nodeFunction.addRpCpCount(countCp.get(WireDimension.PassTransistor));
	    
	    if(!lFR.isEmpty() && !countCw.isEmpty())
		nodeFunction.addLengthFunction(RCType.RwCw, lFR, countCw);
	    
	    if(!lFR.isEmpty() && countCp.getOrDefault(WireDimension.PassTransistor, 1) > 1)
	    {
		Map<WireDimension, Integer> multipliedLF = new HashMap<>();
		lFR.forEach((k,v) -> multipliedLF.put(k, v*countCp.getOrDefault(WireDimension.PassTransistor, 1)-1));
		nodeFunction.addLengthFunction(RCType.RwCp, multipliedLF, null);
	    }
	    
	    
	}
	else
	{
	    Map<WireDimension, Integer> countCw = cCountMap.getOrDefault(CType.Cw, new EnumMap<>(WireDimension.class));
	    if(!lFR.isEmpty() && !countCw.isEmpty())
		nodeFunction.addLengthFunction(RCType.RwCw, lFR, countCw);
	    
	    Map<WireDimension, Integer> countCp = cCountMap.getOrDefault(CType.Cp, new EnumMap<>(WireDimension.class));
	    if(!lFR.isEmpty() && countCp.getOrDefault(WireDimension.PassTransistor, 0)> 0)
	    {
		Map<WireDimension, Integer> multipliedLF = new HashMap<>();
		lFR.forEach((k,v) -> multipliedLF.put(k,v*countCp.getOrDefault(WireDimension.PassTransistor, 0)));
		nodeFunction.addLengthFunction(RCType.RwCp, multipliedLF, null);
	    }
	}
	
	return nodeFunction;
    }

    
    @Override
    protected Map<Pin, Double> getSubPathDelay(List<PIP> pipList, WireConnection w, Node parent, List<Pin> sinkPins, Set<Node> seenNodes) {
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
	
	Set<Node> seenNodesCpy = new HashSet<>(seenNodes);

	Map<Pin, Double> retVal = new HashMap<>();
	Map<Pin, Double> subDelay;
	double tmpDelay =  -1;
	
	
	for (Pin pin : sinkPins) {
	    if(curr_node.equals(new Node(pin.getTile(), curr_node.getTile().getDevice().getPrimitiveExternalPin(pin), null, 0)))
            {
		tmpDelay = evaluateNodeDelay(curr_node, we, seenNodesCpy, pipList);
		retVal.put(pin, tmpDelay);
		return retVal;
            }
	}
	
	if(curr_node.getConnections() != null)
	{
	    for(WireConnection con : curr_node.getConnections())
	    {
		subDelay = getSubPathDelay(pipList, con, curr_node, sinkPins, seenNodes);
		if(subDelay != null && subDelay.size() > 0)
		{
		    tmpDelay  =  evaluateNodeDelay(curr_node, we, seenNodesCpy, pipList);
		    for (Pin pin : subDelay.keySet()) {
			subDelay.merge(pin, tmpDelay, Double::sum);
		    }
		    retVal.putAll(subDelay);
		}
	    }
	}
	
	return retVal;
    }

    
    @Override
    protected Map<Pin, NetFunction> getSubPathFunction(List<PIP> pipList, WireConnection w, Node parent, List<Pin> sinkPins, Set<Node> seenNodes) {
	
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
	
	Set<Node> seenNodesCpy = new HashSet<>(seenNodes);

	
	NetFunction tmpFunction;
	Map<Pin, NetFunction> retVal = new HashMap<>();
	
	
	for (Pin pin : sinkPins) {
	    if(curr_node.equals(new Node(pin.getTile(), curr_node.getTile().getDevice().getPrimitiveExternalPin(pin), null, 0)))
            {
		tmpFunction = evaluateNodeFunction(curr_node, we, seenNodesCpy, pipList);
		retVal.put(pin, tmpFunction);
		return retVal;
            }
	    
	}
	
	if(curr_node.getConnections() != null)
	{
	    for(WireConnection con : curr_node.getConnections())
	    {
		Map<Pin, NetFunction> subFunction = getSubPathFunction(pipList, con, curr_node, sinkPins, seenNodes);	
		if(subFunction != null && !subFunction.isEmpty())
                {
		   tmpFunction = evaluateNodeFunction(curr_node, we, seenNodesCpy, pipList);
		   for (Pin pin : subFunction.keySet()) {
		       subFunction.get(pin).sumNetFunction(tmpFunction);
		   }
		   retVal.putAll(subFunction);
                }
	    }
	}
	
	return retVal;
    }
    
    

}
