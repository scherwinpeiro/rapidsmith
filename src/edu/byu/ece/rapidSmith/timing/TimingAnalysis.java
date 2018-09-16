package edu.byu.ece.rapidSmith.timing;

import edu.byu.ece.rapidSmith.design.Design;
import edu.byu.ece.rapidSmith.primitiveDefs.PrimitiveDefDetails;
import edu.byu.ece.rapidSmith.primitiveDefs.PrimitiveDefList;
import edu.byu.ece.rapidSmith.router.RoutingElement;
import edu.byu.ece.rapidSmith.timing.logic.IsolatedDelayDesignCreator;
import edu.byu.ece.rapidSmith.timing.routing.DelayModel;
import edu.byu.ece.rapidSmith.util.DeviceDatabaseProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Paths;

/**
 * Created by jakobw on 04.08.15.
 */
public class TimingAnalysis {
	private static boolean createIsolatedDesign = false;

	private static final Logger logger = LoggerFactory.getLogger(TimingAnalysis.class);
    public static void main(String[] args) throws IOException, ClassNotFoundException {


        //final String directory = FileTools.getRapidSmithPath()+FileTools.getDirectorySeparator();



        /*CalibrateTiming.Testcase[] testcases = new CalibrateTiming.Testcase[] {
                new CalibrateTiming.Testcase("testdaten/carsten/carsten.xdl","testdaten/carsten/carsten5000.twx"),
                new CalibrateTiming.Testcase("testdaten/fsm/fsm_using_single_always.xdl","testdaten/fsm/fsm_using_single_always.twx"),
                new CalibrateTiming.Testcase("testdaten/pwm/pwm.xdl","testdaten/pwm/pwm.twx"),
                new CalibrateTiming.Testcase("testdaten/dvi/dvi.xdl","testdaten/dvi/dvi.twx"),
                new CalibrateTiming.Testcase("testdaten/clockabuse/blah.xdl","testdaten/clockabuse/blah.twx")
        };
        List<TimingCalibration.CalibData> calibDatas = Arrays.stream(testcases).map(tc -> CalibrateTiming.loadTestcase(tc, directory)).collect(Collectors.toList());

        List<String> partNames = calibDatas.stream().map(calibData -> calibData.design.getPartName()).distinct().collect(Collectors.toList());

        if (partNames.size()>1) {
            throw new RuntimeException("Trying to calibrate for multiple parts: "+partNames);
        }*/

        //PrimitiveDefList primitiveDefs = FileTools.loadPrimitiveDefs(partNames.get(0));

		Design design = new Design(Paths.get(args[0]));

		PrimitiveDefList primitiveDefs = DeviceDatabaseProvider.getDeviceDatabase().loadPrimitiveDefs(design.getPartName());
		PrimitiveDefDetails.insertDetails(primitiveDefs);
        PrimitiveDefDetails.checkConsistency(primitiveDefs);


        TimingCalibration cal = TimingCalibration.loadFromFile(new File(args[1]));

        cal.enterToPrimitiveDefs(primitiveDefs);

        DelayModel delayModel = cal.createDelayModel();

		IsolatedDelayDesignCreator isolatedDelayDesignCreator = new IsolatedDelayDesignCreator(design, primitiveDefs, Paths.get("isolatedDelays"));

        //for (TimingCalibration.CalibData calibData : calibDatas) {

//            String name = calibData.name;
//            System.out.println("Analyzing " + name);

            TimingCalculator calc = new TimingCalculator(primitiveDefs, delayModel, design, false);


            calc.init();



            calc.calculateTimings((x,y)->{
            	logger.warn("Unknown delay from {} to {}", x,y);
            	if (createIsolatedDesign) {
					try {
						DotDumper.dumpInstanceOnce(x.getInstance(), primitiveDefs);
						isolatedDelayDesignCreator.saveIsolatedDesignOnce(x.getInstance());
					} catch (FileNotFoundException e) {
						e.printStackTrace();
					}
				}
				/*final InstanceElement ie = x.getInstance().getInstanceElement("A6", primitiveDefs);
				System.out.println(ie);
				System.out.println("ie.isRegister(primitiveDefs) = " + ie.isRegister(primitiveDefs));
				System.out.println("ie.isDisabled() = " + ie.isDisabled());
				final RoutingElement prev = ie.getConnectedBackward(primitiveDefs).stream().iterator().next();
				System.out.println(((Pin) prev).getNet().getType());*/
			});

            calc.getCriticalPaths(1E-6).forEach(System.out::println);

		final float delay = calc.getCriticalPaths(1E-6).findAny().orElseThrow(() -> new RuntimeException("No paths")).getDelay();
		System.out.println("Max frequency: " + 1000/delay+ "Mhz");

		//TimingStuff.dumpConnections(calibData.design,primitiveDefs,new File("dumps/design.dot"), TimingStuff.Direction.FORWARD,calc.getMaxDelay(),1E-6,delayModel);



        //}
    }

    private static void findClockSource(RoutingElement elem, String path, PrimitiveDefList primitives) {
        if (elem.isRegister(primitives)== RoutingElement.RegisterType.REGISTER) {
            //System.out.println("found reg: " + path);
            return;
        }
        boolean hasClockNexts = false;
        for (RoutingElement next : elem.getConnectedForward(primitives)) {
            if (!next.isClock()) {
                continue;
            }
            hasClockNexts=true;
            String nextName = next.toString().replace('\n','_');
            if (path.contains(nextName+"->"))
                return;
            String pathWithNext = path+"->"+nextName;
            findClockSource(next,pathWithNext,primitives);
        }
        if (!hasClockNexts)
            System.out.println("found path: "+path);
    }
}
