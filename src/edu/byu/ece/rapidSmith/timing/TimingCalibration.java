package edu.byu.ece.rapidSmith.timing;

import edu.byu.ece.rapidSmith.design.Design;
import edu.byu.ece.rapidSmith.design.Instance;
import edu.byu.ece.rapidSmith.design.Net;
import edu.byu.ece.rapidSmith.primitiveDefs.PrimitiveDef;
import edu.byu.ece.rapidSmith.primitiveDefs.PrimitiveDefList;
import edu.byu.ece.rapidSmith.timing.logic.LogicDelay;
import edu.byu.ece.rapidSmith.timing.logic.LogicTimingAnalyzer;
import edu.byu.ece.rapidSmith.timing.logic.LogicTimingSolver;
import edu.byu.ece.rapidSmith.timing.routing.DelayModel;
import edu.byu.ece.rapidSmith.timing.routing.NetTimingAnalyzer;
import edu.byu.ece.rapidSmith.timing.routing.WireDimension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by jakobw on 10.07.15.
 */
public class TimingCalibration implements Serializable {

	private static final Logger logger = LoggerFactory.getLogger(TimingCalibration.class);

	private static final long serialVersionUID = 8013088286739533884L;


	public static class CalibData {

		public final File input;
		public final Design design;
		public final List<PathDelay> delays;
		public final String name;

		public CalibData(File input, Design design, List<PathDelay> delays, String name) {
			this.input = input;
			this.design = design;
			this.delays = delays;
			this.name = name;
		}
	}


	final public List<LogicDelay> logicDelays;
	final public double RpCp;
	final public double RpCw;
	final public double RwCp;
	final public double RwCw;
	final public String delayModel;
	final public Map<WireDimension, Double> wireFactors;

	public TimingCalibration(List<LogicDelay> logicDelays, double rpCp, double rpCw, double rwCp, double rwCw, String delayModel, Map<WireDimension, Double> wireFactors) {
		this.logicDelays = logicDelays;
		RpCp = rpCp;
		RpCw = rpCw;
		RwCp = rwCp;
		RwCw = rwCw;
		this.delayModel = delayModel;
		this.wireFactors = wireFactors;
	}

	public TimingCalibration withNewLogicDelays(List<LogicDelay> logicDelays) {
		return new TimingCalibration(logicDelays, this.RpCp, this.RpCw, this.RwCp, this.RwCw, this.delayModel, this.wireFactors);
	}


	public void enterToPrimitiveDefs(PrimitiveDefList primitiveDefs) {
		logicDelays.forEach(logicDelay -> {
			if (logicDelay.type != null) {
				PrimitiveDef prim = primitiveDefs.getPrimitiveDef(logicDelay.type);
				if (!logicDelay.enterToPrimitiveDef(prim)) {
					throw new RuntimeException("Could not enter delay " + logicDelay + " into primitive!");
				}
			} else {
				boolean found = false;
				for (PrimitiveDef prim : primitiveDefs) {
					boolean f = logicDelay.enterToPrimitiveDef(prim);
					if (f) {
						found = true;
					}
				}
				if (!found) {
					System.err.println("Did not find any primitives to enter delay " + logicDelay);
				}
			}
		});
	}

	public static List<LogicDelay> calibrateLogic(Collection<CalibData> calibDatas, LogicTimingAnalyzer timingAnalyzer) {

		Map<LogicTimingAnalyzer.LogicDelayPath, LogicTimingAnalyzer.InstancesAndDelay> timings = new HashMap<>();
		for (CalibData calibData : calibDatas) {
			logger.info("handling {}", calibData.input);
			Map<LogicTimingAnalyzer.LogicDelayPath, Set<Instance>> paths = timingAnalyzer.findPaths(calibData.design);
			Map<LogicTimingAnalyzer.LogicDelayPath, LogicTimingAnalyzer.InstancesAndDelay> designTimings = timingAnalyzer.getPathTimings(calibData.design, paths, calibData.delays);

			for (LogicTimingAnalyzer.LogicDelayPath logicDelayPath : designTimings.keySet()) {
				LogicTimingAnalyzer.InstancesAndDelay existing = timings.get(logicDelayPath);
				LogicTimingAnalyzer.InstancesAndDelay newData = designTimings.get(logicDelayPath);
				if (existing == null) {
					timings.put(logicDelayPath, newData);
				} else {
					timings.put(logicDelayPath, mergeInstancesAndDelays(existing, newData));
				}
			}
		}
//		timings.forEach((path, instDelays) -> {
//			logger.info(path.toString());
//			logger.info(instDelays.instances.stream().map(Instance::getName).collect(Collectors.joining(",")));
//			if (instDelays.delays == null) {
//				logger.warn("Delays are null");
//			} else {
//				logger.info(instDelays.delays.toString());
//			}
//		});
		LogicTimingSolver solver = new LogicTimingSolver(timings, timingAnalyzer);

		return solver.solve();
	}

	private static LogicTimingAnalyzer.InstancesAndDelay mergeInstancesAndDelays(LogicTimingAnalyzer.InstancesAndDelay existing, LogicTimingAnalyzer.InstancesAndDelay newData) {

		Set<Instance> instances = new HashSet<>(existing.instances);
		instances.addAll(newData.instances);

		List<Float> delays;
		if (existing.delays != null) {
			delays = new ArrayList<>(existing.delays);
			if (newData.delays != null) {
				delays.addAll(newData.delays);
			}
		} else {
			delays = newData.delays;
		}

		return new LogicTimingAnalyzer.InstancesAndDelay(instances, delays);
	}

	private static TimingCalibration calibrateRouting(Collection<CalibData> calibDatas, DelayModel delayModel) {
		List<Net> netList = new ArrayList<>();
		List<PathDelay> pathDelayList = new ArrayList<>();

		for (CalibData data : calibDatas) {
			netList.addAll(data.design.getNets());
			pathDelayList.addAll(data.delays);
		}

		return NetTimingAnalyzer.analyzeNetTiming(netList, pathDelayList, delayModel);
		//return NetTimingAnalyzer.consitencyTest(netList, delayModel);
	}

	public static TimingCalibration calibrate(Collection<CalibData> calibDatas, DelayModel delayModel, LogicTimingAnalyzer timingAnalyzer) {
		List<LogicDelay> logicDelays = calibrateLogic(calibDatas, timingAnalyzer);
		TimingCalibration routing = calibrateRouting(calibDatas, delayModel);

		return new TimingCalibration(logicDelays, routing.RpCp, routing.RpCw, routing.RwCp, routing.RwCw, delayModel.getClass().getName(), routing.wireFactors);
	}

	public static TimingCalibration loadFromFile(File file) throws IOException, ClassNotFoundException {

		ObjectInputStream fs = new ObjectInputStream(new FileInputStream(file));
		try {

			return (TimingCalibration) fs.readObject();

		} finally {
			fs.close();
		}
	}

	public void saveToFile(File file) throws IOException {
		ObjectOutputStream fs = new ObjectOutputStream(new FileOutputStream(file));
		try {
			fs.writeObject(this);
		} finally {
			fs.close();
		}
	}

	public DelayModel createDelayModel() {
		return DelayModel.fromTimingCalibration(this);
	}
}
