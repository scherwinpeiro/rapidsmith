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
package edu.byu.ece.rapidSmith.primitiveDefs;

import edu.byu.ece.rapidSmith.device.PrimitiveType;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;

public class PrimitiveDefList  extends ArrayList<PrimitiveDef> implements Serializable{

	private static final long serialVersionUID = 4988664481262250467L;

	private HashMap<PrimitiveType,PrimitiveDef> primitiveMap;
	
	public PrimitiveDefList(){
		super();
		primitiveMap = new HashMap<PrimitiveType,PrimitiveDef>();
	}
	
	public PrimitiveDef getPrimitiveDef(PrimitiveType type){
		return primitiveMap.get(type);
	}
	
	@Override
	public boolean add(PrimitiveDef e){
		primitiveMap.put(e.getType(), e);
		return super.add(e);
	}
	
	@Override
	public String toString(){
		StringBuilder s = new StringBuilder();
		String nl = System.getProperty("line.separator");
		s.append("(primitive_defs " + this.size() +nl);
		for(PrimitiveDef p : this){
			s.append("\t" + p.toString() + nl);
		}
		s.append(")");
		return s.toString();
	}

	public void clearDelays() {
		this.stream().flatMap(p -> p.getElements().stream()).flatMap(e -> e.getConnections().stream()).forEach(connection -> {
			connection.setDelay(Double.POSITIVE_INFINITY);
		});
	}
}
