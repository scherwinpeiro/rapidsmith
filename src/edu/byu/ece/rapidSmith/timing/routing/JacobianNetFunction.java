package edu.byu.ece.rapidSmith.timing.routing;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.fitting.leastsquares.MultivariateJacobianFunction;
import org.apache.commons.math3.fitting.leastsquares.ParameterValidator;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.BlockRealMatrix;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.util.Pair;

public class JacobianNetFunction implements MultivariateJacobianFunction, ParameterValidator {
    
    private List<NetFunction> functions;
    public RealVector results;
    private RealMatrix jacobian;
    
    public JacobianNetFunction()
    {
	functions = new ArrayList<>();
	results = new ArrayRealVector();
    }
    
    /**
     * Inserts a map that represents a net function of type y = a*RpCp+b*RpCw+c*RwCp+d*RwCw
     * @param delayFunction Function of type y = a*RpCp+b*RpCw+c*RwCp+d*RwCw
     * @param result the result calculated by Xilinx
     * @return true if successfully
     */
    public boolean putFunction(NetFunction delayFunction, double result)
    {
	boolean b1 = functions.add(delayFunction);
	if(b1)
	    results = results.append(result);
	return b1;
    }
    
    private RealVector evaluatePoint(RealVector point)
    {
	WireDimension[] values = WireDimension.values();
	if(point.getDimension() != values.length+4)
	{
	    System.out.println("MaxIndex: "+ point.getDimension());
	    return null;
	}
	    
	    
	RealVector retVal = new ArrayRealVector();
	
	Map<RCType, Double> typeValues= new EnumMap<>(RCType.class);
	typeValues.put(RCType.RpCp, point.getEntry(0));
	typeValues.put(RCType.RpCw, point.getEntry(1));
	typeValues.put(RCType.RwCp, point.getEntry(2));
	typeValues.put(RCType.RwCw, point.getEntry(3));
	
	Map<WireDimension, Double> wireValues = new EnumMap<>(WireDimension.class);
	WireDimension[] dimensions = WireDimension.values();
	for(int i = 0; i < dimensions.length; i++)
	{
	    wireValues.put(dimensions[i], point.getEntry(i+4));
	}
	
	for (NetFunction func : functions) {
	    
	    double result = func.calculatePoint(typeValues, wireValues);
	    retVal = retVal.append(result);
	}
	
	return retVal;
    }
    
    /**
     * Evaluates the jacobi matrix for the given functions. Has to be called one time after entering all functions
     */
    private RealMatrix evaluateMatrix(RealVector point)
    {
	RealMatrix retVal = MatrixUtils.createRealMatrix(functions.size(), point.getDimension());
	
	Map<RCType, Double> typeValues= new EnumMap<>(RCType.class);
	typeValues.put(RCType.RpCp, point.getEntry(0));
	typeValues.put(RCType.RpCw, point.getEntry(1));
	typeValues.put(RCType.RwCp, point.getEntry(2));
	typeValues.put(RCType.RwCw, point.getEntry(3));
	
	Map<WireDimension, Double> wireValues = new EnumMap<>(WireDimension.class);
	WireDimension[] dimensions = WireDimension.values();
	for(int i = 0; i < dimensions.length; i++)
	{
	    wireValues.put(dimensions[i], point.getEntry(i+4));
	}
	
	for(int i = 0; i < functions.size(); i++)
	{
	    NetFunction func = functions.get(i); 
	    double[] row = func.calculateJacobianRow(typeValues, wireValues);
	    retVal.setRow(i, row);
	}
	
	jacobian = retVal;
	
	return retVal;
    }

    @Override
    /**
     * Order: RpCp, RpCw, RwCp, RwCw
     */
    public Pair<RealVector, RealMatrix> value(RealVector point) {
	
	RealVector result = evaluatePoint(point);
	//if(jacobian == null ||jacobian.getRowDimension() != functions.size())
	RealMatrix matrix = evaluateMatrix(point);
	
	Pair<RealVector, RealMatrix> retVal = new Pair<>(result, matrix);
	return retVal;
    }

    @Override
    public RealVector validate(RealVector params) {
	double[] res = new double[params.getDimension()];
        for (int i = 0; i < res.length; i++) {
            double val = params.getEntry(i);
            if (val<0)
                val = 0;
            res[i] = val;

        }
        return new ArrayRealVector(res,false);
    }

}
