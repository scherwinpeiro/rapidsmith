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
import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIdentityReference;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This class represents the ports used to define the interfaces of modules
 * in XDL.  They consist of a unique name, the instance to which they are
 * connected and a pin name on the instance.
 * @author Chris Lavin
 * Created on: Jun 22, 2010
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.NONE, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE, setterVisibility = JsonAutoDetect.Visibility.NONE)
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
@JsonIdentityInfo(property = "name", generator = ObjectIdGenerators.PropertyGenerator.class, scope = Port.class)
public class Port implements Serializable, Cloneable, INamed{

	private static final long serialVersionUID = -8961782654770650827L;
	/** Name of the Port of the current module, this is the port of an instance in the module. */
	@JsonProperty("name")
	private String name;
	/** This is the pin that the port references. */
	private List<Pin> pins = new ArrayList<>();
	@JsonProperty("aliases")
	private List<String> aliases = new ArrayList<>();
	@JsonProperty("type")
	private NetType netType = NetType.WIRE;
	@JsonProperty("passthroughPorts")
	@JsonIdentityReference(alwaysAsId=true)
	private final List<Port> passthroughPorts = new ArrayList<>();
	@JsonProperty("passthroughFrom")
	@JsonIdentityReference(alwaysAsId=true)
	private Port passthroughFrom;

	/**
	 * Default constructor, everything is null.
	 */
	public Port(){
		name = null;
		setPin(null);
	}
	

	/**
	 * @param name Name of the port.
	 * @param pin Pin which the port references
	 */
	public Port(String name, Pin pin){
		this.name = name;
		this.setPin(pin);
	}
	

	/**
	 * Gets and returns the name of the port.
	 * @return The name of the port.
	 */
	public String getName(){
		return name;
	}
	
	/**
	 * Sets the name of the port.
	 * @param name The new name of the port.
	 */
	public void setName(String name){
		this.name = name;
	}
	
	/**
	 * Gets and returns the instance name.
	 * @return The name of the instance where this port resides.
	 */
	public String getInstanceName(){
		return getPin().getInstanceName();
	}
	
	/**
	 *  Gets the pin name of the instance where the port resides.
	 * @return The pin name of the port.
	 */
	public String getPinName(){
		return getPin().getName();
	}
	

	/**
	 * @param pin the pin to set
	 */
	public void setPin(Pin pin) {
		if (pin==null)
			pins.clear();
		else
			this.pins = new ArrayList<>(Collections.singletonList(pin));
	}

	/**
	 * @return the pin
	 */
	public Pin getPin() {
		if (pins.isEmpty())
			return null;
		if (pins.size()>1)
			throw new RuntimeException("In Port "+name+", pins does not contain exactly one pin: "+pins);
		return pins.get(0);
	}

	/**
	 * @return the instance
	 */
	public Instance getInstance() {
		return getPin().getInstance();
	}

	/**
	 * Simply looks at the pin of the port to determine
	 * its direction.
	 * @return True if this port is an output, false otherwise.
	 */
	public Boolean isOutPort(){
		if (pins.size()==0)
			return null;
		return pins.get(0).isOutPin();
	}
	
	/**
	 * Generates hashCode for this port based on instance name, port name, and pin name.
	 */
	@Override
	public int hashCode(){
		final int prime = 31;
		int result = 1;
		for(Pin pin:getPins()) {
			result = prime * result + (Objects.isNull(pin.getInstanceName())? 0 : pin.getInstanceName().hashCode());
			result = prime * result + (Objects.isNull(pin.getName()) ? 0 : pin.getName().hashCode());
		}
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		return result;
	}

	/**
	 * Checks if this and obj are equal ports by comparing port name,
	 * instance name and pin name.
	 */
	@Override
	public boolean equals(Object obj){
		if(this == obj)
			return true;
		if(obj == null)
			return false;
		if(getClass() != obj.getClass())
			return false;
		Port other = (Port) obj;

		if(null == name) {
			if(null != other.name)
				return false;
		}
		else if(null == other.name)
			return false;
		else if(!name.equals(other.name))
			return false;
		if(this.getPins().size()!=other.getPins().size())
			return false;
		getPins().containsAll(other.getPins());
		return true;
	}
	
	@Override
	public String toString(){
		return "port \"" + name + "\" "+aliases+" " + pins;
	}

	public void addPin(Pin pin) {
		if (pin==null)
			throw new NullPointerException();
		if (pins.size()>0 && (isOutPort() != pin.isOutPin()))
			throw new RuntimeException("Cannot mix out and in pins in port.");
		pins.add(pin);
	}

	public List<Pin> getPins() {
		return pins;
	}

	public List<String> getAliases() {
		return aliases;
	}

	public Stream<Pin> streamModuleInstancePins(ModuleInstance instance) {
		if (instance==null)
			return pins.stream();
		else
			return pins.stream().map(template-> {
				Instance inst = instance.getInst2instMap().get(template.getInstance());
				Pin pin = inst.getPin(template.getName());
				if (pin ==null) {
					//Pin self registers
					pin = new Pin(template.getPinType(),template.getName(),inst);
				}
				return pin;

			});
	}

	public NetType getNetType() {
		return netType;
	}

	public void setNetType(NetType netType) {
		this.netType = netType;
	}

	/**
	 * Output ports that directly pass through this port's signal
	 * @return
	 */
	public List<Port> getPassthroughPorts() {
		return Collections.unmodifiableList(passthroughPorts);
	}

	public void addPassthroughPort(Port out) {
		passthroughPorts.add(Objects.requireNonNull(out));
		out.setPassthroughFrom(this);
	}

	public void setPassthroughFrom(Port passthroughFrom) {
		this.passthroughFrom = passthroughFrom;
	}

	/**
	 * An input port whose signal this port passes through
	 * @return
	 */
	public Port getPassthroughFrom() {
		return passthroughFrom;
	}

	public Port clone(Function<Pin, Pin> mapPin) {
		Port clone = new Port();

		clone.name = name;
		clone.aliases = new ArrayList<>(aliases);
		clone.netType = netType;
		clone.pins = pins.stream().map(mapPin).collect(Collectors.toList());
		//No passthrough Ports
		//No passthrough from
		return clone;
	}

	public <R> void setAliases(List<String> aliases) {
		this.aliases = aliases;
	}
}
