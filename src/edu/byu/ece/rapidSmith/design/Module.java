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

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIdentityReference;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import edu.byu.ece.rapidSmith.design.jackson.InstanceSetToMapConverter;
import edu.byu.ece.rapidSmith.design.jackson.MapToSetConverter;
import edu.byu.ece.rapidSmith.design.jackson.NetSetToMapConverter;
import edu.byu.ece.rapidSmith.design.jackson.PortSetToMapConverter;
import edu.byu.ece.rapidSmith.device.Device;
import edu.byu.ece.rapidSmith.device.PrimitiveSite;
import edu.byu.ece.rapidSmith.device.PrimitiveType;
import edu.byu.ece.rapidSmith.device.Tile;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This class represents the modules as found in XDL.  They are used to describe
 * hard macros and RPMs and instances of each.
 *
 * @author Chris Lavin
 * Created on: Jun 22, 2010
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.NONE, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE, setterVisibility = JsonAutoDetect.Visibility.NONE)
@JsonPropertyOrder({"name", "device", "attrs", "ports", "insts", "nets"})
public class Module implements Serializable, Cloneable {

	private static final long serialVersionUID = 7127893920489370872L;
	/**
	 * This is the key into externalPortMap for retrieving the constraints used to build the hard macro
	 */
	public static final String moduleBuildConstraints = "MODULE_BUILD_CONSTRAINTS";
	/**
	 * Unique name of this module
	 */
	@JsonProperty("name")
	private String name;
	/**
	 * All of the attributes in this module
	 */
	@JsonProperty("attrs")
	private List<Attribute> attributes;
	/**
	 * This is the anchor of the module
	 */
	@JsonProperty("anchor")
	@JsonIdentityReference(alwaysAsId = true)
	private Instance anchor;
	/**
	 * Ports on the module
	 */
	@JsonProperty("ports")
	@JsonSerialize(converter = MapToSetConverter.class)
	@JsonDeserialize(converter = PortSetToMapConverter.class)
	private Map<String, Port> portMap;
	/**
	 * Instances which are part of the module
	 */
	@JsonProperty("insts")
	@JsonIdentityReference
	@JsonSerialize(converter = MapToSetConverter.class)
	@JsonDeserialize(converter = InstanceSetToMapConverter.class)
	private Map<String, Instance> instanceMap;
	/**
	 * Nets of the module
	 */
	@JsonProperty("nets")
	@JsonIdentityReference
	@JsonSerialize(converter = MapToSetConverter.class)
	@JsonDeserialize(converter = NetSetToMapConverter.class)
	private Map<String, Net> netMap;
	/**
	 * Keeps track of the minimum clock period of this module
	 */
	private float minClkPeriod = Float.MAX_VALUE;
	/**
	 * Provides a catch-all map to store information about hard macro
	 */
	private Map<String, List<String>> metaDataMap;

	private ArrayList<PrimitiveSite> validPlacements;

	/**
	 * Empty constructor, strings are null, everything else is initialized
	 */
	public Module() {
		name = null;
		anchor = null;
		attributes = new ArrayList<Attribute>();
		portMap = new HashMap<String, Port>();
		instanceMap = new HashMap<String, Instance>();
		netMap = new HashMap<String, Net>();
		validPlacements = new ArrayList<PrimitiveSite>();
	}

	/**
	 * Creates and returns a new hard macro design with the appropriate
	 * settings and adds this module to the module list.
	 *
	 * @return A complete hard macro design with this module as the hard macro.
	 */
	public Design createDesignFromModule(String partName) {
		Design design = new Design();
		design.setPartName(partName);
		design.setName(Design.hardMacroDesignName);
		design.setIsHardMacro(true);
		design.addModule(this);
		return design;
	}

	/**
	 * Gets and returns the device this module was made for via the tile the anchor is placed in
	 *
	 * @return the device this module was made for
	 */
	@JsonProperty("device")
	public Device getDevice() {
		return anchor.getTile().getDevice();
	}

	/**
	 * WARNING: NOOP
	 * This is used to trick jackson on deserialization
	 */
	@JsonProperty("device")
	private void __setDevice(Device device) {
	}

	/**
	 * Sets the name of this module
	 *
	 * @param name New name for this module
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * Gets and returns the current name of this module
	 *
	 * @return The current name of this module
	 */
	public String getName() {
		return name;
	}

	/**
	 * Gets and returns the current attributes of this module
	 *
	 * @return The current attributes of this module
	 */
	public List<Attribute> getAttributes() {
		return attributes;
	}

	/**
	 * Adds the attribute with value to this module.
	 *
	 * @param physicalName Physical name of the attribute.
	 * @param value Value to set the new attribute to.
	 */
	public void addAttribute(String physicalName, String logicalName, String value) {
		attributes.add(new Attribute(physicalName, logicalName, value));
	}

	/**
	 * Add the attribute to this module.
	 *
	 * @param attribute The attribute to add.
	 */
	public void addAttribute(Attribute attribute) {
		attributes.add(attribute);
	}

	/**
	 * Checks if the design attribute has an attribute with a physical
	 * name called physicalName.
	 *
	 * @param physicalName The physical name of the attribute to check for.
	 * @return True if this module contains an attribute with the
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
	 * Sets the list of attributes for this module.
	 *
	 * @param attributes The new list of attributes to associate with this
	 * module.
	 */
	public void setAttributes(ArrayList<Attribute> attributes) {
		this.attributes = attributes;
	}


	/**
	 * This gets and returns the instance anchor of the module.
	 *
	 * @return Instance which is the anchor for this module.
	 */
	public Instance getAnchor() {
		return anchor;
	}

	/**
	 * Gets and returns the instance in the module called name.
	 *
	 * @param name Name of the instance in the module to get.
	 * @return The instance name or null if it does not exist.
	 */
	public Instance getInstance(String name) {
		return instanceMap.get(name);
	}

	/**
	 * Gets and returns all of the instances part of this module.
	 *
	 * @return The instances that are part of this module.
	 */
	public Collection<Instance> getInstances() {
		return instanceMap.values();
	}

	/**
	 * Gets and returns the net in the module called name.
	 *
	 * @param name Name of the net in the module to get.
	 * @return The net name or null if it does not exist.
	 */
	public Net getNet(String name) {
		return netMap.get(name);
	}

	/**
	 * Gets and returns all the nets that are part of the module.
	 *
	 * @return The nets that are part of this module.
	 */
	public Collection<Net> getNets() {
		return netMap.values();
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
			if (p.getNet().equals(net)) {
				p.setNet(null);
			}
		}
		netMap.remove(net.getName());
	}


	/**
	 * This method carefully removes an instance in a design with its
	 * pins and possibly nets.  Nets are only removed if they are empty
	 * after removal of the instance's pins. This method CANNOT remove
	 * instances that are part of a ModuleInstance.
	 *
	 * @param name The instance name in the module to remove.
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
			p.getNet().unroute();
			if (p.getNet().getPins().size() == 1) {
				netMap.remove(p.getNet().getName());
			} else {
				p.getNet().removePin(p);
			}
		}
		instanceMap.remove(instance.getName());
		instance.setDesign(null);
		instance.setNetList(null);
		instance.setModuleTemplate(null);
		return true;
	}


	/**
	 * Sets the anchor instance for this module.
	 *
	 * @param anchor New anchor instance for this module.
	 */
	public void setAnchor(Instance anchor) {
		this.anchor = anchor;
	}

	/**
	 * Gets and returns the port list for this module.
	 *
	 * @return The port list for this module.
	 */
	public Collection<Port> getPorts() {
		return portMap.values();
	}

	/**
	 * Sets the port list for this module.
	 *
	 * @param portList The new port list to be set for this module.
	 */
	public void setPorts(Collection<Port> portList) {
		portMap.clear();
		for (Port p : portList) {
			addPort(p);
		}
	}

	private void addPortToMap(String name, Port port) {
		Port old;
		if ((old = this.portMap.put(name, port)) != null) {
			throw new RuntimeException("port name " + name + " already exists in module " + getName() + ". Old pins: " + old.getPins() + ", new pins: " + port.getPins() + ", " + (old == port));
		}
	}

	/**
	 * Adds a port to this module.
	 *
	 * @param port The new port to add.
	 */
	public void addPort(Port port) {
		addPortToMap(port.getName(), port);
		port.getAliases().forEach(alias -> {
			addPortToMap(alias, port);
		});
	}

	/**
	 * Returns the port with the given name.
	 *
	 * @param name the port's name
	 * @return the port
	 */
	public Port getPort(String name) {
		return this.portMap.get(name);
	}

	/**
	 * Adds a net to this module.
	 *
	 * @param net The net to add to the module.
	 */
	public void addNet(Net net) {
		this.netMap.put(net.getName(), net);
	}

	/**
	 * Adds an instance to this module.
	 *
	 * @param inst The instance to add to the module.
	 */
	public void addInstance(Instance inst) {
		this.instanceMap.put(inst.getName(), inst);
	}

	/**
	 * @return the metaDataMap
	 */
	public Map<String, List<String>> getMetaDataMap() {
		return metaDataMap;
	}

	/**
	 * @param metaDataMap the metaDataMap to set
	 */
	public void setMetaDataMap(Map<String, List<String>> metaDataMap) {
		this.metaDataMap = metaDataMap;
	}

	/**
	 * @param minClkPeriod the minClkPeriod to set
	 */
	public void setMinClkPeriod(float minClkPeriod) {
		this.minClkPeriod = minClkPeriod;
	}

	/**
	 * @return the minClkPeriod
	 */
	public float getMinClkPeriod() {
		return minClkPeriod;
	}

	/**
	 * Sets the design in all the module's instances to null
	 */
	public void disconnectDesign() {
		for (Instance i : this.getInstances()) {
			i.setDesign(null);
		}
	}

	/**
	 * Does a brute force search to find all valid locations of where this module
	 * can be placed.
	 *
	 * @return A list of valid anchor sites for the module to be placed.
	 */
	public ArrayList<PrimitiveSite> calculateAllValidPlacements(Device dev) {
		if (getAnchor() == null) {
			return null;
		}
		ArrayList<PrimitiveSite> validSites = new ArrayList<PrimitiveSite>();
		PrimitiveSite[] sites = dev.getAllCompatibleSites(getAnchor().getType());
		for (PrimitiveSite newAnchorSite : sites) {
			if (isValidPlacement(newAnchorSite, dev)) {
				validSites.add(newAnchorSite);
			}
		}
		this.validPlacements = validSites;
		return validSites;
	}

	/**
	 * Gets the previously calculated valid placement locations for this particular module.
	 *
	 * @return A list of anchor primitive sites which are valid for this module.
	 */
	public ArrayList<PrimitiveSite> getAllValidPlacements() {
		return this.validPlacements;
	}

	public boolean isValidPlacement(PrimitiveSite proposedAnchorSite, Device dev) {
		// Check if parameters are null
		if (proposedAnchorSite == null || dev == null) {
			return false;
		}

		// Do some error checking on the newAnchorSite
		PrimitiveSite p = anchor.getPrimitiveSite();
		Tile t = proposedAnchorSite.getTile();
		PrimitiveSite newValidSite = Device.getCorrespondingPrimitiveSite(p, anchor.getType(), t);
		if (!proposedAnchorSite.equals(newValidSite)) {
			return false;
		}

		//=======================================================//
		/* Check instances at proposed location                  */
		//=======================================================//
		for (Instance inst : getInstances()) {
			PrimitiveSite templateSite = inst.getPrimitiveSite();
			Tile newTile = getCorrespondingTile(templateSite.getTile(), proposedAnchorSite.getTile(), dev);
			if (newTile == null) {
				return false;
			}
			if (Device.getCorrespondingPrimitiveSite(templateSite, inst.getType(), newTile) == null) {
				return false;
			}
		}

		//=======================================================//
		/* Check nets at proposed location                       */
		//=======================================================//
		for (Net net : getNets()) {
			for (PIP pip : net.getPIPs()) {
				if (getCorrespondingTile(pip.getTile(), proposedAnchorSite.getTile(), dev) == null) {
					return false;
				}
			}
		}
		return true;
	}


	private static Map<String, Set<String>> compatibleTiles = null;

	public static String baseName(String s) {
		int i = s.lastIndexOf("_");
		if (i == -1) {
			return s;
		}
		return s.substring(0, i);
	}

	public static Map<String, Set<String>> getCompatibleTiles(Device device) {
		fillCompatibleTiles(device);
		return compatibleTiles;
	}

	private static void fillCompatibleTiles(Device device) {

		//TODO clean up, run on xdlrc parsing
		if (compatibleTiles != null) {
			return;
		}

		//Sometimes, the basename of tiles is not the same as their tile type. As we use the basename for moving,
		//we use the basename for compatibility as well.

		Map<PrimitiveType, Set<String>> primTypeToContainingTiles =
				//Get all tiles
				device.getTileMap().values().stream()
						//Get their Primitive sites
						.flatMap(tile -> {
							PrimitiveSite[] sites = tile.getPrimitiveSites();
							if (sites == null) {
								return Stream.empty();
							}
							return Stream.of(sites);
						})
						//To Map prim type -> basename of tiles containing them
						.collect(
								Collectors.groupingBy(
										PrimitiveSite::getType,
										Collectors.mapping((PrimitiveSite ps) -> baseName(ps.getTile().getName()), Collectors.toSet())
								)
						);


		//All tiles containing the same primitive type are compatible with each other
		compatibleTiles = new HashMap<>();
		primTypeToContainingTiles.forEach((k, v) -> {
			PrimitiveType[] compTypes = k.getCompatibleTypes(device);
			v.forEach(tileType -> {
				Set<String> siteMap = compatibleTiles.computeIfAbsent(tileType, (x) -> new HashSet<>());
				//Add all other sites
				Stream<String> fromType = v.stream();
				Stream<String> fromCompType;
				if (compTypes != null) {
					fromCompType = Arrays.stream(compTypes).map(primTypeToContainingTiles::get).flatMap(Collection::stream);
				} else {
					fromCompType = Stream.empty();
				}

				Stream.concat(fromType, fromCompType).forEach(x -> {
					if (!x.equals(tileType)) {
						siteMap.add(x);
					}
				});
			});
		});
	}

	/**
	 * This method will calculate and return the corresponding tile of a module
	 * for a new anchor location.
	 *
	 * @param templateTile The tile in the module which acts as a template.
	 * @param tileXOffset offset in X
	 * @param tileYOffset offset in Y
	 * @param dev The device which corresponds to this module.
	 * @return The new tile of the module instance which corresponds to the templateTile, or null
	 * if none exists.
	 */
	public static Tile getCorrespondingTile(Tile templateTile, int tileXOffset, int tileYOffset, Device dev) {
		int newTileX = templateTile.getTileXCoordinate() + tileXOffset;
		int newTileY = templateTile.getTileYCoordinate() + tileYOffset;
		String oldName = templateTile.getName();


		String newName = oldName.substring(0, oldName.lastIndexOf('X') + 1) + newTileX + "Y" + newTileY;
		Tile correspondingTile = dev.getTile(newName);
		if (correspondingTile == null) {
			fillCompatibleTiles(dev);

			Set<String> comp = compatibleTiles.get(baseName(templateTile.getName()));
			if (comp != null) {
				for (String s : comp) {
					Tile t = dev.getTile(s + "_X" + newTileX + "Y" + newTileY);
					if (t != null) {
						//if (newTileX==5 && newTileY==38)
						//	System.out.println("we actually found something!! "+newName+" is actually "+t.getName());
						return t;
					}
				}
			}


			/*if(templateTile.getType().equals(TileType.CLBLL)){
				correspondingTile = dev.getTile("CLBLM_X" + newTileX + "Y" + newTileY);
			}else if(templateTile.getType().equals(TileType.CLBLM)){
				correspondingTile = dev.getTile("CLBLL_X" + newTileX + "Y" + newTileY);
			}


			if(templateTile.getType().equals(TileType.CLEXL)){
				correspondingTile = dev.getTile("CLEXM_X" + newTileX + "Y" + newTileY);
			}else if(templateTile.getType().equals(TileType.CLEXM)){
				correspondingTile = dev.getTile("CLEXL_X" + newTileX + "Y" + newTileY);
			}*/
		}
		return correspondingTile;
	}

	/**
	 * This method will calculate and return the corresponding tile of a module
	 * for a new anchor location.
	 *
	 * @param templateTile The tile in the module which acts as a template.
	 * @param newAnchorTile This is the tile of the new anchor instance of the module.
	 * @param dev The device which corresponds to this module.
	 * @return The new tile of the module instance which corresponds to the templateTile, or null
	 * if none exists.
	 */
	public Tile getCorrespondingTile(Tile templateTile, Tile newAnchorTile, Device dev) {


		int tileXOffset = newAnchorTile.getTileXCoordinate() - anchor.getTile().getTileXCoordinate();
		int tileYOffset = newAnchorTile.getTileYCoordinate() - anchor.getTile().getTileYCoordinate();
		return getCorrespondingTile(templateTile, tileXOffset, tileYOffset, dev);

	}

	/**
	 * Generates the hashCode strictly on the module name.
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((name == null)? 0 : name.hashCode());
		return result;
	}

	/**
	 * Checks if two modules are equal based on the name of the module.
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		Module other = (Module) obj;
		if (name == null) {
			if (other.name != null) {
				return false;
			}
		} else if (!name.equals(other.name)) {
			return false;
		}
		return true;
	}

	public String toString() {
		return name;
	}

	private Function<Pin, Pin> mapPin(Module clone) {
		Objects.requireNonNull(clone);
		return pin -> {
			if (pin==null) {
				throw new NullPointerException("Pin null!");
			}
			final Instance instance = clone.getInstance(pin.getInstanceName());
			if (instance == null) {
				throw new NullPointerException("Could not find instance " + pin.getInstanceName() + " in cloned module");
			}
			return instance.getPin(pin.getName());
		};
	}

	private <T extends INamed> Collector<T, ?, Map<String, T>> collectNamed() {
		return Collectors.toMap(T::getName, Function.identity());
	}

	public Module clone() {
		Module clone = new Module();
		final Function<Pin, Pin> mapPin = mapPin(clone);
		clone.name = name;
		clone.attributes = attributes.stream().map(Attribute::clone).collect(Collectors.toList());
		clone.instanceMap = instanceMap.values().stream().map(Instance::clone).collect(collectNamed());
		clone.anchor = clone.getInstance(anchor.getName());
		clone.minClkPeriod = minClkPeriod;
		if (metaDataMap != null) {
			clone.metaDataMap = metaDataMap.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> new ArrayList<>(e.getValue())));
		}
		clone.validPlacements = new ArrayList<>(validPlacements);
		clone.netMap = netMap.values().stream().map(net -> net.clone(mapPin)).collect(collectNamed());
		clone.portMap = portMap.values().stream().map(port -> port.clone(mapPin)).collect(collectNamed());

		//rebuild stuff that was not cloned
		clone.netMap.values().stream().forEach(net -> {
			for (Pin pin : net.getPins()) {
				pin.setNet(net);
				pin.getInstance().getNetList().add(net);
			}
		});

		for (Port originalPort : portMap.values()) {
			Port clonePort = clone.getPort(originalPort.getName());
			for (Port originalPassthrough : originalPort.getPassthroughPorts()) {
				Port clonePassthrough = clone.getPort(originalPassthrough.getName());
				clonePort.addPassthroughPort(clonePassthrough);
			}

			for (Pin pin : clonePort.getPins()) {
				pin.setPort(clonePort);
			}
		}
		return clone;
	}
}
