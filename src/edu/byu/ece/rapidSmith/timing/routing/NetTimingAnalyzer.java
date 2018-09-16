package edu.byu.ece.rapidSmith.timing.routing;

import edu.byu.ece.rapidSmith.design.Net;
import edu.byu.ece.rapidSmith.design.Pin;
import edu.byu.ece.rapidSmith.timing.PathDelay;
import edu.byu.ece.rapidSmith.timing.PathElement;
import edu.byu.ece.rapidSmith.timing.RoutingPathElement;
import edu.byu.ece.rapidSmith.timing.TimingCalibration;
import edu.byu.ece.rapidSmith.timing.logic.AverageStrategy;
import org.apache.commons.math3.fitting.leastsquares.*;
import org.apache.commons.math3.fitting.leastsquares.LeastSquaresOptimizer.Optimum;
import org.apache.commons.math3.linear.RealVector;

import java.util.*;

public class NetTimingAnalyzer {
    
    public AverageStrategy averageStrategy;
    
    public static TimingCalibration analyzeNetTiming(List<Net> netList, List<PathDelay> pathDelayList, DelayModel delayModel, double[] startPoint)
    {
	
	JacobianNetFunction jacobianNetFunction = new JacobianNetFunction();
	
	Map<Net, Map<Pin, NetFunction>> allFunctions = new HashMap<>();
	
	for(Net net: netList)
	{
	    allFunctions.put(net, delayModel.getNetFunction(net));
	}
		
	// ANALISIS
	for(PathDelay pathDelay : pathDelayList)
	{
	    for(PathElement pathElement : pathDelay.getMaxDataPath())
	    {
		if(pathElement instanceof RoutingPathElement)
		{
		    Net routingNet = ((RoutingPathElement) pathElement).getNet();
		    Pin routingPin = pathElement.getPin();
		    
		    NetFunction delayFunction;
                    double result;
                    delayFunction = allFunctions.getOrDefault(routingNet, new HashMap<>()).get(routingPin);
                    //delayFunction = delayModel.getNetFunction(routingNet, routingPin);
                    result = pathElement.getDelay();
                    if(delayFunction != null && !delayFunction.isEmpty())
                    	jacobianNetFunction.putFunction(delayFunction, result);
		    
		}
	    }
	}
	
	if(delayModel instanceof LinearDelayModel)
	    System.out.println("USING LINEAR DELAY MODEL!");
	else if(delayModel instanceof ElmoreDelayModel)	
	    System.out.println("USING ELMORE DELAY MODEL!");
	
	//Optimize Problem
	LeastSquaresOptimizer optimizer = new LevenbergMarquardtOptimizer();//new GaussNewtonOptimizer(Decomposition.QR);
	
	LeastSquaresBuilder builder = new LeastSquaresBuilder();
	builder.model(jacobianNetFunction);
	
	builder.target(jacobianNetFunction.results);
	builder.maxIterations(100);
	builder.maxEvaluations(100);
	builder.checker(new EvaluationRmsChecker(0.001));
	builder.parameterValidator(jacobianNetFunction);
	
	
        builder.start(startPoint);
	
	
	LeastSquaresProblem problem= builder.build();
	Optimum optimum = optimizer.optimize(problem);
	//Random rnd = new Random();
	//for(int i = 0; i < 30; i++)
	//{
	//    for(int k = 0; k < startPoint.length; k++)
	//    {
	//	startPoint[k] = rnd.nextDouble();
	//    }
	//    
	//    builder.start(startPoint);
	//    problem = builder.build();
	//    Optimum newOptimum = optimizer.optimize(problem);
	//    if(newOptimum.getRMS() < optimum.getRMS())
	//	optimum = newOptimum;
	//}
	
	RealVector point = optimum.getPoint();
	Map<WireDimension, Double> wireFactors = new EnumMap<>(WireDimension.class);
	WireDimension[] dim = WireDimension.values();
	
	for(int i = 0; i < dim.length; i++)
	{
	    wireFactors.put(dim[i], point.getEntry(i+4));
	}
	
	TimingCalibration result = new TimingCalibration(new ArrayList<>(), point.getEntry(0), point.getEntry(1), point.getEntry(2), point.getEntry(3), delayModel.getClass().toString(), wireFactors);

	System.out.println("Optimization Result equals: ");
        System.out.println(optimum.getPoint().toString());        
        System.out.println("The RMS equals: " + optimum.getRMS());
        //System.out.println("Residual: " + optimum.getResiduals().toString());
        System.out.println("Number of Iterations: " + optimum.getIterations());
        System.out.println("Number of Evaluations: " + optimum.getEvaluations());
	
	return result;
	
	
    }
    
    public static TimingCalibration analyzeNetTiming(List<Net> netList, List<PathDelay> pathDelayList, DelayModel delayModel)
    {
	double[] startPoint = new double[WireDimension.values().length + 4];
	
	startPoint[0] = 0.05;
        startPoint[1] = 0.1;
        startPoint[2] = 0.1;
        startPoint[3] = 0.2;
        for(int i = 4; i < startPoint.length; i++)
            startPoint[i] = 0.01;
        
        return analyzeNetTiming(netList, pathDelayList, delayModel, startPoint);
    }
    
    public static TimingCalibration consitencyTest(List<Net> netList, DelayModel delayModel){
	JacobianNetFunction jacobianNetFunction = new JacobianNetFunction();
	
	
	//CONSISTENCY TEST -> OWN FUNCTION?
	//Analyze Nets
	for(Net net : netList)
	{
	    Map<Pin, Double> results = delayModel.getNetDelay(net);
	    Map<Pin, NetFunction> delayFunctions = delayModel.getNetFunction(net);
            for(Pin pin : results.keySet())
            {
            	if(!pin.equals(net.getSource())&&delayFunctions.containsKey(pin))
            	{
            	    NetFunction function;
            	    function = delayFunctions.get(pin);
            
            	    if(!function.isEmpty())
            		//Generate Problem
            		jacobianNetFunction.putFunction(function, results.get(pin));
            	}
            }
	}
	
	//Optimize Problem
	
	double[] startPoint = new double[WireDimension.values().length + 4];
	startPoint[0] = 1.2;
	startPoint[1] = 1.3;
	startPoint[2] = 2.4;
	startPoint[3] = 2.6;
	for(int i = 4; i < startPoint.length; i++)
	    startPoint[i] = 1;
	
	LeastSquaresOptimizer optimizer = new LevenbergMarquardtOptimizer();//new GaussNewtonOptimizer(Decomposition.QR);
	
	LeastSquaresBuilder builder = new LeastSquaresBuilder();
	builder.model(jacobianNetFunction);
	builder.start(startPoint);
	builder.target(jacobianNetFunction.results);
	builder.maxIterations(100);
	builder.maxEvaluations(100);
	builder.checker(new EvaluationRmsChecker(0.001));
	builder.parameterValidator(jacobianNetFunction);
	
	
	if(delayModel instanceof LinearDelayModel)
	    System.out.println("USING LINEAR DELAY MODEL!");
	else if(delayModel instanceof ElmoreDelayModel)	
	    System.out.println("USING ELMORE DELAY MODEL!");
	
	LeastSquaresProblem problem= builder.build();
	Optimum optimum = optimizer.optimize(problem);
	
	RealVector point = optimum.getPoint();
	Map<WireDimension, Double> wireFactors = new EnumMap<>(WireDimension.class);
	WireDimension[] dim = WireDimension.values();
	
	for(int i = 0; i < dim.length; i++)
	{
	    wireFactors.put(dim[i], point.getEntry(i+4));
	}
	
	TimingCalibration result = new TimingCalibration(new ArrayList<>(), point.getEntry(0), point.getEntry(1), point.getEntry(2), point.getEntry(3), delayModel.getClass().toString(), wireFactors);

	
	System.out.println("Optimization Result equals: ");
	System.out.println(optimum.getPoint().toString());        
	System.out.println("The RMS equals: " + optimum.getRMS());
	System.out.println("Residual: " + optimum.getResiduals().toString());
	System.out.println("Number of Iterations: " + optimum.getIterations());
	System.out.println("Number of Evaluations: " + optimum.getEvaluations());
	
	return result;
	
	
    }

}
