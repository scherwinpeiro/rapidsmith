/*
 * _______________________________________________________________________________
 *
 *  Copyright (c) 2012 TU Dresden, Chair for Embedded Systems
 *  Copyright (c) 2013-2016 TU Darmstadt, Computer Systems Group
 *  (http://www.rs.tu-darmstadt.de) All rights reserved.
 *
 *  Redistribution and use in source and binary forms, with or without
 *  modification, are permitted provided that the following conditions
 *  are met:
 *
 *  1. Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *  2. Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *  3. All advertising materials mentioning features or use of this software
 *     must display the following acknowledgement: "This product includes
 *     software developed by the TU Dresden Chair for Embedded Systems, TU Darmstadt Computer Systems Group and
 *     its contributors."
 *
 *  4. Neither the name of the TU Dresden Chair for Embedded Systems, TU Darmstadt Computer Systems Group nor the
 *     names of its contributors may be used to endorse or promote products
 *     derived from this software without specific prior written permission.
 *
 *  THIS SOFTWARE IS PROVIDED BY TU DRESDEN CHAIR FOR EMBEDDED SYSTEMS, TU DARMSTADT COMPUTER SYSTEMS GROUP AND
 *  CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING,
 *  BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 *  FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 *  REGENTS OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 *  SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
 *  TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 *  PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 *  LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 *  NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 *  SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * _______________________________________________________________________________
 */

package edu.byu.ece.rapidSmith.device.database;

import edu.byu.ece.rapidSmith.device.Device;
import edu.byu.ece.rapidSmith.device.WireEnumerator;
import edu.byu.ece.rapidSmith.primitiveDefs.PrimitiveDefList;
import edu.byu.ece.rapidSmith.util.FamilyType;
import edu.byu.ece.rapidSmith.util.PartNameTools;

import java.util.List;

public interface DeviceDatabase {

	/**
	 * Checks if this DeviceDatabase is available
	 * @return true if available
	 */
	boolean isDatabaseAvailable();

	/**
	 * Loads the appropriate Device file based on the part name.  Accounts for speed grade in
	 * file name.
	 * @param partName Name of the part or device to load the information for.
	 * @return The device or null if there was an error.
	 */
	Device loadDevice(String partName);

	/**
	 * Loads the appropriate WireEnumerator file based on the part name.  Accounts for
	 * speed grade in file name.
	 * @param familyType Family of the device to load the information for.
	 * @return The WireEnumerator or null if there was an error.
	 */
	WireEnumerator loadWireEnumerator(FamilyType familyType);

	/**
	 * Loads the primitiveDefs file for the appropriate family based on partName.
	 * @param partName The part name to load the primitiveDefs for.
	 * @return The PrimitiveDefList object containing the primitives used in the family.
	 */
	PrimitiveDefList loadPrimitiveDefs(String partName);

	/**
	 * Loads the primitiveDefs file for the appropriate family based on FamilyType.
	 * @param familyType the Xilinx FPGA family to load primitive defs for.
	 * @return The PrimitiveDefList object containing the primitives used in the family.
	 */
	PrimitiveDefList loadPrimitiveDefs(FamilyType familyType);

	/**
	 * Checks for all device files present in the current RapidSmith path and returns
	 * a list of strings of those part names available to be used by the tool.
	 * @return A list of available Xilinx parts for use by the tools.
	 */
	List<String> getAvailableParts();

	/**
	 * Checks for all device files present in the current RapidSmith family path and returns
	 * a list of strings of those part names available to be used by the tool within the specified family.
	 * @param type The specified family type.
	 * @return A list of available Xilinx parts for the given family type
	 */
	List<String> getAvailableParts(FamilyType type);
	/**
	 * This method returns an ArrayList of family types currently supported
	 * @return ArrayList of all family types installed
	 */
	List<FamilyType> getAvailableFamilies();

	/**
	 * Looks at the current device file for the part name specified and retrieves
	 * its current device version.
	 * @param partName The part name of the file to check.
	 * @return The version of the device file, null if part does not exist or is invalid.
	 */
	String getDeviceVersion(String partName);

	/**
	 * Loads the appropriate WireEnumerator file based on the part name.  Accounts for
	 * speed grade in file name.
	 * @param partName Name of the part or device to load the information for.
	 * @return The WireEnumerator or null if there was an error.
	 */
	default WireEnumerator loadWireEnumerator(String partName){
		FamilyType familyType = PartNameTools.getFamilyTypeFromPart(partName);
		return loadWireEnumerator(familyType);
	}
}
