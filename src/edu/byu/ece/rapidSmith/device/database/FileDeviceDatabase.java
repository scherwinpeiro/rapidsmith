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

import edu.byu.ece.rapidSmith.util.FamilyType;
import edu.byu.ece.rapidSmith.util.Installer;
import edu.byu.ece.rapidSmith.util.MessageGenerator;
import edu.byu.ece.rapidSmith.util.PartNameTools;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

public class FileDeviceDatabase extends AbstractDeviceDatabase {
	private static final Logger logger = LoggerFactory.getLogger(FileDeviceDatabase.class);

	/** Environment Variable Name which points to the rapidSmith project on disk */
	private static final String rapidSmithPathVariableName = "RAPIDSMITH_PATH";
	/** Folder where device files are kept */
	private static final String deviceFolderName = "devices";

	private static String rapidSmithPath = null;

	/**
	 * Gets and returns the value of the environment variable rapidSmithPathVariableName
	 * @return The string of the path to the rapidSmith project location
	 */
	public static String getRapidSmithPath(){
		if (rapidSmithPath!=null)
			return rapidSmithPath;

		rapidSmithPath = calcRapidsmithPath(true);

		return rapidSmithPath;
	}

	@NotNull
	private static String calcRapidsmithPath(boolean logging) {
		String path = System.getProperty("RAPIDSMITH_PATH", null);

		if (path == null) {
			path = System.getenv(rapidSmithPathVariableName);
		}
		if(path == null){
			Path currentRelativePath = Paths.get("");
			path = currentRelativePath.toAbsolutePath().toString();

			String nl = System.getProperty("line.separator");
			if (logging) {
				logger.warn(": You do not have the {} set in your environment. Trying to continue by using the current working dir ({})", rapidSmithPathVariableName, path);
			}
		}
		if(path.endsWith(File.separator)){
			path.substring(0, path.length()-1);
		}

		if (logging) {
			logger.info("Rapidsmith root at {}", path);
		}
		return path;
	}

	private static String getDeviceFolderName() {
		return  getRapidSmithPath() +
				File.separator +
				deviceFolderName +
				File.separator;
	}

	/**
	 * Gets and returns the path of the folder where the part files resides for partName.
	 * @param partName Name of the part to get its corresponding folder path.
	 * @return The path of the folder where the parts files resides.
	 */
	public static String getPartFolderPath(String partName){
		return getDeviceFolderName() +
				AbstractDeviceDatabase.getRelativePartFolderPath(partName);
	}

	/**
	 * Gets and returns the path of the folder where the family type resides.
	 * @param familyType The family type corresponding folder path.
	 * @return The path of the folder where the parts of familyType reside.
	 */
	private static String getPartFolderPath(FamilyType familyType){
		return getDeviceFolderName()
				+ AbstractDeviceDatabase.getRelativePartFolderPath(familyType);
	}

	/**
	 * Gets the device file path and name for the given partName.
	 * @param partName Name of the part to get corresponding device file for.
	 * @return The full path to the device file specified by partName.
	 */
	public static String getDeviceFileName(String partName){
		return getDeviceFolderName()
				+ AbstractDeviceDatabase.getRelativeDeviceFileName(partName);
	}


	@Override
	public boolean isDatabaseAvailable() {
		//Do not log Rapidsmith path while checking if it is valid
		final File file = new File(calcRapidsmithPath(false) + File.separator +
				deviceFolderName);
		return file.exists();
	}

	@Override
	protected InputStream getElement(String name) {
		try {
			return new FileInputStream(getDeviceFolderName()+name);
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Gets the wire enumerator file path and name for the given partName.
	 * @param partName Name of the part to get corresponding wire enumerator file for.
	 * @return The full path to the wire enumerator file specified by partName.
	 */
	public static String getWireEnumeratorFileName(String partName){
		return getDeviceFolderName()
				+ AbstractDeviceDatabase.getRelativeWireEnumeratorFileName(partName);
	}

	/**
	 * Gets the wire enumerator file path and name for the given familyType.
	 * @param familyType Name of the family type to get corresponding wire enumerator file for.
	 * @return The full path to the wire enumerator file specified by familyType.
	 */
	public static String getWireEnumeratorFileName(FamilyType familyType){
		return getDeviceFolderName()
				+ AbstractDeviceDatabase.getRelativeWireEnumeratorFileName(familyType);
	}

	/**
	 * Gets the primitive defs file path and name for the given partName.
	 * @param partName Name of the part to get corresponding primitive defs file for.
	 * @return The full path to the primitive defs file specified by partName.
	 */
	public static String getPrimitiveDefsFileName(String partName){
		return getDeviceFolderName()
				+ AbstractDeviceDatabase.getRelativePrimitiveDefsFileName(partName);
	}

	/**
	 * Gets the primitive defs file path and name for the given familyType.
	 * @param familyType Family type to get corresponding primitive defs file for.
	 * @return The full path to the primitive defs file specified by fileName.
	 */
	public static String getPrimitiveDefsFileName(FamilyType familyType){
		return getDeviceFolderName()
				+ AbstractDeviceDatabase.getRelativePrimitiveDefsFileName(familyType);
	}

	/**
	 * Checks for all device files present in the current RapidSmith family path and returns
	 * a list of strings of those part names available to be used by the tool within the specified family.
	 * @param type The specified family type.
	 * @return A list of available Xilinx parts for the given family type
	 */
	@Override
	public ArrayList<String> getAvailableParts(FamilyType type){
		ArrayList<String> allParts = new ArrayList<String>();
		String pattern = "_db.dat";
		File dir = new File(getRapidSmithPath() + File.separator + "devices" + File.separator + type.toString().toLowerCase());
		if(!dir.exists()){
			MessageGenerator.briefErrorAndExit("ERROR: No part files exist.  Please run " +
					Installer.class.getCanonicalName() +" to create part files.");
		}
		for(String part : dir.list()){
			if(part.endsWith(pattern)){
				allParts.add(part.replace(pattern, ""));
			}
		}
		return allParts;
	}

	/**
	 * This method returns an ArrayList of family types currently supported
	 * @return ArrayList of all family types installed
	 */
	@Override
	public ArrayList<FamilyType> getAvailableFamilies() {
		ArrayList<FamilyType> allFamilies = new ArrayList<FamilyType>();
		File dir = new File(getRapidSmithPath() + File.separator + "devices");
		if(!dir.exists()){
			MessageGenerator.briefErrorAndExit("ERROR: No part files exist.  Please run " +
					Installer.class.getCanonicalName() +" to create part files.");
		}
		for(String partFamily : dir.list()){
			FamilyType type = PartNameTools.getFamilyTypeFromFamilyName(partFamily);
			if (type != null) allFamilies.add(type);
		}

		return allFamilies;
	}
}
