package edu.byu.ece.rapidSmith.timing.logic;

import org.apache.commons.math3.fitting.leastsquares.*;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Created by jakobw on 03.07.15.
 */
public class LogicTimingSolver implements MultivariateJacobianFunction,ParameterValidator {
	private static final Logger logger = LoggerFactory.getLogger(LogicTimingSolver.class);


    private final LogicTimingAnalyzer analyzer;
    Map<String,Integer> variableIndices = new HashMap<>();
    private final Map<LogicTimingAnalyzer.LogicDelayPath, LogicTimingAnalyzer.InstancesAndDelay> realPathsWithDelay;
    //Ordered paths
    private final List<LogicTimingAnalyzer.LogicDelayPath> paths;

    private final int[][] varsPerPath;

    private final RealMatrix jacobian;

    public LogicTimingSolver(Map<LogicTimingAnalyzer.LogicDelayPath, LogicTimingAnalyzer.InstancesAndDelay> pathDelays, LogicTimingAnalyzer analyzer) {
        this.analyzer = analyzer;

        realPathsWithDelay= new HashMap<>();

        //Creating real paths and variables
        pathDelays.forEach((combinedPath, instancesAndDelay) -> {
            Collection<LogicTimingAnalyzer.LogicDelayPath> rps = LogicTimingAnalyzer.toRealPaths(combinedPath);
            rps.forEach(path -> {
                if (instancesAndDelay.delays!=null && !instancesAndDelay.delays.isEmpty()) {
                    realPathsWithDelay.put(path,instancesAndDelay);

                    for (int i = 0; i < path.path.size()-1; i++) {
                        getIndex(getVar(path,i));
                    }
                }


            });
        });

        paths = new ArrayList<>(realPathsWithDelay.keySet());
        varsPerPath = new int[paths.size()][];

        for (int i = 0; i < paths.size(); i++) {
            LogicTimingAnalyzer.LogicDelayPath p = paths.get(i);
            varsPerPath[i] = new int[p.path.size()-1];
            for (int j = 0; j<p.path.size()-1;j++) {
                varsPerPath[i][j] = getIndex(getVar(p,j));
            }
        }

        jacobian = buildJacobian();
    }

    private String getVar(LogicTimingAnalyzer.LogicDelayPath d, int i) {
        String prefix="";
        if (d.primitiveType!=null)
            prefix=d.primitiveType.toString()+"_";
        return prefix+d.path.get(i)+"_"+d.path.get(i+1);
    }

    private int getIndex(String var) {
        Integer index = variableIndices.get(var);
        if (index==null) {
            index = variableIndices.size();
            variableIndices.put(var,index);
        }
        return index;
    }

    private RealMatrix buildJacobian() {

        double[][] res = new double[paths.size()][variableIndices.size()];
        int[] row = {0};
        paths.forEach(logicDelayPath -> {
           for (int i=0;i<logicDelayPath.path.size()-1;i++) {
               res[row[0]][getIndex(getVar(logicDelayPath,i))] = 1;
           }
            row[0]++;
        });

        return MatrixUtils.createRealMatrix(res);
    }

    @Override
    public Pair<RealVector, RealMatrix> value(RealVector point) {

        double[] res = new double[paths.size()];
        for (int i=0;i<paths.size();i++) {
            double delay = 0;
            for (int j=0;j<varsPerPath[i].length;j++)
                delay+=point.getEntry(varsPerPath[i][j]);
            res[i]=delay;
        }
        ArrayRealVector resVec = new ArrayRealVector(res, false);
        return new Pair<>(resVec,jacobian);
    }

    public List<LogicDelay> solve() {
        LeastSquaresOptimizer optimizer = new GaussNewtonOptimizer(GaussNewtonOptimizer.Decomposition.SVD);
        //LeastSquaresOptimizer optimizer = new LevenbergMarquardtOptimizer();

        LeastSquaresBuilder builder = new LeastSquaresBuilder();
        builder.model(this);
        builder.start(new double[variableIndices.size()]);
        builder.target(delayVector());
        builder.parameterValidator(this);
        builder.maxIterations(1000);
        builder.maxEvaluations(1000);
        builder.checker(new EvaluationRmsChecker(1));

        LeastSquaresProblem problem = builder.build();
        logger.info("Starting solver");
        LeastSquaresOptimizer.Optimum optimum = optimizer.optimize(problem);
        logger.info("Optimization Result equals: ");
        variableIndices.forEach((s, integer) -> {
            double val = optimum.getPoint().getEntry(integer);
            //if (val<0 || Double.isInfinite(val))
                logger.info(s + ": " + val);
        });

        double[] sqrError = {0};

        List<LogicDelay> logicDelays = new ArrayList<>();
        realPathsWithDelay.forEach((logicDelayPath, instancesAndDelay) -> {
            double delay=0;
            for (int i = 0; i < logicDelayPath.path.size()-1; i++) {

                String first = logicDelayPath.path.get(i);
                String second = logicDelayPath.path.get(i+1);

                double elemDelay = optimum.getPoint().getEntry(getIndex(getVar(logicDelayPath, i)));

                LogicDelay d = new LogicDelay(logicDelayPath.primitiveType,first,second,elemDelay);
                logicDelays.add(d);

                delay+=elemDelay;
            }
            //System.out.println(instancesAndDelay.delay+" vs "+delay);
            double err = analyzer.averageStrategy.strategy.apply(instancesAndDelay.delays)-delay;
            sqrError[0]+=err*err;


        });
        logger.info("Summed error: "+Math.sqrt(sqrError[0]));
        return logicDelays;
    }

    private double[] delayVector() {
        double[] res = new double[paths.size()];
        for (int i = 0; i < res.length; i++) {
            res[i] = analyzer.averageStrategy.strategy.apply(realPathsWithDelay.get(paths.get(i)).delays);
        }
        return res;
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
