package edu.byu.ece.rapidSmith.timing;

import edu.byu.ece.rapidSmith.design.Design;
import edu.byu.ece.rapidSmith.primitiveDefs.PrimitiveDefDetails;
import edu.byu.ece.rapidSmith.primitiveDefs.PrimitiveDefList;
import edu.byu.ece.rapidSmith.timing.logic.AverageStrategy;
import edu.byu.ece.rapidSmith.timing.logic.LogicDelay;
import edu.byu.ece.rapidSmith.timing.logic.LogicTimingAnalyzer;
import edu.byu.ece.rapidSmith.timing.routing.LinearDelayModel;
import edu.byu.ece.rapidSmith.util.DeviceDatabaseProvider;
import edu.byu.ece.rapidSmith.util.FileTools;
import edu.byu.ece.rapidSmith.device.database.FileDeviceDatabase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by jakobw on 02.07.15.
 */
public class CalibrateTiming {
	private static final Logger logger = LoggerFactory.getLogger(CalibrateTiming.class);

	public static class Testcase {

		public final String xdl;
		public final String twx;

		public Testcase(String xdl, String twx) {
			this.xdl = xdl;
			this.twx = twx;
		}
	}


	public static TimingCalibration.CalibData loadTestcase(Testcase tc, String directory) {
		logger.info("Loading testcase "+tc.xdl);
		Design design = new Design();
		design.loadXDLFile(Paths.get(directory + tc.xdl));

		File f = new File(directory + tc.xdl);
		String name = f.getName().split("\\.")[0];

		TraceReportXmlParser p = new TraceReportXmlParser();
		p.parseTWX(directory + tc.twx, design);

		return new TimingCalibration.CalibData(f, design, p.getPathDelays(), name);
	}

	public static void main(String[] args) throws IOException, ClassNotFoundException {


        /*System.setErr(new DebugStream(System.err));
		System.setOut(new DebugStream(System.out));*/

		final String directory = FileDeviceDatabase.getRapidSmithPath() + FileTools.getDirectorySeparator();


		Stream<Testcase> explicit = Stream.of(
				new Testcase("testdaten/pwm/pwm.xdl", "testdaten/pwm/pwm.twx"),
				new Testcase("testdaten/carsten/carsten.xdl", "testdaten/carsten/carsten5000.twx"),
				new Testcase("testdaten/fsm/fsm_using_single_always.xdl", "testdaten/fsm/fsm_using_single_always_with_iob2.twx"),
				new Testcase("testdaten/dvi/dvi.xdl", "testdaten/dvi/dvi.twx"),
				new Testcase("testdesigns/distributedram/distributedram.xdl", "testdesigns/distributedram/distributedram.twx"),
				new Testcase("testdesigns/regularluts/regularluts.xdl", "testdesigns/regularluts/regularluts.twx"),
				new Testcase("testdesigns/iobs/iobs.xdl", "testdesigns/iobs/iobs.twx")
//				new Testcase("testdaten/isolated/spartanmc_0_exmem_multiplier.xdl", "testdaten/isolated/spartanmc_0_exmem_multiplier.twx")
//				new Testcase("testdaten/isolated/spartanmc_0_uart_light_0_word_rx_0_.xdl", "testdaten/isolated/spartanmc_0_uart_light_0_word_rx_0_.twx")
		);

		final Stream<Testcase> isolated = Files.list(Paths.get("testdaten/isolated")).filter(p -> p.toString().endsWith("xdl"))
				.map(p -> new Testcase(p.toString(), p.toString().replace(".xdl", ".twx")));

//		Stream<Testcase> testcases = explicit;
		Stream<Testcase> testcases = Stream.concat( isolated, explicit);

		List<TimingCalibration.CalibData> calibDatas = testcases.map(tc -> loadTestcase(tc, directory)).collect(Collectors.toList());



		List<String> partNames = calibDatas.stream().map(calibData -> calibData.design.getPartName()).distinct().collect(Collectors.toList());

		//TODO when should we allow calibrating for multiple parts?
/*
		if (partNames.size() > 1) {
			throw new RuntimeException("Trying to calibrate for multiple parts: " + partNames);
		}*/

		PrimitiveDefList primitiveDefs = DeviceDatabaseProvider.getDeviceDatabase().loadPrimitiveDefs(partNames.get(0));
		PrimitiveDefDetails.insertDetails(primitiveDefs);
		PrimitiveDefDetails.checkConsistency(primitiveDefs);


		LogicTimingAnalyzer analyzer = new LogicTimingAnalyzer(primitiveDefs);
		analyzer.combineSameName = false;
		analyzer.combineLUTs = false;
		analyzer.combineNumberedElements = false;
		analyzer.averageStrategy = AverageStrategy.ARITHMETIC_MEAN;


		//TODO set this by cmd line switch
		if (true) {
			TimingCalibration cal = TimingCalibration.loadFromFile(new File("calibration.cal"));

			final List<LogicDelay> logic = TimingCalibration.calibrateLogic(calibDatas, analyzer);
			TimingCalibration refined = cal.withNewLogicDelays(logic);

			refined.saveToFile(new File("calibrationRefined.cal"));
			refined.enterToPrimitiveDefs(primitiveDefs);
		} else {
			//TimingCalibration cal = TimingCalibration.calibrate(calibDatas, new ElmoreDelayModel(), analyzer);
			TimingCalibration cal = TimingCalibration.calibrate(calibDatas, new LinearDelayModel(), analyzer);


			cal.saveToFile(new File("calibrationRefined.cal"));
			cal.enterToPrimitiveDefs(primitiveDefs);
		}

	}


}
