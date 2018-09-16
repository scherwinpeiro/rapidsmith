package edu.byu.ece.rapidSmith.timing.routing;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class NetFunction {
    
    private Map<RCType, List<Map<WireDimension, Integer>>> resistanceFunctions;
    private Map<RCType, List<Map<WireDimension, Integer>>> capacityFunctions;
    int rpcpCount;
    
    public NetFunction()
    {
	resistanceFunctions = new EnumMap<>(RCType.class);
	capacityFunctions = new EnumMap<>(RCType.class);
	rpcpCount = 0;
    }
      
    public Map<RCType, List<Map<WireDimension, Integer>>> getResistanceLengthFunction()
    {
	return resistanceFunctions;
    }
    
    public Map<RCType, List<Map<WireDimension, Integer>>> getCapacityLengthFunction()
    {
	return capacityFunctions;
    }
    
    public int getRpCpCount()
    {
	return rpcpCount;
    }
    
    public void addRpCpCount(int count)
    {
	rpcpCount += count;
    }
    
    public void addLengthFunction(RCType type, Map<WireDimension, Integer> resistanceLengthFunction, Map<WireDimension, Integer> capacityLengthFunction){
	
	if(type == RCType.RpCp)
	    rpcpCount++;
	else{
	    List<Map<WireDimension, Integer>> oldLengthFunction = resistanceFunctions.getOrDefault(type, new ArrayList<>());
	    oldLengthFunction.add(resistanceLengthFunction);
	    resistanceFunctions.put(type, oldLengthFunction);
	
	    oldLengthFunction = capacityFunctions.getOrDefault(type, new ArrayList<>());
	    oldLengthFunction.add(capacityLengthFunction);
	    capacityFunctions.put(type, oldLengthFunction);
	}
    }
    
    public void addLengthFunction(Map<RCType, List<Map<WireDimension, Integer>>> resistanceLengthFunction, Map<RCType, List<Map<WireDimension, Integer>>> capacityLengthFunction){
	for(RCType type : RCType.values()){
	    List<Map<WireDimension, Integer>> oldLengthFunction = resistanceFunctions.getOrDefault(type, new ArrayList<>());
	    List<Map<WireDimension, Integer>> additionalLengthFunction =  resistanceLengthFunction.getOrDefault(type, new ArrayList<>());
	    oldLengthFunction.addAll(additionalLengthFunction);
	    resistanceFunctions.put(type, oldLengthFunction);
		
	    oldLengthFunction = capacityFunctions.getOrDefault(type, new ArrayList<>());
	    additionalLengthFunction =  capacityLengthFunction.getOrDefault(type, new ArrayList<>());
	    oldLengthFunction.addAll(additionalLengthFunction);
	    capacityFunctions.put(type, oldLengthFunction);
	}
    }
     
    public void sumNetFunction(NetFunction otherFunction){
	addLengthFunction(otherFunction.getResistanceLengthFunction(), otherFunction.getCapacityLengthFunction());
	addRpCpCount(otherFunction.getRpCpCount());
    }
    
    public double calculatePoint(Map<RCType, Double> typeValues, Map<WireDimension, Double> wireValues){
	
	double retVal; 
	double value;
	List<Map<WireDimension, Integer>> resistanceLengthList; 
	List<Map<WireDimension, Integer>> capacityLengthList; 
	
	//RpCp -> PIPs don't have a length Factor
	retVal = typeValues.getOrDefault(RCType.RpCp, 0.0)*rpcpCount;
	
	//RpCw -> only C has a length Factor
	capacityLengthList = capacityFunctions.getOrDefault(RCType.RpCw, new ArrayList<>());
	value = 0.0;
	
        for(int i = 0; i < capacityLengthList.size(); i++)
        {	
            Map<WireDimension, Integer> capacityMap = capacityLengthList.get(i);
            for(WireDimension wireDim : capacityMap.keySet())
            {
        	value += capacityMap.getOrDefault(wireDim, 0)*wireValues.getOrDefault(wireDim, 0.0);
            }
	}  
	retVal += value*typeValues.getOrDefault(RCType.RpCw, 0.0);
	
	
	//RwCp -> only R has a length Factor
	resistanceLengthList = resistanceFunctions.getOrDefault(RCType.RwCp, new ArrayList<>());
	value = 0.0;
	
        for(int i = 0; i < resistanceLengthList.size(); i++)
        {	
            Map<WireDimension, Integer> resistivityMap = resistanceLengthList.get(i);
            for(WireDimension wireDim : resistivityMap.keySet())
            {
        	value += resistivityMap.getOrDefault(wireDim, 0)*wireValues.getOrDefault(wireDim, 0.0);
            }
	}  
	retVal += value*typeValues.getOrDefault(RCType.RwCp, 0.0);
	
	
	
	//RwCw -> both have a length Factor
	resistanceLengthList = resistanceFunctions.getOrDefault(RCType.RwCw, new ArrayList<>());
	capacityLengthList = capacityFunctions.getOrDefault(RCType.RwCw, new ArrayList<>());	    
	value = 0.0;
	
	for(int i = 0; i < resistanceLengthList.size(); i++)
	{
	    double resistanceValue = 0.0;
	    double capacityValue = 0.0;
	    Map<WireDimension, Integer> resistanceMap = resistanceLengthList.get(i);
	    Map<WireDimension, Integer> capacityMap = capacityLengthList.get(i);       	
	    for(WireDimension wireDim : resistanceMap.keySet())
	    {    
		resistanceValue += resistanceMap.getOrDefault(wireDim, 0)*wireValues.getOrDefault(wireDim, 0.0);
	    }
	    for(WireDimension wireDim : capacityMap.keySet())
	    {
		capacityValue += capacityMap.getOrDefault(wireDim, 0)*wireValues.getOrDefault(wireDim, 0.0);
	    }
       		value +=resistanceValue*capacityValue;
	}
	
	retVal += value*typeValues.getOrDefault(RCType.RwCw, 0.0);
	
	return retVal;
	
    }
    
    public double[] calculateJacobianRow(Map<RCType, Double> typeValues, Map<WireDimension, Double> wireValues){
	double[] retVal = new double[WireDimension.values().length+4];
	List<Map<WireDimension, Integer>> resistanceLengthList; 
	List<Map<WireDimension, Integer>> capacityLengthList;
	
	//RpCp -> PIPs don't have a length Factor
        retVal[0] = rpcpCount;
        
        //RpCw -> only C has a length Factor
        capacityLengthList = capacityFunctions.getOrDefault(RCType.RpCw, new ArrayList<>());
        retVal[1] = 0.0;        
        for(int i = 0; i < capacityLengthList.size(); i++)
        {	
           Map<WireDimension, Integer> capacityMap = capacityLengthList.get(i);
           for(WireDimension wireDim : capacityMap.keySet())
           {
               retVal[1] += capacityMap.getOrDefault(wireDim, 0)*wireValues.getOrDefault(wireDim, 0.0);
           }
        }  
        
        
        //RwCp -> only R has a length Factor
        resistanceLengthList = resistanceFunctions.getOrDefault(RCType.RwCp, new ArrayList<>());
        retVal[2] = 0.0;
        for(int i = 0; i < resistanceLengthList.size(); i++)
        {	
            Map<WireDimension, Integer> resistivityMap = resistanceLengthList.get(i);
            for(WireDimension wireDim : resistivityMap.keySet())
            {
        	retVal[2] += resistivityMap.getOrDefault(wireDim, 0)*wireValues.getOrDefault(wireDim, 0.0);
            }
        }  
        
        
        
        //RwCw -> both have a length Factor
        resistanceLengthList = resistanceFunctions.getOrDefault(RCType.RwCw, new ArrayList<>());
        capacityLengthList = capacityFunctions.getOrDefault(RCType.RwCw, new ArrayList<>());	    
        retVal[3] = 0.0;
        
        for(int i = 0; i < resistanceLengthList.size(); i++)
        {
            double resistanceValue = 0.0;
            double capacityValue = 0.0;
            Map<WireDimension, Integer> resistanceMap = resistanceLengthList.get(i);
            Map<WireDimension, Integer> capacityMap = capacityLengthList.get(i);       	
            for(WireDimension wireDim : resistanceMap.keySet())
            {    
        	resistanceValue += resistanceMap.getOrDefault(wireDim, 0)*wireValues.getOrDefault(wireDim, 0.0);
            }
            for(WireDimension wireDim : capacityMap.keySet())
            {
        	capacityValue += capacityMap.getOrDefault(wireDim, 0)*wireValues.getOrDefault(wireDim, 0.0);
            }
            retVal[3] +=resistanceValue*capacityValue;
        }
        	
	//RCType[] types = RCType.values();	
	//for (int i = 0; i < types.length; i++) {    
	//    double value = 0.0;
	//    List<Map<WireDimension, Integer>> resistanceLengthList = resistanceLengthFunction.getOrDefault(types[i], new ArrayList<>());
	//    List<Map<WireDimension, Integer>> capacityLengthList = capacityLengthFunction.getOrDefault(types[i], new ArrayList<>());
	//    
	//    for(int k = 0; k < resistanceLengthList.size(); k++)
	//    {
	//	double resistanceValue = 0.0;
	//	double capacityValue = 0.0;
	//	Map<WireDimension, Integer> resistanceMap = resistanceLengthList.get(k);
	//	Map<WireDimension, Integer> capacityMap = capacityLengthList.get(k);
	//	
	//	for(WireDimension wireDim : resistanceMap.keySet())
	//	{
	//	    resistanceValue += resistanceMap.get(wireDim)*wireValues.getOrDefault(wireDim, 0.0);   
	//	}
	//	
	//	for(WireDimension wireDim: capacityMap.keySet())
	//	{
	//	    capacityValue += capacityMap.get(wireDim)*wireValues.getOrDefault(wireDim, 0.0);
	//	}
	//	value += resistanceValue*capacityValue;
	//    }
	//    retVal[i] = value;
	//}
	
	WireDimension[] dimensions = WireDimension.values();
	for(int i = 0; i < dimensions.length; i++)
	{
	    resistanceLengthList = resistanceFunctions.getOrDefault(RCType.RwCp, new ArrayList<>());
	    capacityLengthList = capacityFunctions.getOrDefault(RCType.RpCw, new ArrayList<>());
	    //RpCp part is 0 for derivation
	    //RwCp
	    for(Map<WireDimension, Integer> resistanceMap : resistanceLengthList)
	    {
		retVal[i+4] +=resistanceMap.getOrDefault(dimensions[i], 0)*typeValues.getOrDefault(RCType.RwCp, 0.0);
	    }
	    //RpCw
	    for(Map<WireDimension, Integer> capacityMap : capacityLengthList)
	    {
		retVal[i+4] +=capacityMap.getOrDefault(dimensions[i], 0)*typeValues.getOrDefault(RCType.RpCw, 0.0);
	    }
	    
	    //RwCw
	    double value = 0;
	    resistanceLengthList = resistanceFunctions.getOrDefault(RCType.RwCw, new ArrayList<>());
	    capacityLengthList = capacityFunctions.getOrDefault(RCType.RwCw, new ArrayList<>());
	    
	    for(int k = 0; k < resistanceLengthList.size(); k++)
	    {
		double resistanceValue = 0.0;
		double capacityValue = 0.0;
		Map<WireDimension, Integer> resistanceMap = resistanceLengthList.get(k);
		Map<WireDimension, Integer> capacityMap = capacityLengthList.get(k);
		for(WireDimension wireDim : resistanceMap.keySet())
		{
		    resistanceValue += resistanceMap.get(wireDim)*wireValues.getOrDefault(wireDim, 0.0);
		}
		for(WireDimension wireDim : capacityMap.keySet())
		{
		    capacityValue += capacityMap.get(wireDim)*wireValues.getOrDefault(wireDim, 0.0);
		}

		double resistanceFactor = resistanceMap.getOrDefault(dimensions[i], 0);
		double capacityFactor = capacityMap.getOrDefault(dimensions[i], 0);
		//(u*v)' = u'*v+u*v'
		value += (resistanceFactor*capacityValue + capacityFactor*resistanceValue);
	    }
	    retVal[i+4] += value*typeValues.getOrDefault(RCType.RwCw, 0.0);
	    
	}
	return retVal;
    }
    
    public boolean isEmpty()
    {
	if(resistanceFunctions.size() == 0 && capacityFunctions.size() == 0 && rpcpCount == 0)
	    return true;
	else
	    return false;
    }
}
	