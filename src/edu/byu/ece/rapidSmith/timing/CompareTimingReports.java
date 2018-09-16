package edu.byu.ece.rapidSmith.timing;

import edu.byu.ece.rapidSmith.design.Attribute;
import edu.byu.ece.rapidSmith.design.Instance;
import edu.byu.ece.rapidSmith.design.Pin;
import edu.byu.ece.rapidSmith.primitiveDefs.PrimitiveDefDetails;
import edu.byu.ece.rapidSmith.primitiveDefs.PrimitiveDefList;
import edu.byu.ece.rapidSmith.router.InstanceElement;
import edu.byu.ece.rapidSmith.timing.logic.LogicPathElement;
import edu.byu.ece.rapidSmith.timing.routing.DelayModel;
import edu.byu.ece.rapidSmith.util.DeviceDatabaseProvider;
import edu.byu.ece.rapidSmith.util.FileTools;
import edu.byu.ece.rapidSmith.util.SetHelpers;
import edu.byu.ece.rapidSmith.device.database.FileDeviceDatabase;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by jakobw on 04.08.15.
 */
public class CompareTimingReports {

    public static InstanceElement findNamedElementInInstance(PrimitiveDefList primitives, Instance instance,String name) {
        for (Attribute attr : instance.getAttributes()) {
            //names in IOBs sometimes have .PAD appended...
            if (attr.getLogicalName().equals(name)
                    || (instance.isIOB() && attr.getLogicalName().equals(name+".PAD"))) {
                InstanceElement res = instance.getInstanceElement(primitives.getPrimitiveDef(instance.getType()).getElement(attr.getPhysicalName()), primitives);
                if (res==null)
                    System.err.println("Did not find element for physical name "+attr.getPhysicalName());
                return res;
            }
        }
        System.err.println("did not find element for logical name " + name + " in " + instance);
        return null;
    }

    private static PathDelay getNonClockPart(PathDelay in, PrimitiveDefList primitives) {
        int tckoPos = -1;
        int tasPos = -1;
        for (int i=0;i<in.getMaxDataPath().size();i++) {
            String type = in.getMaxDataPath().get(i).getType();
            if (type.equals("Tcko") || type.equals("Tshcko")) {
                tckoPos=i;
            }
            if (type.equals("Tas")) {
                tasPos=i;
            }
        }
        if (tasPos>=0 && tckoPos>=0) {
            throw new RuntimeException("found both Tcko and Tas in "+in);
        }

        if (tckoPos==-1 && tasPos==-1) {
            throw new RuntimeException("could not find Tcko or Tas in "+in);
        }

        List<PathElement> filteredDelays;

        //if we found tcko, skip everything before
        if (tckoPos>=0)
            filteredDelays = in.getMaxDataPath().stream().skip(tckoPos).collect(Collectors.toList());
        else //if we found tas, skip everything after
            filteredDelays = in.getMaxDataPath().stream().limit(tasPos + 1).collect(Collectors.toList());

        PathDelay res = new PathDelay();


        if (tasPos>=0)
            res.setSource(filteredDelays.get(0).getPin().getInstance().getInstanceElement("PAD",primitives).getAttribute().getLogicalName());
        else
            res.setSource(in.getSource());

        res.setDestination(in.getDestination());
        res.setLevelsOfLogic(in.getLevelsOfLogic());
        res.setDestinationClock(in.getDestinationClock());
        res.setClockUncertainty(in.getClockUncertainty());
        res.setClockPathSkew(in.getClockPathSkew());
        res.setSourceClock(in.getSourceClock());
        res.setSlack(in.getSlack());
        res.setDelayConstraint(in.getDelayConstraint());

        res.setMaxDataPath(filteredDelays);

        float dataPathDelay = (float) filteredDelays.stream().filter(elem -> elem instanceof LogicPathElement).mapToDouble(PathElement::getDelay).sum();
        float routingDelay  = (float) filteredDelays.stream().filter(elem -> elem instanceof RoutingPathElement).mapToDouble(PathElement::getDelay).sum();


        res.setDataPathDelay(dataPathDelay);
        res.setRoutingDelay(routingDelay);

        //TODO skew, uncertainty?
        res.setDelay(dataPathDelay+routingDelay);

        return res;

    }

    public static void comparePathTiming(PathDelay origDelay, PrimitiveDefList primitives, TimingCalculator calculator, PrintWriter totalWriter, PrintWriter logicWriter, PrintWriter routingWriter, PrintWriter logicSumWriter, PrintWriter routingSumWriter) {


        //Delays from reg to iob include clock path stuff. we don't want that
        PathDelay delay;
        if (origDelay.getMaxDataPath().get(0).getType().equals("Tiopi")) {
            delay = getNonClockPart(origDelay, primitives);
        } else delay = origDelay;

        InstanceElement source = findNamedElementInInstance(primitives,delay.getMaxDataPath().get(0).getPin().getInstance(), delay.getSource());
        InstanceElement dest = findNamedElementInInstance(primitives, delay.getMaxDataPath().get(delay.getMaxDataPath().size() - 1).getPin().getInstance(), delay.getDestination());


        if (source==null || dest==null) {
            System.out.println(SetHelpers.dumpVs(origDelay.toString(),delay.toString()));
            System.err.println("source = " + source);
            System.err.println("dest = " + dest);
            System.err.println();
            System.err.println();
            return;
        }


        if (source.isClock() || dest.isClock())
            System.out.println("Clock for "+delay.getSource()+" to "+delay.getDestination());

        Pin.broken = false;


        Optional<PathDelay> oursOpt = calculator.getPathTimingNew(source, dest);
        if (!oursOpt.isPresent()) {
            System.out.println("no path found from "+delay.getSource()+" to "+delay.getDestination());
            return;
        }

        PathDelay ours = oursOpt.get();

        if (delay.getMaxDataPath().size() != ours.getMaxDataPath().size()) {

        } else if (Pin.broken) {
            System.out.println("Missing routing path");
        } else {

            logCompare(totalWriter, delay.getDelay(), ours.getDelay(), null);


            logCompare(logicSumWriter, delay.getDataPathDelay(), ours.getDataPathDelay(), null);
            logCompare(routingSumWriter, delay.getRoutingDelay(), ours.getRoutingDelay(), null);
            for (int i = 0; i < ours.getMaxDataPath().size(); i++) {

                PathElement xilElement = delay.getMaxDataPath().get(i);
                PathElement ourElement = ours.getMaxDataPath().get(i);

                if (ourElement instanceof LogicPathElement) {
                    logCompare(logicWriter, xilElement.getDelay(), ourElement.getDelay(), ourElement);
                } else
                    logCompare(routingWriter, xilElement.getDelay(), ourElement.getDelay(), ourElement);
            }
        }
    }

    private static void logCompare(PrintWriter w, float xilinx, float ours, PathElement ourElement) {
        /*if (ours > 10000)
            return;*/

        float error = Math.abs(ours-xilinx)/xilinx;

        String name = "";
        if (ourElement!=null) {
            name = ourElement.getLogicalName();
        }


        w.println(String.format(Locale.US,"%.3f\t%.3f\t%.3f #%s",xilinx,ours,error,name));
        //w.flush();
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

        CalibrateTiming.Testcase[] testcases = new CalibrateTiming.Testcase[] {
                new CalibrateTiming.Testcase("testdaten/carsten/carsten.xdl","testdaten/carsten/carsten5000.twx"),
                //new CalibrateTiming.Testcase("testdaten/dvi/dvi.xdl","testdaten/dvi/dvi.twx"),
                //new CalibrateTiming.Testcase("testdaten/fsm/fsm_using_single_always.xdl","testdaten/fsm/fsm_using_single_always_with_iob2.twx"),
                //new CalibrateTiming.Testcase("testdaten/pwm/pwm.xdl","testdaten/pwm/pwm.twx"),
                //new CalibrateTiming.Testcase("testdaten/clockabuse/blah.xdl","testdaten/clockabuse/blah.twx")
        };

        List<TimingCalibration.CalibData> calibDatas = Arrays.stream(testcases).map(tc -> CalibrateTiming.loadTestcase(tc, directory)).collect(Collectors.toList());

        List<String> partNames = calibDatas.stream().map(calibData -> calibData.design.getPartName()).distinct().collect(Collectors.toList());

        if (partNames.size()>1) {
            throw new RuntimeException("Trying to calibrate for multiple parts: "+partNames);
        }

        PrimitiveDefList primitiveDefs = DeviceDatabaseProvider.getDeviceDatabase().loadPrimitiveDefs(partNames.get(0));
        PrimitiveDefDetails.insertDetails(primitiveDefs);
        PrimitiveDefDetails.checkConsistency(primitiveDefs);


        TimingCalibration cal = TimingCalibration.loadFromFile(new File("calibration.cal"));

        cal.enterToPrimitiveDefs(primitiveDefs);

        DelayModel delayModel = cal.createDelayModel();



        for (TimingCalibration.CalibData calibData : calibDatas) {

            String name = calibData.name;
            System.out.println("Analyzing " + name);

            TimingCalculator calc = new TimingCalculator(primitiveDefs, delayModel, calibData.design, false);

            PrintWriter totalWriter = new PrintWriter(new FileWriter("dumps/" + name + "-total.dat"));
            PrintWriter logicWriter = new PrintWriter(new FileWriter("dumps/" + name + "-logicSingle.dat"));
            PrintWriter routingWriter = new PrintWriter(new FileWriter("dumps/" + name + "-routingSingle.dat"));
            PrintWriter logicSumWriter = new PrintWriter(new FileWriter("dumps/" + name + "-logicSum.dat"));
            PrintWriter routingSumWriter = new PrintWriter(new FileWriter("dumps/" + name + "-routingSum.dat"));

            calc.buildReachables();

            try {
                calibData.delays.parallelStream().forEach(pathDelay -> {
                    comparePathTiming(pathDelay, primitiveDefs, calc, totalWriter, logicWriter, routingWriter, logicSumWriter, routingSumWriter);
                    //System.out.println("analyzed");
                });
            } finally {
                totalWriter.close();
                logicWriter.close();
                routingWriter.close();
            }
        }
    }
}
