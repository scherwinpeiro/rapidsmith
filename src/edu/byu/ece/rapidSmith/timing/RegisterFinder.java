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
 */

package edu.byu.ece.rapidSmith.timing;

import edu.byu.ece.rapidSmith.design.Design;
import edu.byu.ece.rapidSmith.design.Instance;
import edu.byu.ece.rapidSmith.primitiveDefs.Element;
import edu.byu.ece.rapidSmith.primitiveDefs.PrimitiveDef;
import edu.byu.ece.rapidSmith.primitiveDefs.PrimitiveDefList;
import edu.byu.ece.rapidSmith.router.InstanceElement;
import edu.byu.ece.rapidSmith.router.RoutingElement;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Created by wenzel on 31.07.14.
 */
public class RegisterFinder {

	PrimitiveDefList primitives;

	public RegisterFinder(PrimitiveDefList primitives) {
		this.primitives = primitives;
	}

	/**
	 * Find all the registers in an instance
	 * @param inst
	 * @return
	 */
	public Set<InstanceElement> findActiveInstanceElementRegisters(Instance inst) {
		Set<InstanceElement> result = new HashSet<>();

		if (inst.getPins().isEmpty())
			return result;

		PrimitiveDef prim = primitives.getPrimitiveDef(inst.getType());
		for (Element e : prim.getElements()) {
			InstanceElement ie = inst.getInstanceElement(e,primitives);
			if (ie.isRegister(primitives) == InstanceElement.RegisterType.REGISTER && !ie.isClock()) {
					result.add(ie);
			}
		}

		return result;
	}

	/**
	 * Find all the registers in a design.
	 * @param design
	 * @return
	 */
	public Set<RoutingElement> findActiveDesignRegisters(Design design) {
		List<RoutingElement> x = DesignWalker.routingElementStream(design,primitives)
				.filter(re -> re.isRegister(primitives) == RoutingElement.RegisterType.REGISTER && !re.isClock())
				.collect(Collectors.toList());
		return new HashSet<>(x);
	}
}
