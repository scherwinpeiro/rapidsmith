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

import com.caucho.hessian.io.Hessian2Input;
import edu.byu.ece.rapidSmith.device.Device;
import edu.byu.ece.rapidSmith.device.WireEnumerator;
import edu.byu.ece.rapidSmith.primitiveDefs.PrimitiveDefList;
import edu.byu.ece.rapidSmith.util.FamilyType;
import edu.byu.ece.rapidSmith.util.FileTools;
import edu.byu.ece.rapidSmith.util.PartNameTools;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;

import static edu.byu.ece.rapidSmith.util.FileTools.loadFromCompressedStream;

public abstract class AbstractDeviceDatabase implements DeviceDatabase{

	protected abstract InputStream getElement(String name);


	/**
	 * Gets and returns the path of the folder where the part files resides for partName.
	 * @param partName Name of the part to get its corresponding folder path.
	 * @return The path of the folder where the parts files resides.
	 */
	public static String getRelativePartFolderPath(String partName){
		return PartNameTools.getFamilyNameFromPart(partName) +
				File.separator;
	}

	/**
	 * Gets and returns the path of the folder where the family type resides.
	 * @param familyType The family type corresponding folder path.
	 * @return The path of the folder where the parts of familyType reside.
	 */
	public static String getRelativePartFolderPath(FamilyType familyType){
		familyType = PartNameTools.getBaseTypeFromFamilyType(familyType);
		return familyType.toString().toLowerCase() +
				File.separator;
	}

	/**
	 * Gets the device file path and name for the given partName.
	 * @param partName Name of the part to get corresponding device file for.
	 * @return The full path to the device file specified by partName.
	 */
	public static String getRelativeDeviceFileName(String partName){
		return getRelativePartFolderPath(partName) +
				PartNameTools.removeSpeedGrade(partName) +
				FileTools.deviceFileSuffix;
	}

	/**
	 * Loads the appropriate Device file based on the part name.  Accounts for speed grade in
	 * file name.
	 * @param partName Name of the part or device to load the information for.
	 * @return The device or null if there was an error.
	 */
	@Override
	public Device loadDevice(String partName){
		String canonicalName = PartNameTools.removeSpeedGrade(partName);
		Device device = Device.getInstance(canonicalName);

		// Don't reload the device if same part is already loaded
		if(device.getPartName() != null){
			return device;
		}

		String path = getRelativeDeviceFileName(canonicalName);
		InputStream is = getElement(path);

		if(!device.readDeviceFromCompactInputStream(is, path)){
			return null;
		}
		else{
			return device;
		}
	}

	/**
	 * Gets the wire enumerator file path and name for the given partName.
	 * @param partName Name of the part to get corresponding wire enumerator file for.
	 * @return The full path to the wire enumerator file specified by partName.
	 */
	public static String getRelativeWireEnumeratorFileName(String partName){
		return getRelativePartFolderPath(partName) + FileTools.wireEnumeratorFileName;
	}

	/**
	 * Gets the wire enumerator file path and name for the given familyType.
	 * @param familyType Name of the family type to get corresponding wire enumerator file for.
	 * @return The full path to the wire enumerator file specified by familyType.
	 */
	public static String getRelativeWireEnumeratorFileName(FamilyType familyType){
		return getRelativePartFolderPath(familyType) + FileTools.wireEnumeratorFileName;
	}


	/**
	 * Loads the appropriate WireEnumerator file based on the part name.  Accounts for
	 * speed grade in file name.
	 * @param familyType Family of the device to load the information for.
	 * @return The WireEnumerator or null if there was an error.
	 */
	@Override
	public WireEnumerator loadWireEnumerator(FamilyType familyType){
		familyType = PartNameTools.getBaseTypeFromFamilyType(familyType);
		WireEnumerator we = WireEnumerator.getInstance(familyType);
		if(we.getFamilyName() != null){
			return we;
		}

		String path = getRelativeWireEnumeratorFileName(familyType);
		InputStream is = getElement(path);

		if(!we.readCompactEnumInputStream(is, familyType)){
			return null;
		}
		else{
			return we;
		}
	}


	/**
	 * Gets the primitive defs file path and name for the given partName.
	 * @param partName Name of the part to get corresponding primitive defs file for.
	 * @return The full path to the primitive defs file specified by partName.
	 */
	public static String getRelativePrimitiveDefsFileName(String partName){
		return getRelativePartFolderPath(partName) + FileTools.primitiveDefFileName;
	}

	/**
	 * Gets the primitive defs file path and name for the given familyType.
	 * @param familyType Family type to get corresponding primitive defs file for.
	 * @return The full path to the primitive defs file specified by fileName.
	 */
	public static String getRelativePrimitiveDefsFileName(FamilyType familyType){
		return getRelativePartFolderPath(familyType) + FileTools.primitiveDefFileName;
	}

	/**
	 * Loads the primitiveDefs file for the appropriate family based on partName.
	 * @param partName The part name to load the primitiveDefs for.
	 * @return The PrimitiveDefList object containing the primitives used in the family.
	 */
	@Override
	public PrimitiveDefList loadPrimitiveDefs(String partName){
		String path = getRelativePrimitiveDefsFileName(partName);
		InputStream is = getElement(path);
		return (PrimitiveDefList) loadFromCompressedStream(getElement(path));
	}

	/**
	 * Loads the primitiveDefs file for the appropriate family based on FamilyType.
	 * @param familyType the Xilinx FPGA family to load primitive defs for.
	 * @return The PrimitiveDefList object containing the primitives used in the family.
	 */
	@Override
	public PrimitiveDefList loadPrimitiveDefs(FamilyType familyType){
		String path = getRelativePrimitiveDefsFileName(familyType);
		return (PrimitiveDefList) loadFromCompressedStream(getElement(path));
	}

	/**
	 * Looks at the current device file for the part name specified and retrieves
	 * its current device version.
	 * @param partName The part name of the file to check.
	 * @return The version of the device file, null if part does not exist or is invalid.
	 */
	@Override
	public String getDeviceVersion(String partName){
		String fileName = getRelativeDeviceFileName(partName);
		String version;
		try{
			InputStream is = getElement(fileName);
			Hessian2Input his = FileTools.decompressInputStream(is);
			version = his.readString();
			his.close();
		}
		catch (FileNotFoundException e){
			return null;
		}
		catch (IOException e){
			return null;
		}

		return version;
	}

	@Override
	public List<String> getAvailableParts() {
		return getAvailableFamilies().stream()
				.flatMap(familyType -> getAvailableParts(familyType).stream())
				.collect(Collectors.toList());
	}
}
