/*
 * Copyright (c) 2010 Brigham Young University
 * 
 * This file is part of the BYU RapidSmith Tools.
 * 
 * BYU RapidSmith Tools is free software: you may redistribute it 
 * and/or modify it under the terms of the GNU General Public License 
 * as published by the Free Software Foundation, either version 2 of 
 * the License, or (at your option) any later version.
 * 
 * BYU RapidSmith Tools is distributed in the hope that it will be 
 * useful, but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU 
 * General Public License for more details.
 * 
 * A copy of the GNU General Public License is included with the BYU 
 * RapidSmith Tools. It can be found at doc/gpl2.txt. You may also 
 * get a copy of the license at <http://www.gnu.org/licenses/>.
 * 
 */
package edu.byu.ece.rapidSmith.design;

import edu.byu.ece.rapidSmith.design.parser.DesignParser;
import edu.byu.ece.rapidSmith.device.Device;
import edu.byu.ece.rapidSmith.device.PrimitiveSite;
import edu.byu.ece.rapidSmith.device.PrimitiveType;
import edu.byu.ece.rapidSmith.device.WireEnumerator;
import edu.byu.ece.rapidSmith.primitiveDefs.Element;
import edu.byu.ece.rapidSmith.primitiveDefs.PrimitiveDef;
import edu.byu.ece.rapidSmith.primitiveDefs.PrimitiveDefList;
import edu.byu.ece.rapidSmith.router.InstanceElement;
import edu.byu.ece.rapidSmith.router.RoutingElement;
import edu.byu.ece.rapidSmith.util.DeviceDatabaseProvider;
import edu.byu.ece.rapidSmith.util.FamilyType;
import edu.byu.ece.rapidSmith.util.FileTools;
import edu.byu.ece.rapidSmith.util.MessageGenerator;
import edu.byu.ece.rapidSmith.util.PartNameTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The design class houses an entire XDL design or hard macro.  It keeps
 * track of all of its member instances, nets, modules and attributes
 * and can load/import save/export XDL files.  When an XDL design is loaded
 * into this class it also populates the Device and WireEnumerator classes
 * that correspond to the part this design targets.
 *
 * @author Chris Lavin
 *         Created on: Jun 22, 2010
 */
public class Design implements Serializable {

	private static final Logger logger = LoggerFactory.getLogger(Design.class);

	private static final long serialVersionUID = 6586577338969915167L;

	/**
	 * Name of the design
	 */
	private String name;
	/**
	 * All of the attributes in the design
	 */
	private ArrayList<Attribute> attributes;
	/**
	 * This is the Xilinx part, package and speed grade that this design targets
	 */
	private String partName;
	/**
	 * XDL is typically generated from NCD, this is the version of that NCD file (v3.2 is typical)
	 */
	private String NCDVersion;
	/**
	 * This is the list of modules or macros in the design
	 */
	private HashMap<String, Module> modules;
	/**
	 * Keeps track of module instances and groups them according to module instance name
	 */
	private HashMap<String, ModuleInstance> moduleInstances;
	/**
	 * This is a list of all the instances of primitives and macros in the design
	 */
	private HashMap<String, Instance> instances;
	/**
	 * A map used to keep track of all used primitive sites used by the design
	 */
	private HashMap<PrimitiveSite, Instance> usedPrimitiveSites;
	/**
	 * This is a list of all the nets in the design
	 */
	private HashMap<String, Net> nets;
	/**
	 * A flag designating if this is a design or hard macro
	 */
	private boolean isHardMacro;

	/**
	 * This is the actual part database device for the design specified by partName
	 */
	private transient Device dev;
	/**
	 * This is the accompanying wire enumeration class to convert wire integers to Strings and vice versa
	 */
	private transient WireEnumerator we;

	/**
	 * This is the special design name used by Xilinx to denote an XDL design as a hard macro
	 */
	public static final String hardMacroDesignName = "__XILINX_NMC_MACRO";
	/**
	 * Keeps track of all slice primitive types, initialized statically
	 */
	public static HashSet<PrimitiveType> sliceTypes;
	/**
	 * Keeps track of all DSP48 primitive types, initialized statically
	 */
	public static HashSet<PrimitiveType> dspTypes;
	/**
	 * Keeps track of all BRAM primitive types, initialized statically
	 */
	public static HashSet<PrimitiveType> bramTypes;
	/**
	 * Keeps track of all IOB primitive types, initialized statically
	 */
	public static HashSet<PrimitiveType> iobTypes;

	private List<String> comments = new ArrayList<>();

	/**
	 * Constructor which initializes all member data structures. Sets partName to null.
	 * NCDVersion is set to null. isHardMacro is set to false.
	 */
	public Design() {
		name = null;
		partName = null;
		NCDVersion = "v3.2"; // By default, it is often this value
		attributes = new ArrayList<Attribute>();
		modules = new HashMap<String, Module>();
		instances = new HashMap<String, Instance>();
		usedPrimitiveSites = new HashMap<PrimitiveSite, Instance>();
		nets = new HashMap<String, Net>();
		isHardMacro = false;
		moduleInstances = new HashMap<String, ModuleInstance>();
	}

	/**
	 * Creates a new design and populates it with the given design name and
	 * part name.
	 *
	 * @param designName The name of the newly created design.
	 * @param partName The target part name of the newly created design.
	 */
	public Design(String designName, String partName) {
		this();
		setName(designName);
		setPartName(partName);
	}

	/**
	 * Loads and creates a design from an XDL file.
	 *
	 * @param xdlFileName Name of the XDL file to load into the design.
	 */
	public Design(Path xdlFileName) {
		this();
		loadXDLFile(xdlFileName);
	}


	/**
	 * Loads the corresponding Device and WireEnumerator based on partName.
	 */
	public void loadDeviceAndWireEnumerator() {
		we = DeviceDatabaseProvider.getDeviceDatabase().loadWireEnumerator(partName);
		dev = DeviceDatabaseProvider.getDeviceDatabase().loadDevice(partName);
	}

	/**
	 * Checks if the primitive site site is used in this design.
	 *
	 * @param site The site to check for.
	 * @return True if this design uses site, false otherwise.
	 */
	public boolean isPrimitiveSiteUsed(PrimitiveSite site) {
		return usedPrimitiveSites.containsKey(site);
	}

	/**
	 * Marks a primitive site as used by a particular instance.
	 *
	 * @param site The site to be marked as used.
	 * @param inst The instance using the site or null if the primitive site
	 * is null.
	 */
	protected Instance setPrimitiveSiteUsed(PrimitiveSite site, Instance inst) {
		if (site == null) {
			return null;
		}
		return usedPrimitiveSites.put(site, inst);
	}

	public void rebuildPrimitiveSitesUsed() {
		clearUsedPrimitiveSites();
		instances.values().forEach(i -> {
			if (i.isPlaced()) {
				setPrimitiveSiteUsed(i.getPrimitiveSite(), i);
			}
		});
	}

	protected Instance releasePrimitiveSite(PrimitiveSite site) {
		return usedPrimitiveSites.remove(site);
	}

	/**
	 * Gets and returns the instance which resides at site.
	 *
	 * @param site The site of the desired instance.
	 * @return The instance at site, or null if the primitive site is unoccupied.
	 */
	public Instance getInstanceAtPrimitiveSite(PrimitiveSite site) {
		return usedPrimitiveSites.get(site);
	}

	/**
	 * Hard macro instances do not have a unified container, so we use a HashMap.
	 * This function allows the separation of an instance based on its hard macro instance
	 * name.
	 *
	 * @param inst The instance to add to the hashMap
	 * @return The ModuleInstance that the instance was added to
	 */
	public ModuleInstance addInstanceToModuleInstances(Instance inst, String moduleInstanceName) {
		String key = moduleInstanceName;// + inst.getModuleTemplate().name;
		ModuleInstance mi = moduleInstances.get(key);

		if (mi == null) {
			mi = new ModuleInstance(moduleInstanceName, this);
			moduleInstances.put(key, mi);
			mi.setModule(inst.getModuleTemplate());
		}
		mi.addInstance(inst);
		inst.setModuleInstance(mi);
		return mi;
	}

	/**
	 * Gets and returns the moduleInstance called name.
	 *
	 * @param name The name of the moduleInstance to get.
	 * @return The moduleInstance name, or null if it does not exist.
	 */
	public ModuleInstance getModuleInstance(String name) {
		return moduleInstances.get(name);
	}

	/**
	 * Creates, adds to design, and returns a new
	 * ModuleInstance called name and based on module.
	 * The module is also added to the design if not already present.
	 *
	 * @param name The name of the new ModuleInstance created.
	 * @param module The Module that the new ModuleInstance instances.
	 * @return A new ModuleInstance
	 */
	public ModuleInstance createModuleInstance(String name, Module module) {
		addModule(module);

		ModuleInstance modInst = new ModuleInstance(name, this);
		moduleInstances.put(modInst.getName(), modInst);
		modInst.setModule(module);
		String prefix = modInst.getName() + "/";
		HashMap<Instance, Instance> inst2instMap = new HashMap<Instance, Instance>();
		modInst.setInst2instMap(inst2instMap);
		for (Instance templateInst : module.getInstances()) {
			Instance inst = new Instance();
			inst.setName(prefix + templateInst.getName());
			inst.setModuleTemplate(module);
			inst.setModuleTemplateInstance(templateInst);
			for (Attribute attr : templateInst.getAttributes()) {
				inst.addAttribute(attr);
			}
			inst.setBonded(templateInst.getBonded());
			inst.setType(templateInst.getType());

			this.addInstance(inst);
			inst.setModuleInstance(modInst);
			modInst.addInstance(inst);
			if (templateInst.equals(module.getAnchor())) {
				modInst.setAnchor(inst);
			}
			inst2instMap.put(templateInst, inst);
		}

		/*HashMap<Pin,Port> pinToPortMap = new HashMap<Pin,Port>();
		for(Port port : module.getPorts()){
			pinToPortMap.put(port.getPin(),port);
		}*/

		for (Net templateNet : module.getNets()) {
			Net net = new Net(prefix + templateNet.getName(), templateNet.getType());

			HashSet<Instance> instanceList = new HashSet<Instance>();
			Port port = null;
			for (Pin templatePin : templateNet.getPins()) {
				//TODO does this work?
				Port temp = templatePin.getPort();
				//Port temp = pinToPortMap.get(templatePin);
				port = (temp != null)? temp : port;
				Instance inst = inst2instMap.get(templatePin.getInstance());
				if (inst == null) {
					System.out.println("DEBUG: could not find Instance " + prefix + templatePin.getInstanceName());
				}
				instanceList.add(inst);
				Pin pin = new Pin(templatePin.isOutPin(), templatePin.getName(), inst);
				net.addPin(pin);
				pin.setPort(templatePin.getPort());
			}

			if (port == null) {
				modInst.addNet(net);
				net.addAttribute("_MACRO", "", modInst.getName());
				net.setModuleInstance(modInst);
				net.setModuleTemplate(module);
				net.setModuleTemplateNet(templateNet);
			} else {
				net.setName(prefix + port.getName());
			}
			this.addNet(net);
			if (templateNet.hasAttributes()) {
				for (Attribute a : templateNet.getAttributes()) {
					if (a.getPhysicalName().contains("BELSIG")) {
						net.addAttribute(new Attribute(a.getPhysicalName(), a.getLogicalName().replace(a.getValue(), modInst.getName() + "/" + a.getValue()), modInst.getName() + "/" + a.getValue()));
					} else {
						net.addAttribute(a);
					}
				}
			}
			for (Instance inst : instanceList) {
				if (inst != null) {
					inst.addToNetList(net);
				}
			}
		}
		return modInst;
	}

	/**
	 * Gets and returns the current name of the design.
	 *
	 * @return The current name of the design.
	 */
	public String getName() {
		return name;
	}

	/**
	 * Gets and returns the current attributes of the design.
	 *
	 * @return The current attributes of the design.
	 */
	public ArrayList<Attribute> getAttributes() {
		return attributes;
	}

	/**
	 * Adds the attribute with value to this design.
	 *
	 * @param physicalName Physical name of the attribute.
	 * @param value Value to set the new attribute to.
	 */
	public void addAttribute(String physicalName, String logicalName, String value) {
		attributes.add(new Attribute(physicalName, logicalName, value));
	}

	/**
	 * Add the attribute to the design.
	 *
	 * @param attribute The attribute to add.
	 */
	public void addAttribute(Attribute attribute) {
		attributes.add(attribute);
	}

	/**
	 * Checks if the design attribute has an attribute with a physical
	 * name called phyisicalName.
	 *
	 * @param physicalName The physical name of the attribute to check for.
	 * @return True if the design contains an attribute with the
	 * physical name physicalName, false otherwise.
	 */
	public boolean hasAttribute(String physicalName) {
		for (Attribute attr : attributes) {
			if (attr.getPhysicalName().equals(physicalName)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Sets the list of attributes for this design.
	 *
	 * @param attributes The new list of attributes to associate with this
	 * design.
	 */
	public void setAttributes(ArrayList<Attribute> attributes) {
		this.attributes = attributes;
	}

	/**
	 * Gets and returns the Collection of all of the module instances in this design.
	 *
	 * @return All the module instances in this design.
	 */
	public Collection<ModuleInstance> getModuleInstances() {
		return moduleInstances.values();
	}

	/**
	 * Gets and returns the HashMap of all of the module instance members separated by
	 * module instance name.
	 *
	 * @return The HashMap containing all current module instances.
	 */
	public HashMap<String, ModuleInstance> getModuleInstanceMap() {
		return moduleInstances;
	}

	/**
	 * This will return the part name with speed grade of the part this design or
	 * hard macro targets (ex: xc4vsx35ff668-10).
	 *
	 * @return The part name with package and speed grade information.
	 */
	public String getPartName() {
		return this.partName;
	}

	/**
	 * Gets and returns the all lower case exact Xilinx family name this design
	 * targets (ex: qvirtex4 instead of virtex4). DO NOT use exact family
	 * methods if it is to be used for accessing device or wire enumeration
	 * files as RapidSmith does not generate files for devices that have
	 * XDLRC compatible files.
	 *
	 * @return The exact Xilinx family name this design targets.
	 */
	public String getExactFamilyName() {
		return PartNameTools.getExactFamilyNameFromPart(partName);
	}

	/**
	 * Gets and returns the all lower case base family name this design
	 * targets. This ensures compatibility with all RapidSmith files. For
	 * differentiating family names (qvirtex4 rather than virtex4) use
	 * getExactFamilyName().
	 *
	 * @return The base family name of the part this design targets.
	 */
	public String getFamilyName() {
		return PartNameTools.getFamilyNameFromPart(partName);
	}

	/**
	 * Gets and returns the all lower case exact Xilinx family type this design
	 * targets (ex: qvirtex4 instead of virtex4). DO NOT use exact family
	 * methods if it is to be used for accessing device or wire enumeration
	 * files as RapidSmith does not generate files for devices that have
	 * XDLRC compatible files.
	 *
	 * @return The exact Xilinx family type this design targets.
	 */
	public FamilyType getExactFamilyType() {
		return PartNameTools.getExactFamilyTypeFromPart(partName);
	}

	/**
	 * Gets and returns the base family type this design targets. This
	 * ensures compatibility with all RapidSmith files. For differentiating
	 * family types (qvirtex4 rather than virtex4) use getExactFamilyType().
	 *
	 * @return The base family type of the part this design targets.
	 */
	public FamilyType getFamilyType() {
		return PartNameTools.getFamilyTypeFromPart(partName);
	}


	/**
	 * Gets the NCD version present in the XDL design.
	 *
	 * @return The NCD version string.
	 */
	public String getNCDVersion() {
		return this.NCDVersion;
	}

	/**
	 * Determines if this design is a hard macro.
	 *
	 * @return True if this design is a hard macro, false otherwise.
	 */
	public boolean isHardMacro() {
		return this.isHardMacro;
	}

	/**
	 * Adds a module to the design.
	 *
	 * @param module The module to add.
	 */
	public void addModule(Module module) {
		Module prev = modules.put(module.getName(), module);
		if (prev != null && prev != module) {
			throw new RuntimeException("different module with name " + module.getName() + " already exists in design");
		}
	}

	/**
	 * Adds an instance to the design.
	 *
	 * @param inst This instance to add.
	 */
	public void addInstance(Instance inst) {
		if (inst.isPlaced()) {
			setPrimitiveSiteUsed(inst.getPrimitiveSite(), inst);
		}
		inst.setDesign(this);
		instances.put(inst.getName(), inst);
	}

	/**
	 * Adds a net to the design.
	 *
	 * @param net The net to add.
	 */
	public void addNet(Net net) {
		if (nets.put(net.getName(), net) != null) {
			throw new RuntimeException("duplicate net name: " + net.getName());
		}
	}

	/**
	 * Removes a net from the design
	 *
	 * @param name The name of the net to remove.
	 */
	public void removeNet(String name) {
		Net n = getNet(name);
		if (n != null) {
			removeNet(n);
		}
	}

	/**
	 * Removes a net from the design
	 *
	 * @param net The net to remove from the design.
	 */
	public void removeNet(Net net) {
		for (Pin p : net.getPins()) {
			p.getInstance().getNetList().remove(net);
			if (net == p.getNet()) {
				p.setNet(null);
			}
		}
		nets.remove(net.getName());
	}


	/**
	 * This method carefully removes an instance in a design with its
	 * pins and possibly nets.  Nets are only removed if they are empty
	 * after removal of the instance's pins. This method CANNOT remove
	 * instances that are part of a ModuleInstance.
	 *
	 * @param name The instance name in the design to remove.
	 * @return True if the operation was successful, false otherwise.
	 */
	public boolean removeInstance(String name) {
		return removeInstance(getInstance(name));
	}

	/**
	 * This method carefully removes an instance in a design with its
	 * pins and possibly nets.  Nets are only removed if they are empty
	 * after removal of the instance's pins. This method CANNOT remove
	 * instances that are part of a ModuleInstance.
	 *
	 * @param instance The instance in the design to remove.
	 * @return True if the operation was successful, false otherwise.
	 */
	public boolean removeInstance(Instance instance) {
		if (instance.getModuleInstance() != null) {
			return false;
		}
		for (Pin p : instance.getPins()) {
			// TODO - We can sort through PIPs to only remove those that need
			// to be removed, we just need a method to do that
			if (p.getNet() != null) {
				p.getNet().unroute();
				if (p.getNet().getPins().size() == 1) {
					nets.remove(p.getNet().getName());
				} else {
					p.getNet().removePin(p);
				}
			}
		}
		instances.remove(instance.getName());
		releasePrimitiveSite(instance.getPrimitiveSite());
		instance.setDesign(null);
		instance.setNetList(null);
		return true;
	}

	/**
	 * Get a module by name.
	 *
	 * @param name The name of the module to get.
	 * @return The module called name, if it exists, null otherwise.
	 */
	public Module getModule(String name) {
		return modules.get(name);
	}

	/**
	 * Gets the core module that corresponds to this class if it
	 * is a hard macro (a hard macro design will have only one module).
	 *
	 * @return The module of the hard macro, null if this is not a hard macro design.
	 */
	public Module getHardMacro() {
		if (isHardMacro) {
			for (Module module : modules.values()) {
				return module;
			}
		}
		return null;
	}

	/**
	 * Get an instance by name.
	 *
	 * @param name Name of the instance to get.
	 * @return The instance name, or null if it does not exist.
	 */
	public Instance getInstance(String name) {
		return instances.get(name);
	}

	/**
	 * Get an net by name.
	 *
	 * @param name Name of the net to get.
	 * @return The net name, or null if it does not exist.
	 */
	public Net getNet(String name) {
		return nets.get(name);
	}

	/**
	 * Gets and returns all of the instance in the design.
	 *
	 * @return All the instances of the design.
	 */
	public Collection<Instance> getInstances() {
		return instances.values();
	}

	/**
	 * Gets and returns the hash map of instances in the design.
	 *
	 * @return The hash map of instances.
	 */
	public HashMap<String, Instance> getInstanceMap() {
		return instances;
	}

	/**
	 * Returns the set of used primitive sites occupied by this
	 * design's instances and module instances.
	 *
	 * @return The set of used primitive sites in this design.
	 */
	public Set<PrimitiveSite> getUsedPrimitiveSites() {
		return usedPrimitiveSites.keySet();
	}

	/**
	 * Clears out all the used sites in the design, use with caution.
	 */
	public void clearUsedPrimitiveSites() {
		usedPrimitiveSites.clear();
	}

	/**
	 * Gets and returns all of the modules of the design.
	 *
	 * @return All the modules of the design.
	 */
	public Collection<Module> getModules() {
		return modules.values();
	}

	/**
	 * Gets and returns all of the nets of the design.
	 *
	 * @return The hash map of nets.
	 */
	public Collection<Net> getNets() {
		return nets.values();
	}

	/**
	 * Gets and returns the hash map of nets in the design.
	 *
	 * @return All of the nets of the design.
	 */
	public HashMap<String, Net> getNetMap() {
		return nets;
	}

	/**
	 * Gets and returns the wire enumerator for this part.
	 *
	 * @return The wire enumerator for this part.
	 */
	public WireEnumerator getWireEnumerator() {
		return we;
	}

	/**
	 * Sets the WireEnumerator for this design.
	 *
	 * @param we The WireEnumerator to set for this design.
	 */
	public void setWireEnumerator(WireEnumerator we) {
		this.we = we;
	}

	/**
	 * Gets the device specific to this part and returns it. (This should
	 * be the same device loaded with the XDL design file).
	 *
	 * @return The device specific to this part.
	 */
	public Device getDevice() {
		return dev;
	}

	/**
	 * Sets the name of the design
	 *
	 * @param name New name for the design
	 */
	public void setName(String name) {
		this.name = name;
		if (name.equals(hardMacroDesignName)) {
			this.isHardMacro = true;
		}
	}

	/**
	 * Sets the device specific to this part.  Generally only used by the parser
	 * when loading a design, but could be used to convert a design to a different
	 * part (among a host of other transformations).
	 *
	 * @param dev The device to set this design with.
	 */
	public void setDevice(Device dev) {
		this.dev = dev;
	}

	/**
	 * Sets the Xilinx part name, it should include package and speed grade also.
	 * For example xc4vfx12ff668-10 is a valid part name.
	 *
	 * @param partName Name of the Xilinx FPGA part.
	 */
	public void setPartName(String partName) {
		if (this.partName != null) {
			MessageGenerator.briefErrorAndExit("Sorry, cannot change a Design part name" +
					"after one has already been set. Please create a new Design for that.");
		}
		this.partName = partName;
		loadDeviceAndWireEnumerator();
	}

	/**
	 * Sets the NCD version as shown in the XDL file.
	 *
	 * @param ver The NCD version.
	 */
	public void setNCDVersion(String ver) {
		this.NCDVersion = ver;
	}

	/**
	 * Sets the design as a hard macro or not (a hard macro
	 * will have only one module as a member of the design).
	 *
	 * @param value true if it is a hard macro, false otherwise.
	 */
	public void setIsHardMacro(boolean value) {
		this.isHardMacro = value;
	}

	/**
	 * Sets the nets of the design from the ArrayList netList.
	 *
	 * @param netList The new list of nets to replace the current
	 * nets of the design.
	 */
	public void setNets(Collection<Net> netList) {
		nets.clear();
		for (Net net : netList) {
			addNet(net);
		}
	}

	/**
	 * Unroutes the current design by removing all PIPs.
	 */
	public void unrouteDesign() {
		// Just remove all the PIPs
		for (Net net : nets.values()) {
			net.getPIPs().clear();
		}
	}

	public void flattenDesign() {
		if (isHardMacro) {
			MessageGenerator.briefError("ERROR: Cannot flatten a hard macro design");
			return;
		}
		for (ModuleInstance mi : moduleInstances.values()) {
			for (Instance instance : mi.getInstances()) {
				instance.detachFromModule();
			}
			for (Net net : mi.getNets()) {
				net.detachFromModule();
			}
		}
		modules.clear();
	}

	/**
	 * Loads this instance of design with the XDL design found in
	 * the file fileName.
	 *
	 * @param fileName The name of the XDL file to load.
	 */
	public void loadXDLFile(Path fileName) {
		DesignParser parser = new DesignParser(fileName);
		parser.setDesign(this);
		parser.parseXDL();
	}

	/**
	 * Saves the XDL design to a minimalist XDL file.  This is the same
	 * as saveXDLFile(fileName, false);
	 *
	 * @param fileName Name of the file to save the design to.
	 */
	public void saveXDLFile(Path fileName) {
		saveXDLFile(fileName, false, false);
	}

	public float getMaxClkPeriodOfModuleInstances() {
		float maxModulePeriod = 0.0f;
		int missingClockRate = 0;
		for (ModuleInstance mi : getModuleInstances()) {
			float currModuleClkPeriod = mi.getModule().getMinClkPeriod();
			if (currModuleClkPeriod != Float.MAX_VALUE) {
				if (currModuleClkPeriod > maxModulePeriod) {
					maxModulePeriod = currModuleClkPeriod;
				}
			} else {
				missingClockRate++;
			}
		}
		return maxModulePeriod;
	}

	public String getMaxClkPeriodOfModuleInstancesReport() {
		String nl = System.getProperty("line.separator");
		float maxModulePeriod = 0.0f;
		int missingClockRate = 0;
		for (ModuleInstance mi : getModuleInstances()) {
			float currModuleClkPeriod = mi.getModule().getMinClkPeriod();
			if (currModuleClkPeriod != Float.MAX_VALUE) {
				if (currModuleClkPeriod > maxModulePeriod) {
					maxModulePeriod = currModuleClkPeriod;
				}
			} else {
				missingClockRate++;
			}
		}
		StringBuilder sb = new StringBuilder(nl + "Theoretical Min Clock Period: " +
				String.format("%6.3f", maxModulePeriod) +
				" ns (" + 1000.0f * (1 / maxModulePeriod) + " MHz)" + nl);
		if (missingClockRate > 0) {
			sb.append("  (Although, " + missingClockRate + " module instances did not have min clock period stored)" + nl);
		}

		return sb.toString();
	}

	/**
	 * Saves the XDL design and adds comments based on the parameter addComments.
	 *
	 * @param file Name of the file to save the design to.
	 * @param addComments Adds the same comments found in XDL designs created by the
	 * Xilinx xdl tool.
	 */
	public void saveXDLFile(Path file, boolean addComments, boolean sortPips) {
		String nl = System.getProperty("line.separator");

		try {
			PrintWriter pw = new PrintWriter(Files.newOutputStream(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING));

			if (addComments) {
				pw.write(nl + "# =======================================================" + nl);
				pw.write("# " + this.getClass().getCanonicalName() + " XDL Generation $Revision: 1.01$" + nl);
				pw.write("# time: " + FileTools.getTimeString() + nl + nl);
				pw.write("# =======================================================" + nl + nl + nl);

				pw.write("# =======================================================" + nl);
				pw.write("# The syntax for the design statement is:                " + nl);
				pw.write("# design <design_name> <part> <ncd version>;             " + nl);
				pw.write("# or                                                     " + nl);
				pw.write("# design <design_name> <device> <package> <speed> <ncd_version>" + nl);
				pw.write("# =======================================================" + nl);
			}

			comments.forEach(c -> pw.format("# %s%n", c));

			if (!isHardMacro) {
				pw.write("design \"" + name + "\" " + partName + " " + NCDVersion + " ," + nl);
				pw.write("  cfg \"");
				for (Attribute attr : attributes) {
					pw.write(nl + "       " + attr.toString());
				}
				pw.write("\";" + nl + nl + nl);
			} else {
				pw.write("design \"" + name + "\" " + partName + ";" + nl + nl);
			}


			if (modules.size() > 0) {
				if (addComments) {
					pw.write("# =======================================================" + nl);
					pw.write("# The syntax for modules is:" + nl);
					pw.write("#     module <name> <inst_name> ;" + nl);
					pw.write("#     port <name> <inst_name> <inst_pin> ;" + nl);
					pw.write("#     ." + nl);
					pw.write("#     ." + nl);
					pw.write("#     instance ... ;" + nl);
					pw.write("#     ." + nl);
					pw.write("#     ." + nl);
					pw.write("#     net ... ;" + nl);
					pw.write("#     ." + nl);
					pw.write("#     ." + nl);
					pw.write("#     endmodule <name> ;" + nl);
					pw.write("# =======================================================" + nl + nl);
				}

				for (String moduleName : modules.keySet()) {
					Module module = modules.get(moduleName);
					if (addComments) {
						pw.write("# =======================================================" + nl);
						pw.write("# MODULE of \"" + moduleName + "\"" + nl);
						pw.write("# =======================================================" + nl);
					}

					if (module.getAnchor() == null) {
						if (addComments) {
							pw.write("# This module is a routing only block" + nl);
						}
						continue;
					}

					pw.write("module " + "\"" + moduleName + "\" " + "\"" + module.getAnchor().getName() + "\" , cfg \"");

					for (Attribute attr : module.getAttributes()) {
						pw.write(attr.toString() + " ");
					}
					pw.write("\";" + nl);
					for (Port port : module.getPorts()) {
						pw.write("  port \"" + port.getName() + "\" \"" + port.getInstanceName() + "\" \"" + port.getPinName() + "\";" + nl);
					}
					for (Instance inst : module.getInstances()) {
						String placed = inst.isPlaced()? "placed " + inst.getTile() + " " + inst.getPrimitiveSiteName() : "unplaced";
						pw.write("  inst \"" + inst.getName() + "\" \"" + inst.getType() + "\"," + placed + "  ," + nl);
						pw.write("    cfg \"");
						for (Attribute attr : inst.getAttributes()) {
							pw.write(" " + attr.toString());
						}
						pw.write(" \"" + nl + "    ;" + nl);
					}

					writeNets(nl, pw, module.getNets(), sortPips);

					pw.write("endmodule \"" + moduleName + "\" ;" + nl + nl);
				}
			}
			if (!isHardMacro) {
				if (addComments) {
					if (moduleInstances.size() > 0) {
						pw.write(nl);
						pw.write("#  =======================================================" + nl);
						pw.write("#  MODULE INSTANCES" + nl);
						pw.write("#  =======================================================" + nl);
						for (ModuleInstance mi : moduleInstances.values()) {
							pw.write("# instance \"" + mi.getName() + "\" \"" + mi.getModule().getName() + "\" , ");
							if (mi.getAnchor() == null) {
								System.out.println("Anchor is null");
							}
							if (mi.getAnchor() != null && mi.getAnchor().isPlaced()) {
								pw.write("placed " + mi.getAnchor().getTile() + " " +
										mi.getAnchor().getPrimitiveSiteName() + " ;" + nl);
							} else {
								pw.write("unplaced  ;" + nl);
							}
						}
						pw.write(nl);
					}

					pw.write("#  =======================================================" + nl);
					pw.write("#  The syntax for instances is:" + nl);
					pw.write("#      instance <name> <sitedef>, placed <tile> <site>, cfg <string> ;" + nl);
					pw.write("#  or" + nl);
					pw.write("#      instance <name> <sitedef>, unplaced, cfg <string> ;" + nl);
					pw.write("# " + nl);
					pw.write("#  For typing convenience you can abbreviate instance to inst." + nl);
					pw.write("# " + nl);
					pw.write("#  For IOs there are two special keywords: bonded and unbonded" + nl);
					pw.write("#  that can be used to designate whether the PAD of an unplaced IO is" + nl);
					pw.write("#  bonded out. If neither keyword is specified, bonded is assumed." + nl);
					pw.write("# " + nl);
					pw.write("#  The bonding of placed IOs is determined by the site they are placed in." + nl);
					pw.write("# " + nl);
					pw.write("#  If you specify bonded or unbonded for an instance that is not an" + nl);
					pw.write("#  IOB it is ignored." + nl);
					pw.write("# " + nl);
					pw.write("#  Shown below are three examples for IOs. " + nl);
					pw.write("#     instance IO1 IOB, unplaced ;          # This will be bonded" + nl);
					pw.write("#     instance IO1 IOB, unplaced bonded ;   # This will be bonded" + nl);
					pw.write("#     instance IO1 IOB, unplaced unbonded ; # This will be unbonded" + nl);
					pw.write("#  =======================================================" + nl);
				}
				for (Instance inst : getInstances()) {
					String placed = inst.isPlaced()? "placed " + inst.getTile() +
							" " + inst.getPrimitiveSiteName() : "unplaced";
					String module = inst.getModuleInstanceName() == null? "" : "module \"" +
							inst.getModuleInstanceName() + "\" \"" + inst.getModuleTemplate().getName() + "\" \"" +
							inst.getModuleTemplateInstance().getName() + "\" ,";
					pw.write("inst \"" + inst.getName() + "\" \"" + inst.getType() + "\"," + placed + "  ," + module + nl);
					pw.write("  cfg \"");
					for (Attribute attr : inst.getAttributes()) {
						if (attr.getPhysicalName().charAt(0) == '_') {
							pw.write(nl + "      ");
						}
						pw.write(" " + attr.toString());
					}
					pw.write(" \"" + nl + "  ;" + nl);
				}
				pw.write(nl);

				if (addComments) {
					pw.write("#  ================================================" + nl);
					pw.write("#  The syntax for nets is:" + nl);
					pw.write("#     net <name> <type>," + nl);
					pw.write("#       outpin <inst_name> <inst_pin>," + nl);
					pw.write("#       ." + nl);
					pw.write("#       ." + nl);
					pw.write("#       inpin <inst_name> <inst_pin>," + nl);
					pw.write("#       ." + nl);
					pw.write("#       ." + nl);
					pw.write("#       pip <tile> <wire0> <dir> <wire1> , # [<rt>]" + nl);
					pw.write("#       ." + nl);
					pw.write("#       ." + nl);
					pw.write("#       ;" + nl);
					pw.write("# " + nl);
					pw.write("#  There are three available wire types: wire, power and ground." + nl);
					pw.write("#  If no type is specified, wire is assumed." + nl);
					pw.write("# " + nl);
					pw.write("#  Wire indicates that this a normal wire." + nl);
					pw.write("#  Power indicates that this net is tied to a DC power source." + nl);
					pw.write("#  You can use \"power\", \"vcc\" or \"vdd\" to specify a power net." + nl);
					pw.write("# " + nl);
					pw.write("#  Ground indicates that this net is tied to ground." + nl);
					pw.write("#  You can use \"ground\", or \"gnd\" to specify a ground net." + nl);
					pw.write("# " + nl);
					pw.write("#  The <dir> token will be one of the following:" + nl);
					pw.write("# " + nl);
					pw.write("#     Symbol Description" + nl);
					pw.write("#     ====== ==========================================" + nl);
					pw.write("#       ==   Bidirectional, unbuffered." + nl);
					pw.write("#       =>   Bidirectional, buffered in one direction." + nl);
					pw.write("#       =-   Bidirectional, buffered in both directions." + nl);
					pw.write("#       ->   Directional, buffered." + nl);
					pw.write("# " + nl);
					pw.write("#  No pips exist for unrouted nets." + nl);
					pw.write("#  ================================================" + nl);
				}

				writeNets(nl, pw, getNets(), sortPips);

				pw.write(nl);

				if (addComments) {
					int sliceCount = 0;
					int bramCount = 0;
					int dspCount = 0;
					for (Instance instance : instances.values()) {
						PrimitiveType type = instance.getType();
						if (sliceTypes.contains(type)) {
							sliceCount++;
						} else if (dspTypes.contains(type)) {
							dspCount++;
						} else if (bramTypes.contains(type)) {
							bramCount++;
						}
					}

					pw.write("# =======================================================" + nl);
					pw.write("# SUMMARY" + nl);
					pw.write("# Number of Module Defs: " + modules.size() + nl);
					pw.write("# Number of Module Insts: " + moduleInstances.size() + nl);
					pw.write("# Number of Primitive Insts: " + instances.size() + nl);
					pw.write("#     Number of SLICES: " + sliceCount + nl);
					pw.write("#     Number of DSP48s: " + dspCount + nl);
					pw.write("#     Number of BRAMs: " + bramCount + nl);
					pw.write("# Number of Nets: " + nets.size() + nl);
					pw.write("# =======================================================" + nl + nl + nl);
				}
			} else {
				if (addComments) {
					Module mod = getHardMacro();
					pw.write("# =======================================================" + nl);
					pw.write("# MACRO SUMMARY" + nl);
					pw.write("# Number of Module Insts: " + mod.getInstances().size() + nl);
					HashMap<PrimitiveType, Integer> instTypeCount = new HashMap<PrimitiveType, Integer>();
					for (Instance inst : mod.getInstances()) {
						Integer count = instTypeCount.get(inst.getType());
						if (count == null) {
							instTypeCount.put(inst.getType(), 1);
						} else {
							count++;
							instTypeCount.put(inst.getType(), count);
						}
					}
					for (PrimitiveType type : instTypeCount.keySet()) {
						pw.write("#   Number of " + type.toString() + "s: " + instTypeCount.get(type) + nl);
					}
					pw.write("# Number of Module Ports: " + mod.getPorts().size() + nl);
					pw.write("# Number of Module Nets: " + mod.getNets().size() + nl);
					pw.write("# =======================================================" + nl + nl + nl);
				}
			}
			pw.close();
		} catch (IOException e) {
			logger.error("Failed to write XDL File {}", file, e);
			MessageGenerator.briefErrorAndExit("Error writing XDL file: " +
					file + File.separator + e.getMessage());
		}
	}

	private void writeNets(String nl, PrintWriter pw, Collection<Net> nets, boolean sortEntries) {
		nets.stream().sorted(Comparator.comparing(Net::getName)).forEach(net -> {
			pw.write("  net \"" + net.getName() + "\" " + net.getType().toString() + ",");
			if (net.getAttributes() != null) {
				pw.write("cfg \"");
				for (Attribute attr : net.getAttributes()) {
					pw.write(" " + attr.toString());
				}
				pw.write("\",");
			}
			pw.write(nl);
			Stream<Pin> pinStream = net.getPins().stream();
			if (sortEntries) {
				pinStream = pinStream.sorted(Comparator.<Pin, Boolean>comparing(p -> net.getSource() != p).thenComparing(Comparator.comparing(Pin::getInstanceName)));
			}
			pinStream.forEach(pin -> {
				pw.write("  " + pin.getPinType().toString().toLowerCase() + " \"" + pin.getInstanceName() + "\" " + pin.getName() + " ," + nl);
			});

			if (net.getPIPs().stream().anyMatch(pip -> pip.getTile() == null || pip.getDirection() == null)) {
				throw new RuntimeException("invalid pip for net " + net + ": " + net.getPIPs());
			}
			Stream<PIP> pipStream = net.getPIPs().stream();
			if (sortEntries) {
				pipStream = pipStream.sorted(Comparator.<PIP, String>comparing(pip -> pip.getTile().toString()).thenComparing(pip -> pip.getStartWireName(we)));
			}
			pipStream.forEach(pip -> {
				pw.write("  pip " + pip.getTile() + " " + pip.getStartWireName(we) + " " + pip.getDirection().connString + " " + pip.getEndWireName(we) + " , " + nl);
			});
			pw.write("  ;" + nl);
		});
	}

	public void saveComparableXDLFile(Path fileName) {
		saveXDLFile(fileName, true, true);
	}

	static {
		sliceTypes = new HashSet<PrimitiveType>();
		sliceTypes.add(PrimitiveType.SLICE);
		sliceTypes.add(PrimitiveType.SLICEL);
		sliceTypes.add(PrimitiveType.SLICEM);
		sliceTypes.add(PrimitiveType.SLICEX);

		dspTypes = new HashSet<PrimitiveType>();
		dspTypes.add(PrimitiveType.DSP48);
		dspTypes.add(PrimitiveType.DSP48A);
		dspTypes.add(PrimitiveType.DSP48A1);
		dspTypes.add(PrimitiveType.DSP48E);
		dspTypes.add(PrimitiveType.DSP48E1);
		dspTypes.add(PrimitiveType.MULT18X18);
		dspTypes.add(PrimitiveType.MULT18X18SIO);

		bramTypes = new HashSet<PrimitiveType>();
		bramTypes.add(PrimitiveType.BLOCKRAM);
		bramTypes.add(PrimitiveType.FIFO16);
		bramTypes.add(PrimitiveType.FIFO18E1);
		bramTypes.add(PrimitiveType.FIFO36_72_EXP);
		bramTypes.add(PrimitiveType.FIFO36_EXP);
		bramTypes.add(PrimitiveType.FIFO36E1);
		bramTypes.add(PrimitiveType.RAMB16);
		bramTypes.add(PrimitiveType.RAMB16BWE);
		bramTypes.add(PrimitiveType.RAMB16BWER);
		bramTypes.add(PrimitiveType.RAMB18E1);
		bramTypes.add(PrimitiveType.RAMB18X2);
		bramTypes.add(PrimitiveType.RAMB18X2SDP);
		bramTypes.add(PrimitiveType.RAMB36_EXP);
		bramTypes.add(PrimitiveType.RAMB36E1);
		bramTypes.add(PrimitiveType.RAMB36SDP_EXP);
		bramTypes.add(PrimitiveType.RAMB8BWER);
		bramTypes.add(PrimitiveType.RAMBFIFO18);
		bramTypes.add(PrimitiveType.RAMBFIFO18_36);
		bramTypes.add(PrimitiveType.RAMBFIFO36);
		bramTypes.add(PrimitiveType.RAMBFIFO36E1);

		iobTypes = new HashSet<PrimitiveType>();
		iobTypes.add(PrimitiveType.IOB);
		iobTypes.add(PrimitiveType.IOB18);
		iobTypes.add(PrimitiveType.IOB18M);
		iobTypes.add(PrimitiveType.IOB18S);
		iobTypes.add(PrimitiveType.IOB33);
		iobTypes.add(PrimitiveType.IOB33M);
		iobTypes.add(PrimitiveType.IOB33S);
		iobTypes.add(PrimitiveType.IOB_USB);
		iobTypes.add(PrimitiveType.IOBLR);
		iobTypes.add(PrimitiveType.IOBM);
		iobTypes.add(PrimitiveType.IOBS);
		iobTypes.add(PrimitiveType.LOWCAPIOB);
	}

	/**
	 * Creates two CSV files based on this design, one for instances and one
	 * for nets.
	 *
	 * @param fileName
	 */
	public void toCSV(String fileName) {
		String nl = System.getProperty("line.separator");
		try {
			BufferedWriter bw = new BufferedWriter(new FileWriter(fileName + ".instances.csv"));
			bw.write("\"Name\",\"Type\",\"Site\",\"Tile\",\"#Pins\"" + nl);

			for (Instance i : instances.values()) {
				bw.write("\"" + i.getName() + "\",\"" +
						i.getType() + "\",\"" +
						i.getPrimitiveSiteName() + "\",\"" +
						i.getTile() + "\",\"" +
						i.getPinMap().size() + "\"" + nl);
			}
			bw.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		try {
			BufferedWriter bw = new BufferedWriter(new FileWriter(fileName + ".nets.csv"));
			bw.write("\"Name\",\"Type\",\"Fanout\"" + nl);

			for (Net n : nets.values()) {
				bw.write("\"" + n.getName() + "\",\"" +
						n.getType() + "\",\"" +
						n.getFanOut() + "\"" + nl);
			}
			bw.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private Map<String, InstanceElement> registerLogicalNameMap = null;

	public synchronized Map<String, InstanceElement> getRegisterLogicalNameMap(PrimitiveDefList primitiveDefs) {
		if (registerLogicalNameMap == null) {
			registerLogicalNameMap = new HashMap<>();
			for (Instance instance : instances.values()) {
				PrimitiveDef prim = primitiveDefs.getPrimitiveDef(instance.getType());
				for (Attribute attribute : instance.getAttributes()) {
					if (!attribute.getLogicalName().isEmpty()) {
						String physicalName = attribute.getPhysicalName();
						Element elem = null;
						if (physicalName.endsWith("LUT")) {
							elem = prim.getElement(physicalName + "WRITE");
						}
						if (elem == null) {
							elem = prim.getElement(physicalName);
						}
						if (elem == null) {
							if (physicalName.startsWith("_")) {
								continue;
							} else {
								throw new RuntimeException("Could not find " + physicalName + " in " + prim.getType() + " named " + attribute.getLogicalName());
							}
						}
						InstanceElement ie = instance.getInstanceElement(elem, primitiveDefs);

						if (ie.isRegister(primitiveDefs) == RoutingElement.RegisterType.REGISTER || ie.isRegister(primitiveDefs) == RoutingElement.RegisterType.LATCH) {
							InstanceElement oldElem = registerLogicalNameMap.put(attribute.getLogicalName(), ie);
							if (oldElem != null) {
								throw new RuntimeException("Duplicate logicalName " + attribute.getLogicalName() + ": " + ie + " and " + oldElem);
							}
						}
					}
				}
			}
		}
		return registerLogicalNameMap;
	}


	/**
	 * Work around bug in XDL -&gt; NCD conversion on spartan 6
	 *
	 * The attribute PRE_EMPHASIS is wrongly added to all inputs. Without removing this attribute, all inputs are
	 * considered tri-stated. XDL-&gt;NCD conversion then fails because of missing slew rate attributes
	 */
	public void fixSp6IOBs() {
		getInstances().forEach(i -> {
			Attribute a = i.getAttribute("PRE_EMPHASIS");
			i.getAttributes().remove(a);
		});
	}

	/**
	 * Add a comment that always will be saved to the output file
	 *
	 * @param comment
	 */
	public void addComment(String comment) {
		comments.add(comment);
	}
}
