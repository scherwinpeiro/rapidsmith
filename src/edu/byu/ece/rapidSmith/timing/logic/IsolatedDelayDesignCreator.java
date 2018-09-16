package edu.byu.ece.rapidSmith.timing.logic;

import edu.byu.ece.rapidSmith.design.Attribute;
import edu.byu.ece.rapidSmith.design.Design;
import edu.byu.ece.rapidSmith.design.Instance;
import edu.byu.ece.rapidSmith.design.Net;
import edu.byu.ece.rapidSmith.design.NetType;
import edu.byu.ece.rapidSmith.design.Pin;
import edu.byu.ece.rapidSmith.device.PrimitiveSite;
import edu.byu.ece.rapidSmith.device.PrimitiveType;
import edu.byu.ece.rapidSmith.primitiveDefs.PrimitiveDefDetails;
import edu.byu.ece.rapidSmith.primitiveDefs.PrimitiveDefList;
import edu.byu.ece.rapidSmith.router.RoutingElement;
import edu.byu.ece.rapidSmith.timing.DotDumper;
import edu.byu.ece.rapidSmith.timing.TimingCalculator;
import edu.byu.ece.rapidSmith.timing.TimingCalibration;
import edu.byu.ece.rapidSmith.timing.routing.DelayModel;
import edu.byu.ece.rapidSmith.util.DeviceDatabaseProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Create a design that has a specific logic delay path in isolation to learn its delay via Xilinx timing analysis
 */
public class IsolatedDelayDesignCreator {

	private static final Logger logger = LoggerFactory.getLogger(IsolatedDelayDesignCreator.class);

	private final Design sourceDesign;
	private final PrimitiveDefList primitives;
	private final Path targetDir;

	private final List<String> clockSites = Arrays.asList("V10", "AB13");

	public IsolatedDelayDesignCreator(Design sourceDesign, PrimitiveDefList primitives, Path targetDir) {

		this.sourceDesign = sourceDesign;
		this.primitives = primitives;
		this.targetDir = targetDir;
		try {
			Files.createDirectories(targetDir);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public Design createIsolatedDesign(Instance sourceInstance, Consumer<String> physicalConstraints) {

		PrimitiveSite connectedIob = getConnectedIob(sourceInstance);

		Iterator<PrimitiveSite> iobSites = getIobSites(connectedIob);

		Design design = new Design("isolated_for_" + sourceInstance.getName(), sourceDesign.getPartName());
		sourceDesign.getAttributes().forEach(a -> design.addAttribute(new Attribute(a)));

		final PrimitiveSite clockSite = clockSites.stream().flatMap(s -> {
			final PrimitiveSite ps = design.getDevice().getPrimitiveSite(s);
			if (ps == null) {
				return Stream.empty();
			}
			return Stream.of(ps);
		}).iterator().next();
		Net clockNet = createNet(design, true, "CLOCK", clockSite);
		Net gndNet = new Net("GLOBAL_LOGIC0", NetType.GND);
		Net vccNet = new Net("GLOBAL_LOGIC1", NetType.VCC);


//		physicalConstraints.accept("TIMEGRP "+clockNet.getName()+" = NET \""+clockNet.getName()+"\";");
//		physicalConstraints.accept("TS_clk = PERIOD TIMEGRP \""+clockNet.getName()+"\" 20 ns HIGH 50.000 % ;");
		physicalConstraints.accept("NET \""+clockNet.getName()+"\" PERIOD = 20 ns HIGH 50.000 % ;");

		Instance instance = new Instance(sourceInstance.getName(), sourceInstance.getType());
		design.addInstance(instance);
		instance.place(sourceInstance.getPrimitiveSite());
		sourceInstance.getAttributes().forEach(a -> instance.addAttribute(new Attribute(a)));

		sourceInstance.getPins().forEach(sourcePin -> {
			if (!(sourcePin.getName().equals("COUT") || sourcePin.getName().equals("CIN"))) {
				createAndConnectPin(design, sourcePin, instance, iobSites, clockNet, gndNet, vccNet, connectedIob, physicalConstraints);
			}
		});

		if (gndNet.getPins().size() > 0) {
			design.addNet(gndNet);
		}
		if (vccNet.getPins().size() > 0) {
			design.addNet(vccNet);
		}

		design.fixSp6IOBs();


		return design;
	}

	private int getPrimSiteDistance(PrimitiveSite a, PrimitiveSite b) {
		return Math.abs(a.getTile().getColumn() - b.getTile().getColumn())
				+ Math.abs(a.getTile().getRow() - b.getTile().getRow());
	}

	private PrimitiveSite getConnectedIob(Instance sourceInstance) {
		if (sourceInstance.getType() == PrimitiveType.ILOGIC2 || sourceInstance.getType() == PrimitiveType.OLOGIC2) {
			Pin pin;
			if (sourceInstance.getType() == PrimitiveType.OLOGIC2) {
				pin = sourceInstance.getPin("OQ");
			} else {
				pin = sourceInstance.getPin("D");
			}
			final Set<Pin> collect = pin.getNet().getPins().stream().filter(p -> p != pin).collect(Collectors.toSet());
			if (collect.size() != 1) {
				throw new RuntimeException("Not exactly one other pin: " + collect);
			}
			return collect.iterator().next().getInstance().getPrimitiveSite();
		} else {
			return null;
		}

	}

	private Set<Instance> createdInstances = new HashSet<>();

	public void saveIsolatedDesignOnce(Instance instance) {
		if (createdInstances.add(instance)) {
			try {
				final String escapedName = instance.getName().replaceAll("[^A-Za-z0-9]", "_");
				DotDumper.dumpInstance(instance, primitives, targetDir.resolve(escapedName + ".dot").toFile());

				List<String> constraints = new ArrayList<>();
				Design target = createIsolatedDesign(instance, constraints::add);

				target.saveXDLFile(targetDir.resolve(escapedName + ".xdl"));
				Files.write(targetDir.resolve(escapedName + ".pcf"), constraints);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private Iterator<PrimitiveSite> getIobSites(PrimitiveSite exclude) {
		return Arrays.stream(sourceDesign.getDevice().getAllCompatibleSites(PrimitiveType.IOB))
				//Filter out clock site
				.filter(ps -> !clockSites.contains(ps.getName()))
				.filter(ps -> ps != exclude)
				.iterator();
	}

	private Net createNet(Design design, boolean isSource, String name, PrimitiveSite site) {
		Instance iob = createIob(name, isSource, site, design);
		Pin iobPin = createIobPin(iob, isSource);

		Net net = new Net("net_" + name, NetType.WIRE);
		net.addPin(iobPin);
		design.addNet(net);
		return net;

	}

	private boolean pinNeedsBuffer(Pin pin) {
		boolean isIlogic = pin.getInstance().getType() == PrimitiveType.ILOGIC2;
		boolean isOlogic = pin.getInstance().getType() == PrimitiveType.OLOGIC2;
		if (isIlogic) {
			return !pin.getName().equals("D");
		}
		if (isOlogic) {
			return !pin.getName().equals("OQ");
		}
		return true;
	}

	private Net createNetForPin(Design design, Pin pin, PrimitiveSite site, Net clockNet, Consumer<String> physicalConstraints) {
		Net net = createNet(design, !pin.isOutPin(), pin.getName(), site);

		if (pinNeedsBuffer(pin)) {

			Net buffered = new Net("buf_" + pin.getName(), NetType.WIRE);
			buffered.addPin(pin);
			design.addNet(buffered);

			if (pin.isOutPin()) {
				connectViaSliceFF(buffered, net, clockNet, design, pin);
			} else {
				connectViaSliceFF(net, buffered, clockNet, design, pin);
			}
		} else {
			Instance iob = net.getPins().iterator().next().getInstance();
			net.addPin(pin);

			String clkIob = clockNet.getSource().getInstanceName();

			if (pin.isOutPin()) {
				physicalConstraints.accept("COMP \""+iob.getName()+"\" OFFSET = OUT 20 ns AFTER COMP \""+clkIob+"\";");
			} else {
				physicalConstraints.accept("COMP \""+iob.getName()+"\" OFFSET = IN 20 ns VALID 20 ns BEFORE COMP \""+clkIob+"\" \"RISING\";");
			}

		}
		return net;
	}

	private void createAndConnectPin(Design design, Pin sourcePin, Instance instance, Iterator<PrimitiveSite> iobSites, Net clockNet, Net gndNet, Net vccNet, PrimitiveSite connectedIob, Consumer<String> physicalConstraints) {

		Pin pin = new Pin(sourcePin.getPinType(), sourcePin.getName(), instance);
		logger.info("connecting {}, {}, {}", sourcePin, sourcePin.getNet().getType(), sourcePin.isClock());
		if (sourcePin.getNet().isStaticNet() && needToKeepStatic(sourcePin)) {
			logger.info("static");
			if (sourcePin.getNet().getType() == NetType.GND) {
				gndNet.addPin(pin);
			} else {
				vccNet.addPin(pin);
			}
		} else if (sourcePin.isClock()) {
			logger.info("clock");
			clockNet.addPin(pin);
		} else {
			logger.info("regular");
			PrimitiveSite site;
			if (pinNeedsBuffer(sourcePin)) {
				if (!iobSites.hasNext()) {
					throw new RuntimeException("Not enough IOBs");
				}

				site = iobSites.next();

			} else {
				logger.info("Using connectediob for " + sourcePin);
				site = connectedIob;
			}

			createNetForPin(design, pin, site, clockNet, physicalConstraints);
		}

	}

	private boolean needToKeepStatic(Pin sourcePin) {
		final boolean slice = sourcePin.getName().endsWith("6") && sourcePin.getInstance().isSLICE();
		final boolean iob = sourcePin.getInstance().isIOB();
		final boolean clk = sourcePin.getName().startsWith("CLK");
		return slice || iob || clk;
	}

	private void connectViaSliceFF(Net in, Net out, Net clk, Design design, Pin pin) {
		Instance inst = new Instance("slice_" + pin.getName(), PrimitiveType.SLICEX);
		inst.addAttribute(new Attribute("A5FFSRINIT", "", "#OFF"));
		inst.addAttribute(new Attribute("A5LUT", "", "#OFF"));
		inst.addAttribute(new Attribute("A6LUT", "", "#OFF"));
		inst.addAttribute(new Attribute("AFF", "", "#OFF"));
		inst.addAttribute(new Attribute("AFFMUX", "", "#OFF"));
		inst.addAttribute(new Attribute("AFFSRINIT", "", "#OFF"));
		inst.addAttribute(new Attribute("AOUTMUX", "", "#OFF"));
		inst.addAttribute(new Attribute("AUSED", "", "#OFF"));
		inst.addAttribute(new Attribute("B5FFSRINIT", "", "#OFF"));
		inst.addAttribute(new Attribute("B5LUT", "", "#OFF"));
		inst.addAttribute(new Attribute("B6LUT", "", "#OFF"));
		inst.addAttribute(new Attribute("BFF", "", "#OFF"));
		inst.addAttribute(new Attribute("BFFMUX", "", "#OFF"));
		inst.addAttribute(new Attribute("BFFSRINIT", "", "#OFF"));
		inst.addAttribute(new Attribute("BOUTMUX", "", "#OFF"));
		inst.addAttribute(new Attribute("BUSED", "", "#OFF"));
		inst.addAttribute(new Attribute("C5FFSRINIT", "", "#OFF"));
		inst.addAttribute(new Attribute("C5LUT", "", "#OFF"));
		inst.addAttribute(new Attribute("C6LUT", "", "#OFF"));
		inst.addAttribute(new Attribute("CEUSED", "", "#OFF"));
		inst.addAttribute(new Attribute("CFF", "", "#OFF"));
		inst.addAttribute(new Attribute("CFFMUX", "", "#OFF"));
		inst.addAttribute(new Attribute("CFFSRINIT", "", "#OFF"));
		inst.addAttribute(new Attribute("CLKINV", "", "CLK"));
		inst.addAttribute(new Attribute("COUTMUX", "", "#OFF"));
		inst.addAttribute(new Attribute("CUSED", "", "#OFF"));
		inst.addAttribute(new Attribute("D5FFSRINIT", "", "#OFF"));
		inst.addAttribute(new Attribute("D5LUT", "", "#OFF"));
		inst.addAttribute(new Attribute("D6LUT", "", "#OFF"));
		inst.addAttribute(new Attribute("DFF", inst.getName() + ".o", "#FF"));
		inst.addAttribute(new Attribute("DFFMUX", "", "DX"));
		inst.addAttribute(new Attribute("DFFSRINIT", "", "SRINIT0"));
		inst.addAttribute(new Attribute("DOUTMUX", "", "#OFF"));
		inst.addAttribute(new Attribute("DUSED", "", "#OFF"));
		inst.addAttribute(new Attribute("SRUSED", "", "#OFF"));
		inst.addAttribute(new Attribute("SYNC_ATTR", "", "ASYNC"));

		design.addInstance(inst);

		place(inst, design);

		Pin clkPin = new Pin(false, "CLK", inst);
		Pin inPin = new Pin(false, "DX", inst);
		Pin outPin = new Pin(true, "DQ", inst);

		clk.addPin(clkPin);
		in.addPin(inPin);
		out.addPin(outPin);
	}

	private void place(Instance inst, Design design) {
		for (PrimitiveSite primitiveSite : design.getDevice().getAllCompatibleSites(inst.getType())) {
			if (design.getInstanceAtPrimitiveSite(primitiveSite) == null) {
				inst.place(primitiveSite);
			}
		}
	}

	private Pin createIobPin(Instance iob, boolean isSource) {
		return new Pin(isSource, isSource? "I" : "O", iob);
	}

	private Instance createIob(String name, boolean isSource, PrimitiveSite iobSite, Design design) {


		Instance iob = new Instance("IOB_" + name, PrimitiveType.IOB);


		design.addInstance(iob);
		iob.place(iobSite);


		iob.addAttribute("DIFFI_INUSED", "", "#OFF");
		iob.addAttribute("DIFF_TERM", "", "#OFF");
		iob.addAttribute("DRIVE_0MA", "", "#OFF");
		iob.addAttribute("OUT_TERM", "", "#OFF");
		iob.addAttribute("PADOUTUSED", "", "#OFF");
		iob.addAttribute("PAD", "pin_" + iob.getName(), "");
		iob.addAttribute("PCI_RDYUSED", "", "#OFF");
		iob.addAttribute("PULLTYPE", "", "#OFF");
		iob.addAttribute("IN_TERM", "", "#OFF");

		if (isSource) {
			iob.addAttribute("BYPASS_MUX", "", "I");
			iob.addAttribute("IMUX", iob.getName() + ".IMUX", "I");
			iob.addAttribute("INBUF", "pin_" + iob.getName() + "_IBUF", "");
			iob.addAttribute("ISTANDARD", "", "LVCMOS25");//TODO use configured
		} else {
			iob.addAttribute("BYPASS_MUX", "", "#OFF");
			iob.addAttribute("IMUX", "", "#OFF");
		}

		if (!isSource) {
			iob.addAttribute("DRIVEATTRBOX", "", "12");//TODO use configured
			iob.addAttribute("OSTANDARD", "", "LVCMOS25");//TODO use configured
			iob.addAttribute("OUSED", "", "0");
			iob.addAttribute("OUTBUF", "pin_" + iob.getName() + "_OBUF", "");//OUTPUT
			iob.addAttribute("PRE_EMPHASIS", "", "#OFF");//XDL bug, we cannot add this to inputs or they are considered as outs
			iob.addAttribute("SLEW", "", "SLOW");//TODO use configured
			iob.addAttribute("SUSPEND", "", "3STATE");
		} else {
			iob.addAttribute("DRIVEATTRBOX", "", "#OFF");
			iob.addAttribute("OUSED", "", "#OFF");
			iob.addAttribute("SLEW", "", "#OFF");
			iob.addAttribute("SUSPEND", "", "#OFF");

		}

		iob.addAttribute("TUSED", "", "#OFF");

		return iob;
	}


	public static void main(String[] args) throws IOException, ClassNotFoundException {
		Design design = new Design(Paths.get(args[0]));
		PrimitiveDefList primitiveDefs = DeviceDatabaseProvider.getDeviceDatabase().loadPrimitiveDefs(design.getFamilyType());
		PrimitiveDefDetails.insertDetails(primitiveDefs);

		TimingCalibration cal = TimingCalibration.loadFromFile(new File(args[1]));

		cal.enterToPrimitiveDefs(primitiveDefs);

		DelayModel delayModel = cal.createDelayModel();

		TimingCalculator tc = new TimingCalculator(primitiveDefs, delayModel, design);

		IsolatedDelayDesignCreator creator = new IsolatedDelayDesignCreator(design, primitiveDefs, Paths.get("isolatedDelays"));

		dumpInteresting(design, primitiveDefs);

		tc.calculateTimings(creator::unknownDelay);
	}

	private static void dumpInteresting(Design design, PrimitiveDefList primitiveDefs) throws FileNotFoundException {
		for (Instance inst : design.getInstances()) {
			if (inst.getType() == PrimitiveType.ILOGIC2 || inst.getType().equals(PrimitiveType.OLOGIC2)) {
				DotDumper.dumpInstanceOnce(inst, primitiveDefs);
			}
		}
	}

	private void unknownDelay(RoutingElement from, RoutingElement to) {
		logger.warn("unknown delay from {} to {}", from, to);
		saveIsolatedDesignOnce(from.getInstance());
	}
}
