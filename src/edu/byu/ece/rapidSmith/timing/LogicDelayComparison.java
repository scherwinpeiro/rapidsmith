package edu.byu.ece.rapidSmith.timing;

import edu.byu.ece.rapidSmith.primitiveDefs.PrimitiveDefDetails;
import edu.byu.ece.rapidSmith.primitiveDefs.PrimitiveDefList;
import edu.byu.ece.rapidSmith.timing.logic.AverageStrategy;
import edu.byu.ece.rapidSmith.timing.logic.LogicTimingAnalyzer;
import edu.byu.ece.rapidSmith.timing.routing.DelayModel;
import edu.byu.ece.rapidSmith.util.DeviceDatabaseProvider;
import edu.byu.ece.rapidSmith.util.FileTools;
import edu.byu.ece.rapidSmith.device.database.FileDeviceDatabase;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Compare different logic delay calibration options
 */
public class LogicDelayComparison {

    /**
     * Get a stream of all possible LogicTimingAnalyzer setting configurations
     * @param primitiveDefs
     * @return
     */
    private static Stream<LogicTimingAnalyzer> streamTimingAnalyzers(PrimitiveDefList primitiveDefs) {

        //to enumerate all possible combinations of the boolean options, we count up a integer and check for bits set
        final int combineNumberedElementsBit = 1;
        final int combineLUTSBit = 2;
        final int combineSameNameBit = 4;

        return Stream.of(AverageStrategy.values()).flatMap(strategy ->
            IntStream.range(0, 8).mapToObj(i -> {
                LogicTimingAnalyzer res = new LogicTimingAnalyzer(primitiveDefs);
                res = new LogicTimingAnalyzer(primitiveDefs);
                res.combineNumberedElements = (i & combineNumberedElementsBit) != 0;
                res.combineLUTs = (i & combineLUTSBit) != 0;
                res.combineSameName = (i & combineSameNameBit) != 0;
                res.averageStrategy = strategy;
                return res;
            })
        );
    }

    public static void main(String[] args) throws IOException, ClassNotFoundException {
        /*System.setErr(new DebugStream(System.err));
        System.setOut(new DebugStream(System.out));*/

        final String directory = FileDeviceDatabase.getRapidSmithPath()+FileTools.getDirectorySeparator();


        if (System.console()!=null) {
            System.out.println("Press enter to continue");
            System.console().readLine();
            System.out.println("continuing");
        }


        CalibrateTiming.Testcase[] testcases = new CalibrateTiming.Testcase[]{
                new CalibrateTiming.Testcase("testdaten/pwm/pwm.xdl", "testdaten/pwm/pwm.twx"),
                new CalibrateTiming.Testcase("testdaten/carsten/carsten.xdl", "testdaten/carsten/carsten5000.twx"),
                new CalibrateTiming.Testcase("testdaten/dvi/dvi.xdl", "testdaten/dvi/dvi.twx"),
                new CalibrateTiming.Testcase("testdaten/fsm/fsm_using_single_always.xdl", "testdaten/fsm/fsm_using_single_always_with_iob2.twx"),
                new CalibrateTiming.Testcase("testdaten/clockabuse/blah.xdl", "testdaten/clockabuse/blah.twx")
        };

        String partName = checkPartNames(testcases, directory);

        PrimitiveDefList primitiveDefs = DeviceDatabaseProvider.getDeviceDatabase().loadPrimitiveDefs(partName);
        PrimitiveDefDetails.insertDetails(primitiveDefs);
        PrimitiveDefDetails.checkConsistency(primitiveDefs);


        streamTimingAnalyzers(primitiveDefs).forEach(logicTimingAnalyzer -> {
            try {

                List<TimingCalibration.CalibData> calibDatas = Arrays.stream(testcases).map(tc -> CalibrateTiming.loadTestcase(tc, directory)).collect(Collectors.toList());

                List<String> partNames = calibDatas.stream().map(calibData -> calibData.design.getPartName()).distinct().collect(Collectors.toList());

                //Calibrate logic
                //List<LogicDelay> logicDelays = TimingCalibration.calibrateLogic(calibDatas, logicTimingAnalyzer);
                //TimingCalibration cal = TimingCalibration.calibrate(calibDatas,new LinearDelayModel(),logicTimingAnalyzer);


                //And merge with routing calibration
                /*TimingCalibration routing = TimingCalibration.loadFromFile(new File("calibration.cal"));
                TimingCalibration cal = new TimingCalibration(logicDelays, routing.RpCp, routing.RpCw, routing.RwCp, routing.RwCw, routing.delayModel, routing.wireFactors);*/

                String analyzerName = getAnalyzerName(logicTimingAnalyzer);
                File calFile = new File("calibration-"+analyzerName+".cal");
                //cal.saveToFile(calFile);
                TimingCalibration cal = TimingCalibration.loadFromFile(calFile);
                primitiveDefs.clearDelays();
                cal.enterToPrimitiveDefs(primitiveDefs);

                DelayModel delayModel = cal.createDelayModel();

                for (TimingCalibration.CalibData calibData : calibDatas) {

                    String name = calibData.name;
                    System.out.println("Analyzing " + name);

                    TimingCalculator calc = new TimingCalculator(primitiveDefs, delayModel, calibData.design, false);

                    String prefix = "dumps/"+name+"-"+analyzerName;

                    PrintWriter totalWriter = new PrintWriter(new FileWriter(prefix + "-total.dat"));
                    PrintWriter logicWriter = new PrintWriter(new FileWriter(prefix + "-logicSingle.dat"));
                    PrintWriter routingWriter = new PrintWriter(new FileWriter(prefix + "-routingSingle.dat"));
                    PrintWriter logicSumWriter = new PrintWriter(new FileWriter(prefix + "-logicSum.dat"));
                    PrintWriter routingSumWriter = new PrintWriter(new FileWriter(prefix + "-routingSum.dat"));

                    calc.buildReachables();

                    try {
                        calibData.delays.parallelStream().forEach(pathDelay -> {
                            CompareTimingReports.comparePathTiming(pathDelay, primitiveDefs, calc, totalWriter, logicWriter, routingWriter, logicSumWriter, routingSumWriter);
                            //System.out.println("analyzed");
                        });
                    } finally {
                        totalWriter.close();
                        logicWriter.close();
                        routingWriter.close();
                        logicSumWriter.close();
                        routingSumWriter.close();
                    }
                }
            } catch (IOException |ClassNotFoundException e) {
                System.err.println("Fail!!!! ");
                e.printStackTrace();
            }
        });

    }

    private static String checkPartNames(CalibrateTiming.Testcase[] testcases, String directory) {
        List<TimingCalibration.CalibData> calibDatas = Arrays.stream(testcases).map(tc -> CalibrateTiming.loadTestcase(tc, directory)).collect(Collectors.toList());

        List<String> partNames = calibDatas.stream().map(calibData -> calibData.design.getPartName()).distinct().collect(Collectors.toList());

        if (partNames.size() > 1) {
            throw new RuntimeException("Trying to calibrate for multiple parts: " + partNames);
        }
        return partNames.get(0);
    }

    private static String getAnalyzerName(LogicTimingAnalyzer logicTimingAnalyzer) {
        return    (logicTimingAnalyzer.combineNumberedElements?"1":"0")
                + (logicTimingAnalyzer.combineLUTs            ?"1":"0")
                + (logicTimingAnalyzer.combineSameName        ?"1":"0")
                + "-"+logicTimingAnalyzer.averageStrategy.toString();
    }
}
