
package edu.byu.ece.rapidSmith.examples;

import edu.byu.ece.rapidSmith.device.WireConnection;

public class TileNode {

	
	/** This is the enumerated int that represents the name of the wire specified */
	protected int wire;
	/** This is the pointer to a parent node in the route it is a part of */
	protected TileNode parent;
	/** This is the number of hops from the original source of the route this node is */
	protected int level;
	/** Keeps track of the wires that this node connects to */
	protected WireConnection[] connWires;
	
	/**
	 * Empty constructor, sets tile and wires to null. Sets wire and cost to -1.
	 * level and history are set to 0 and isPIP is set to false.
	 */
	public TileNode(){
		wire = -1;
		parent = null;
		level = 0;
		connWires = null;
	}

	public int getWire() {
		return wire;
	}

	public void setWire(int wire) {
		this.wire = wire;
	}

	public TileNode getParent() {
		return parent;
	}

	public void setParent(TileNode parent) {
		this.parent = parent;
	}

	public int getLevel() {
		return level;
	}

	public void setLevel(int level) {
		this.level = level;
	}

	public WireConnection[] getConnWires() {
		return connWires;
	}

	public void setConnWires(WireConnection[] wires) {
		this.connWires = wires;
	}
	
	public TileNode getRoot() {
		TileNode tmpNode = this;
		if(tmpNode.parent != null) {
			while (tmpNode.parent.parent != null) {
				tmpNode = tmpNode.parent;
			}
		}
		return tmpNode;
	}
	
	public int getRootConnectionWire(){
		TileNode tmpNode = this;
		if(tmpNode.parent != null && tmpNode.parent.parent != null) {
			while (tmpNode.parent.parent.parent != null) {
				tmpNode = tmpNode.parent;
			}
		}
	
		return tmpNode.getWire();
	}
	public boolean isLoop(){
		
		if(this.parent == null)
			return false;
		
		TileNode tmpNode= this.parent;
	
		while(tmpNode.parent.parent!=null){
			if(this.wire== tmpNode.wire)
				return true;
			tmpNode = tmpNode.parent;
		}
		return false;
		
	}

}
