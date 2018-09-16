package edu.byu.ece.rapidSmith.primitiveDefs;

import edu.byu.ece.rapidSmith.design.Design;
import edu.byu.ece.rapidSmith.device.PrimitiveType;
import edu.byu.ece.rapidSmith.timing.DotDumper;
import edu.byu.ece.rapidSmith.timing.TraceReportXmlParser;
import edu.byu.ece.rapidSmith.util.DeviceDatabaseProvider;
import edu.byu.ece.rapidSmith.util.FileTools;
import edu.byu.ece.rapidSmith.device.database.FileDeviceDatabase;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

/**
 * Replace some parts of primitiveDefs that lack in detail
 */
public class PrimitiveDefDetails {

	private static void connect(Element e0, String pin0, Element e1, String pin1, boolean fw) {
		Connection forward = new Connection();
		forward.setElement0(e0.getName());
		forward.setElement1(e1.getName());
		forward.setPin0(pin0);
		forward.setPin1(pin1);
		forward.setForwardConnection(fw);
		e0.addConnection(forward);

		Connection backward = new Connection();
		backward.setElement0(e1.getName());
		backward.setElement1(e0.getName());
		backward.setPin0(pin1);
		backward.setPin1(pin0);
		backward.setForwardConnection(!fw);
		e1.addConnection(backward);

	}

	private static boolean removeConnectionOneDir(Element e0, String pin0, Element e1, String pin1, boolean isForward) {
		if (e0 == null) {
			return false;
		}
		ListIterator<Connection> li = e0.getConnections().listIterator();
		while (li.hasNext()) {
			Connection conn = li.next();
			if (conn.getElement0().equals(e0.getName()) && conn.getElement1().equals(e1.getName()) &&
					conn.getPin0().equals(pin0) && conn.getPin1().equals(pin1) && conn.isForwardConnection() == isForward) {
				li.remove();
				return true;
			}
		}
		return false;
	}

	private static boolean removeConnection(Element e0, String pin0, Element e1, String pin1, boolean reverse) {
		boolean r1 = removeConnectionOneDir(e0, pin0, e1, pin1, !reverse);
		boolean r2 = removeConnectionOneDir(e1, pin1, e0, pin0, reverse);
		return r1 && r2;
	}

	private static boolean removeConnection(Connection conn, PrimitiveDef def) {
		return removeConnection(conn.getElement0(def),
				conn.getPin0(),
				conn.getElement1(def),
				conn.getPin1(),
				!conn.isForwardConnection());
	}

	/**
	 * Move all connections from one pin to a new one
	 *
	 * @param oldElement
	 * @param oldPin
	 * @param newElement
	 * @param newPin
	 * @param def
	 */
	private static void replacePin(Element oldElement, String oldPin, Element newElement, String newPin, PrimitiveDef def) {
		List<Connection> toRemove = new ArrayList<>();
		for (Connection connection : oldElement.getConnections()) {
			if (connection.getElement0().equals(oldElement.getName()) && connection.getPin0().equals(oldPin)) {
				toRemove.add(connection);
				connect(newElement, newPin, connection.getElement1(def), connection.getPin1(), connection.isForwardConnection());
			} else if (connection.getElement1().equals(oldElement.getName()) && connection.getPin1().equals(oldPin)) {
				toRemove.add(connection);
				connect(connection.getElement0(def), connection.getPin0(), newElement, newPin, connection.isForwardConnection());
			}
		}


		for (Connection connection : toRemove) {
			removeConnection(connection, def);
		}
	}

	private static void connectToSame(Element oldElement, String oldPin, Element newElement, String newPin, PrimitiveDef def) {
		for (Connection connection : oldElement.getConnections()) {
			if (connection.getElement0().equals(oldElement.getName()) && connection.getPin0().equals(oldPin)) {
				connect(newElement, newPin, connection.getElement1(def), connection.getPin1(), connection.isForwardConnection());
			} else if (connection.getElement1().equals(oldElement.getName()) && connection.getPin1().equals(oldPin)) {
				connect(connection.getElement0(def), connection.getPin0(), newElement, newPin, connection.isForwardConnection());
			}
		}
	}

	private static Element addCarryXOR(PrimitiveDef slice, int number) {
		Element xor = new Element();
		xor.setName("CARRY4XOR" + number);
		PrimitiveDefPin i0 = new PrimitiveDefPin();
		i0.setInternalName("I0");
		i0.setOutput(false);
		xor.addPin(i0);
		PrimitiveDefPin i1 = new PrimitiveDefPin();
		i1.setInternalName("I1");
		i1.setOutput(false);
		xor.addPin(i1);
		PrimitiveDefPin o = new PrimitiveDefPin();
		o.setInternalName("O");
		o.setOutput(true);
		xor.addPin(o);
		slice.addElement(xor);
		return xor;
	}

	private static Element addCarryInputMux(PrimitiveDef slice) {
		Element inputMux = new Element();
		inputMux.setName("CARRY4INPUT");
		PrimitiveDefPin i0 = new PrimitiveDefPin();
		i0.setInternalName("I0");
		i0.setOutput(false);
		inputMux.addPin(i0);
		PrimitiveDefPin i1 = new PrimitiveDefPin();
		i1.setInternalName("I1");
		i1.setOutput(false);
		inputMux.addPin(i1);
		PrimitiveDefPin o = new PrimitiveDefPin();
		o.setInternalName("O");
		o.setOutput(true);
		inputMux.addPin(o);
		slice.addElement(inputMux);
		return inputMux;
	}

	private static Element addCarryMux(PrimitiveDef slice, int number) {
		Element mux = new Element();
		mux.setName("CARRY4MUX" + number);

		PrimitiveDefPin i0 = new PrimitiveDefPin();
		i0.setInternalName("I0");
		i0.setOutput(false);
		mux.addPin(i0);

		PrimitiveDefPin i1 = new PrimitiveDefPin();
		i1.setInternalName("I1");
		i1.setOutput(false);
		mux.addPin(i1);

		PrimitiveDefPin sel = new PrimitiveDefPin();
		sel.setInternalName("SEL");
		sel.setOutput(false);
		mux.addPin(sel);

		PrimitiveDefPin o = new PrimitiveDefPin();
		o.setInternalName("O");
		o.setOutput(true);
		mux.addPin(o);

		slice.addElement(mux);

		return mux;
	}

	private static void replaceCarry(PrimitiveDef slice) {
		Element carry = slice.getElement("CARRY4");


		Element inputMux = addCarryInputMux(slice);
		replacePin(carry, "CYINIT", inputMux, "I0", slice);
		replacePin(carry, "CIN", inputMux, "I1", slice);

		Element lastMux = inputMux;
		for (int i = 0; i < 4; i++) {
			Element mux = addCarryMux(slice, i);
			Element xor = addCarryXOR(slice, i);

			replacePin(carry, "DI" + i, mux, "I0", slice);
			replacePin(carry, "S" + i, mux, "SEL", slice);

			replacePin(carry, "CO" + i, mux, "O", slice);
			replacePin(carry, "O" + i, xor, "O", slice);

			connectToSame(mux, "SEL", xor, "I0", slice);

			connect(lastMux, "O", mux, "I1", true);
			connectToSame(mux, "I1", xor, "I1", slice);

			lastMux = mux;

		}
	}

	private static void insertDSPInputBus(PrimitiveDef dsp, String input, int width) {
		Element dspElem = dsp.getElement("DSP48A1");
		Element inputBus = new Element();
		inputBus.setName(input + "INPUTBUS");
		dsp.addElement(inputBus);

		PrimitiveDefPin oPin = new PrimitiveDefPin();
		oPin.setInternalName("O");
		oPin.setOutput(true);
		inputBus.addPin(oPin);

		for (int i = 0; i < width; i++) {

			PrimitiveDefPin pin = new PrimitiveDefPin();
			pin.setInternalName(input + i);
			pin.setOutput(false);
			inputBus.addPin(pin);

			replacePin(dspElem, input + i, inputBus, input + i, dsp);
		}
	}

	private static void insertIoDelayBusy(PrimitiveDef ioDelay) {
		Element elem = ioDelay.getElement("IODRP2");

		PrimitiveDefPin pin = new PrimitiveDefPin();
		pin.setInternalName("BUSY");
		pin.setOutput(true);
		elem.addPin(pin);


		Element out = new Element();
		out.setName("BUSY");
		PrimitiveDefPin oPin = new PrimitiveDefPin();
		oPin.setInternalName("BUSY");
		oPin.setExternalName("BUSY");
		oPin.setOutput(false);
		out.addPin(oPin);
		ioDelay.addElement(out);

		connect(elem, "BUSY", out, "BUSY", true);
	}

	public static void replaceDSP(PrimitiveDef dsp) {
		insertDSPInputBus(dsp, "PCIN", 48);
		insertDSPInputBus(dsp, "A", 18);
		insertDSPInputBus(dsp, "B", 18);
		insertDSPInputBus(dsp, "C", 48);
		insertDSPInputBus(dsp, "D", 18);
		insertDSPInputBus(dsp, "BCIN", 18);


		insertOptionalReg(dsp, "D", "DINPUTBUS", "O");
		insertOptionalReg(dsp, "B0", "BINPUTBUS", "O");
		insertOptionalReg(dsp, "A0", "AINPUTBUS", "O");
		insertOptionalReg(dsp, "C", "CINPUTBUS", "O");

	}


	private static void insertOptionalReg(PrimitiveDef dsp, String name, Element input, String elementOPin) {
		Element ff = new Element();
		ff.setName(name + "FF");
		dsp.addElement(ff);

		PrimitiveDefPin ffIn = new PrimitiveDefPin();
		ffIn.setInternalName("I");
		ffIn.setOutput(false);
		ff.addPin(ffIn);

		PrimitiveDefPin ffOut = new PrimitiveDefPin();
		ffOut.setInternalName("O");
		ffOut.setOutput(true);
		ff.addPin(ffOut);

		Element mux = new Element();
		mux.setName(name + "REG");
		dsp.addElement(mux);

		PrimitiveDefPin muxI0 = new PrimitiveDefPin();
		muxI0.setInternalName("0");
		muxI0.setOutput(false);
		mux.addPin(muxI0);

		PrimitiveDefPin muxI1 = new PrimitiveDefPin();
		muxI1.setInternalName("1");
		muxI1.setOutput(false);
		mux.addPin(muxI1);

		PrimitiveDefPin muxO = new PrimitiveDefPin();
		muxO.setInternalName("O");
		muxO.setOutput(true);
		mux.addPin(muxO);


		connect(input, elementOPin, ff, "I", true);
		connect(input, elementOPin, mux, "0", true);
		connect(ff, "O", mux, "1", true);
	}


	private static void insertOptionalReg(PrimitiveDef prim, String name, String input, String elementOPin) {
		Element ie = prim.getElement(input);
		if (ie == null) {
			throw new NullPointerException("Could not find " + input + " in " + prim.getType());
		}
		insertOptionalReg(prim, name, ie, elementOPin);
	}

	/**
	 * Fix an element that only has one pin
	 *
	 * @param prim
	 * @param elem
	 */
	private static void makeTwoPins(PrimitiveDef prim, Element elem) {
		PrimitiveDefPin oPin = new PrimitiveDefPin();
		oPin.setInternalName("OPAD");
		oPin.setOutput(true);
		elem.addPin(oPin);

		//We need to move all connections that are forward
		Connection[] toMove = elem.getConnections().stream().filter(Connection::isForwardConnection).toArray(Connection[]::new);

		for (Connection connection : toMove) {
			connection.setPin0(oPin.getInternalName());

			for (Connection backEdge : connection.getElement1(prim).getConnections()) {
				if (backEdge.getElement1(prim) == elem) {
					backEdge.setPin1(oPin.getInternalName());
					break;
				}
			}
		}
	}

	private static void fixIOB(PrimitiveDef iob) {
		Element pad = iob.getElement("PAD");
		Element pull = iob.getElement("PULL");


		makeTwoPins(iob, pad);

		//remove combinatorial connection from outbuf to inbuf
		Element outbuf = iob.getElement("OUTBUF");
		Element inbuf = iob.getElement("INBUF");
		removeConnection(outbuf, "OUT", inbuf, "PAD", false);

		if (pull != null) {
			makeTwoPins(iob, pull);

			//Remove extra connections
			removeConnectionOneDir(pad, "OPAD", pull, "OPAD", true);
			removeConnectionOneDir(pull, "OPAD", pad, "OPAD", true);
			removeConnectionOneDir(pad, "PAD", pull, "PAD", false);
			removeConnectionOneDir(pull, "PAD", pad, "PAD", false);
		}
	}

	public static void insertDetails(PrimitiveDefList primitiveDefs) {
		replaceCarry(primitiveDefs.getPrimitiveDef(PrimitiveType.SLICEM));
		replaceCarry(primitiveDefs.getPrimitiveDef(PrimitiveType.SLICEL));
		//TODO finish dsp replaccement
		//replaceDSP(primitiveDefs.getPrimitiveDef(PrimitiveType.DSP48A1));

		insertIoDelayBusy(primitiveDefs.getPrimitiveDef(PrimitiveType.IODRP2));

		fixIOB(primitiveDefs.getPrimitiveDef(PrimitiveType.IOB));
		fixIOB(primitiveDefs.getPrimitiveDef(PrimitiveType.IOBM));
		fixIOB(primitiveDefs.getPrimitiveDef(PrimitiveType.IOBS));
		fixIOB(primitiveDefs.getPrimitiveDef(PrimitiveType.IPAD));


		replaceDistributedRam(primitiveDefs.getPrimitiveDef(PrimitiveType.SLICEM));
	}

	/**
	 * Distributed RAM is modeled as a single element. But the read port is combinatorial, whereas the write port
	 * is registered --> Split it
	 *
	 * @param primitiveDef the slice
	 */
	private static void replaceDistributedRam(PrimitiveDef primitiveDef) {
		String[] pinsToMove = {"WA1", "WA2", "WA3", "WA4", "WA5", "WA6", "WA7", "WA8", "WE", "DI1", "DI2", "CLK"};


		for (char c = 'A'; c <= 'D'; c++) {
			for (int i = 5; i <= 6; i++) {
				String lut = Character.toString(c) + i + "LUT";
				final Element lutElement = primitiveDef.getElement(lut);

				Element writePort = new Element();
				writePort.setName(lut + "WRITE");
				for (String pin : pinsToMove) {
					if (lutElement.getPin(pin) != null) {
						movePin(lutElement, pin, writePort, pin, primitiveDef);
					}
				}
				primitiveDef.addElement(writePort);

				//TODO edge from write to lut???
				PrimitiveDefPin ramDataOut = new PrimitiveDefPin();
				ramDataOut.setInternalName("RAMDATA");
				ramDataOut.setOutput(true);

				PrimitiveDefPin ramDataIn = new PrimitiveDefPin();
				ramDataIn.setInternalName("RAMDATA");
				ramDataIn.setOutput(false);

				writePort.addPin(ramDataOut);
				lutElement.addPin(ramDataIn);

				connect(writePort, "RAMDATA", lutElement, "RAMDATA", true);

			}
		}
	}

	private static void movePin(Element oldElement, String oldPin, Element newElement, String newPin, PrimitiveDef def) {
		final PrimitiveDefPin pin = oldElement.getPin(oldPin);
		newElement.addPin(pin);
		pin.setInternalName(newPin);
		oldElement.getPins().remove(pin);

		replacePin(oldElement, oldPin, newElement, newPin, def);
	}


	static class Testcase {

		final String xdl;
		final String twx;

		Testcase(String xdl, String twx) {
			this.xdl = xdl;
			this.twx = twx;
		}
	}

	public static void main(String[] args) {

		Testcase tc = new Testcase("testdaten/dvi/dvi.xdl", "testdaten/dvi/dvi.twx");

		final String directory = FileDeviceDatabase.getRapidSmithPath() + FileTools.getDirectorySeparator();
		Design myDesign = new Design();
		myDesign.loadXDLFile(Paths.get(directory + tc.xdl));

		TraceReportXmlParser p = new TraceReportXmlParser();
		p.parseTWX(directory + tc.twx, myDesign);

		PrimitiveDefList primitiveDefs = DeviceDatabaseProvider.getDeviceDatabase().loadPrimitiveDefs(myDesign.getPartName());


		try {
			DotDumper.dumpPrimitiveDef(primitiveDefs, PrimitiveType.SLICEM, new File("slicem_default.dot"));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}

		insertDetails(primitiveDefs);
		checkConsistency(primitiveDefs);

		try {
			DotDumper.dumpPrimitiveDef(primitiveDefs, PrimitiveType.SLICEM, new File("slicem_augmented.dot"));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}

	}

	public static void checkConsistency(PrimitiveDef prim) {
		prim.getElements().forEach(element -> {
			element.getConnections().forEach(connection -> {
				Element next;
				if (connection.isForwardConnection()) {
					if (connection.getElement0(prim) != element) {
						throw new RuntimeException("Elem 0 of a forward connection should be the current one " + connection + " in " + prim.getType() + "." + element.getName());
					}
					if (!element.getPins().contains(connection.getPin0(prim))) {
						throw new RuntimeException("Elem 0 did not contain the pin to connect to " + connection + " in " + prim.getType() + "." + element.getName());
					}

					if (!connection.getPin0(prim).isOutput()) {
						throw new RuntimeException("Pin 0 should be an output " + connection + " in " + prim.getType() + "." + element.getName());
					}

					next = connection.getElement1(prim);
					if (!next.getPins().contains(connection.getPin1(prim))) {
						throw new RuntimeException("Elem 1 did not contain the pin to connect to " + connection + " in " + prim.getType() + "." + element.getName());
					}

					if (connection.getPin1(prim).isOutput()) {
						throw new RuntimeException("Pin 1 should be an input " + connection + " in " + prim.getType() + "." + element.getName());
					}

				} else {
					if (connection.getElement0(prim) != element) {
						throw new RuntimeException("Elem 0 of a backward connection should be the current one " + connection + " in " + prim.getType() + "." + element.getName() + " but was " + connection.getElement1());
					}
					if (!element.getPins().contains(connection.getPin0(prim))) {
						throw new RuntimeException("Elem 0 did not contain the pin to connect to " + connection + " in " + prim.getType() + "." + element.getName());
					}

					if (connection.getPin0(prim).isOutput()) {
						throw new RuntimeException("Pin 0 should be an input " + connection + " in " + prim.getType() + "." + element.getName());
					}

					next = connection.getElement1(prim);
					if (!next.getPins().contains(connection.getPin1(prim))) {
						throw new RuntimeException("Elem 1 did not contain the pin to connect to " + connection + " in " + prim.getType() + "." + element.getName());
					}

					if (!connection.getPin1(prim).isOutput()) {
						throw new RuntimeException("Pin 1 should be an output " + connection + " in " + prim.getType() + "." + element.getName());
					}
				}


				boolean[] foundBack = new boolean[1];
				foundBack[0] = false;
				next.getConnections().forEach(connection1 -> {
					if (connection1.isForwardConnection() != connection.isForwardConnection()
							&& connection1.getElement0().equals(connection.getElement1())
							&& connection1.getElement1().equals(connection.getElement0())
							&& connection1.getPin0().equals(connection.getPin1())
							&& connection1.getPin1().equals(connection.getPin0())) {
						foundBack[0] = true;
					}
				});
				if (!foundBack[0]) {
					throw new RuntimeException("did not find mirror connection for " + connection + " in " + prim.getType() + "." + element.getName() + " available: " + next.getConnections());
				}
			});
		});
	}

	public static void checkConsistency(PrimitiveDefList primitives) {
		primitives.forEach(PrimitiveDefDetails::checkConsistency);
	}
}
