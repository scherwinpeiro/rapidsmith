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
package edu.byu.ece.rapidSmith.util;

import com.caucho.hessian.io.Deflation;
import com.caucho.hessian.io.Hessian2Input;
import com.caucho.hessian.io.Hessian2Output;
import edu.byu.ece.rapidSmith.device.Device;
import edu.byu.ece.rapidSmith.device.PrimitivePinMap;
import edu.byu.ece.rapidSmith.device.PrimitiveSite;
import edu.byu.ece.rapidSmith.device.PrimitiveType;
import edu.byu.ece.rapidSmith.device.Tile;
import edu.byu.ece.rapidSmith.device.WireConnection;
import edu.byu.ece.rapidSmith.device.helper.HashPool;
import edu.byu.ece.rapidSmith.device.helper.WireArray;
import edu.byu.ece.rapidSmith.device.helper.WireArrayConnection;
import edu.byu.ece.rapidSmith.device.helper.WireHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

/**
 * This class is specifically written to allow for efficient file import/export of different semi-primitive
 * data types and structures.  The read and write functions of this class are only guaranteed to work with
 * those specified in this class and none else.  The goal of this class is to load faster than Serialized
 * Java and produce smaller files as well.
 * 
 * @author Chris Lavin
 * Created on: Apr 22, 2010
 */
public class FileTools {

	private static final Logger logger = LoggerFactory.getLogger(FileTools.class);

	/** Environment Variable Name which points to the rapidSmith project on disk */
	public static final String rapidSmithPathVariableName = "RAPIDSMITH_PATH";
	/** Suffix of the device part files */
	public static final String deviceFileSuffix = "_db.dat";
	/** Suffix of the wireEnumerator files */
	public static final String wireEnumeratorFileName = "wireEnumerator.dat";
	/** Name of the family primitive definition files */
	public static final String primitiveDefFileName = "primitiveDefs.dat";
	/** Name of the Virtex 5 RAMB Primitive Pin Mapping Patch File */
	public static final String v5RAMBPinMappingFileName = "v5RAMBPins.dat";
	//===================================================================================//
	/* Get Streams                                                                       */
	//===================================================================================//
	public static Hessian2Output getOutputStream(String fileName){
		FileOutputStream fos;
		try{
			fos = new FileOutputStream(fileName);
			BufferedOutputStream bos = new BufferedOutputStream(fos);
			Hessian2Output hos = new Hessian2Output(bos);
			Deflation dos = new Deflation();
			return dos.wrap(hos);
		}
		catch(Exception e){
			MessageGenerator.briefError("Problem opening stream for file: " + fileName);
		}
		return null;
	}

	public static Hessian2Input decompressInputStream(InputStream inputStream) {
		try {
			BufferedInputStream bis = new BufferedInputStream(inputStream);
			Hessian2Input his = new Hessian2Input(bis);
			Deflation dis = new Deflation();
			return dis.unwrap(his);
		} catch(IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	public static Hessian2Input getDecompressingInputStream(String fileName){
		FileInputStream fis;
		try {
			fis = new FileInputStream(fileName);
			return decompressInputStream(fis);
		} catch(IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	//===================================================================================//
	/* Custom Read/Write File Functions for Device/WireEnumeration Class                 */
	//===================================================================================//
	public static HashMap<String,Integer> readHashMap(Hessian2Input dis, Integer[] allInts){
		int count;
		HashMap<String,Integer> tileMap = null;
		String[] keys;
		try {
			// TODO - The following read is necessary, but could be removed in a future version
			dis.readInt();//size = dis.readInt();
			count = dis.readInt();
			tileMap = new HashMap<String,Integer>(count);
			keys = new String[count];
			for(int i = 0; i < keys.length; i++){
				keys[i] = dis.readString();
			}
			for(int i=0; i < count; i++){
				tileMap.put(keys[i], allInts[dis.readInt()]);
			}

		} catch (IOException e) {
			MessageGenerator.briefErrorAndExit("Error in readHashMap()");
		}
		return tileMap;
	}
	
	public static boolean writeHashMap(Hessian2Output dos, HashMap<String,Integer> map){
		try {
			int size = 0;
			for(String s : map.keySet()){
				size += s.length() + 1;
			}
			//TODO - The loop above is not needed, and we don't need to write the int below (remove in future version)
			dos.writeInt(size);
			size = map.size();
			dos.writeInt(size);
			ArrayList<Integer> values = new ArrayList<Integer>(map.size());
			for(String s : map.keySet()){
				//dos.write(s.getBytes());
				values.add(map.get(s));
				dos.writeString(s);
				//dos.write('\n');
			}
			for(Integer i : values){
				dos.writeInt(i.intValue());
			}
		} catch (IOException e) {
			return false;
		}
		return true;
	}
	
	public static HashMap<String, ArrayList<String>> readStringMultiMap(Hessian2Input dis){
		int count;
		HashMap<String,ArrayList<String>> map = null;
		try {
			count = dis.readInt();
			map = new HashMap<String, ArrayList<String>>(count);
			for(int i = 0; i < count; i++){
				String key = dis.readString();
				int valueCount = dis.readInt();
				ArrayList<String> value = new ArrayList<String>(valueCount);
				for (int j = 0; j < valueCount; j++) {
					value.add(dis.readString());
				}
				map.put(key, value);
			}

		} catch (IOException e) {
			MessageGenerator.briefErrorAndExit("Error in readStringMultiMap()");
		}
		return map;
	}
	
	public static boolean writeStringMultiMap(Hessian2Output dos, HashMap<String, ArrayList<String>> map){
		try {
			dos.writeInt(map.size());
			for(String s : map.keySet()){
				dos.writeString(s);
				ArrayList<String> values = map.get(s);
				dos.writeInt(values.size());
				for(String str : values){
					dos.writeString(str);
				}
			}
		} catch (IOException e){
			return false;
		}
		return true;
	}
	
	public static boolean writeStringArray(Hessian2Output dos, String[] stringArray){
		int size = 0;
		for(String s : stringArray){
			size += s.length() + 1;
		}
		try {
			dos.writeInt(stringArray.length);
			for(int i=0; i<stringArray.length; i++){
				dos.writeString(stringArray[i]);
			}
		} catch (IOException e){
			return false;
		}
		return true;
	}
	
	public static String[] readStringArray(Hessian2Input dis){
		int size;
		String[] wireArray = null;
		try {
			size = dis.readInt();
			wireArray = new String[size];
			for(int i = 0; i < wireArray.length; i++){
				wireArray[i] = dis.readString();
			}
		} catch (IOException e) {
			e.printStackTrace();
			MessageGenerator.briefErrorAndExit("Error in readStringArray()");
		}
		return wireArray;
	}
	
	public static boolean writeIntArray(Hessian2Output dos, int[] intArray){
		try{
			if(intArray == null){
				dos.writeInt(0);
				return true;
			}
			dos.writeInt(intArray.length);
			for(int i : intArray){
				dos.writeInt(i);
			}
		} 
		catch (IOException e){
			return false;
		}
		
		return true;
	}
	
	public static int[] readIntArray(Hessian2Input dis){
		int size;
		//byte[] buffer;
		int[] intArray = null;
		try {
			size = dis.readInt();
			if(size == 0){
				return null;
			}
			intArray = new int[size];
			//buffer = new byte[size*4];
			for(int i = 0; i < intArray.length; i++){
				intArray[i] = dis.readInt();
			}
			//dis.read(buffer);
			/*for(int i=0; i < buffer.length; i+=4){
				intArray[i>>2] = (((buffer[i  ]) & 0xFF) << 24) + 
								 (((buffer[i+1]) & 0xFF) << 16) + 
							     (((buffer[i+2]) & 0xFF) << 8)  + 
							     (( buffer[i+3]) & 0xFF);
			}*/
		} catch (IOException e) {
			e.printStackTrace();
			System.out.println("Error in readIntArray()");
			System.exit(1);
		}
		return intArray;
	}
	
	public static boolean writeString(DataOutputStream dos, String str){
		try {
			dos.writeInt(str.length());
			dos.write(str.getBytes());
		} catch (IOException e) {
			return false;
		}
		return true;
	}
	
	public static String readString(DataInputStream dis){
		byte[] buffer;
		try {
			buffer = new byte[dis.readInt()];
			dis.read(buffer);
		} catch (IOException e) {
			return null;
		}
		return new String(buffer);
	}

	public static boolean writeIntegerHashSet(Hessian2Output dos, HashSet<Integer> ints) {
		int[] nums = new int[ints.size()];
		int idx = 0;
		for(Integer i : ints){
			nums[idx] = i;
			idx++;
		}
		return writeIntArray(dos, nums);
	}
	
	public static HashSet<Integer> readIntegerHashSet(Hessian2Input dis){
		int[] nums = readIntArray(dis);
		if(nums == null){
			return new HashSet<Integer>();
		}
		HashSet<Integer> tmp = new HashSet<Integer>();
		for(int i : nums){
			tmp.add(i);
		}
		return tmp;
	}
	
	public static boolean writePrimitiveSite(Hessian2Output dos, PrimitiveSite p, Device device, HashPool<PrimitivePinMap> primitivePinPool){
		try {
			// Write Name
			dos.writeString(p.getName());
			
			// Write Type
			dos.writeInt(p.getType().ordinal());
			
			// Write Tile (Unique Integer)
			dos.writeInt(p.getTile().getUniqueAddress());
		
			// Write PinMap
			dos.writeInt(primitivePinPool.getEnumerationValue(new PrimitivePinMap(p.getPins())));

		} catch (IOException e){
			return false;
		}
		return true;
	}
	
	public static PrimitiveSite readPrimitiveSite(Hessian2Input dis, Device device, ArrayList<HashMap<String,Integer>> primitivePinMaps, PrimitiveType[] typeValues){
		PrimitiveSite p = new PrimitiveSite();
		
		try {
			p.setName(dis.readString());
			p.setType(typeValues[dis.readInt()]);
			Tile t = device.getTile(dis.readInt());
			p.setTile(t);
			p.setPins(primitivePinMaps.get(dis.readInt()));

		} catch (IOException e) {
			return null;
		}
		return p;
	}

	public static boolean writeWireHashMap(Hessian2Output dos, WireHashMap wires, 
			HashPool<WireArray> wireArrayPool, HashPool<WireArrayConnection> wireConnectionPool) {

		int[] wireConnections;
		if(wires == null){
			wireConnections = new int[0];
		}
		else{
			wireConnections = new int[wires.size()];
			int ndx = 0;
			for(Integer key : wires.keySet()){
				WireArrayConnection tmp = new WireArrayConnection(key,wireArrayPool.getEnumerationValue(new WireArray(wires.get(key))));
				wireConnections[ndx] = wireConnectionPool.getEnumerationValue(tmp);
				ndx++;
			}
		}
		
		writeIntArray(dos,wireConnections);
		
		return true;
	}

	public static WireHashMap readWireHashMap(Hessian2Input dis, ArrayList<WireConnection[]> wires, ArrayList<WireArrayConnection> wireConnections){
		int[] intArray = readIntArray(dis);
		
		if(intArray == null){
			return null;
		}
				
		WireHashMap newMap = new WireHashMap((int)(intArray.length*1.3f));

		for(int i : intArray){
			WireArrayConnection wc = wireConnections.get(i);
			newMap.put(wc.wire, wires.get(wc.wireArrayEnum));
		}

		return newMap;
	}

	//===================================================================================//
	/* Generic Read/Write Serialization Methods                                          */
	//===================================================================================//	
	/**
	 * Loads a serialized Java object from fileName.
	 * @param fileName The file to read from.
	 * @return The Object de-serialized from the file or null if there was an error.
	 */
	public static Object loadFromFile(String fileName){
		File inputFile = new File(fileName);
		FileInputStream fis;
		BufferedInputStream bis;
		ObjectInputStream ois;
		Object o; 
		try {
			fis = new FileInputStream(inputFile);
			bis = new BufferedInputStream(fis);
			ois = new ObjectInputStream(bis);
			o = ois.readObject();
			ois.close();
			bis.close();
			fis.close();
		} 
		catch (FileNotFoundException e) {
			MessageGenerator.briefError("Could not open file: " + fileName + " , does it exist?");
			return null;			
		}
		catch (IOException e) {
			MessageGenerator.briefError("Trouble reading from file: " + fileName);
			return null;						
		}		
		catch (ClassNotFoundException e) {
			MessageGenerator.briefError("Improper file found: ");
			return null;									
		}
		catch (OutOfMemoryError e){
			MessageGenerator.briefError("The JVM ran out of memory trying to load the object in " +
				fileName + ". Try using the JVM switch to increase the heap space (" +
						"ex: java -Xmx1600M).");
			return null;
		}
		return o;
	}

	/**
	 * Serialize the Object o to a the file specified by fileName.
	 * @param o The object to serialize.
	 * @param fileName Name of the file to serialize the object to.
	 * @return True if operation was successful, false otherwise.
	 */
	public static boolean saveToFile(Object o, String fileName){
		FileOutputStream fos = null;
		BufferedOutputStream bos = null;
		ObjectOutputStream oos = null;
		File objectFile = null;
		
		objectFile = new File(fileName);
		try {
			fos = new FileOutputStream(objectFile);
			bos = new BufferedOutputStream(fos);
			oos = new ObjectOutputStream(bos);
			oos.writeObject(o);
			oos.close();
			bos.close();
			fos.close();
		} catch (FileNotFoundException e) {
			return false;
		} catch (IOException e) {
			return false;
		}
		return true;
	}

	public static boolean saveToCompressedFile(Object o, String fileName){
		Hessian2Output hos = getOutputStream(fileName);
		try{
			hos.writeObject(o);
			hos.close();
		}
		catch(IOException e){
			return false;
		}
		return true;
	}


	private static Object loadFromHis(Hessian2Input his) {
		try{
			Object o = his.readObject();
			his.close();
			return o;
		}
		catch(IOException e){
			return null;
		}
	}

	public static Object loadFromCompressedFile(String fileName){
		Hessian2Input his = getDecompressingInputStream(fileName);
		return loadFromHis(his);
	}
	
	public static Object loadFromCompressedStream(InputStream inputStream){
		Hessian2Input his = decompressInputStream(inputStream);
		return loadFromHis(his);
	}
	
	/**
	 * This is a simple method that writes the elements of an ArrayList of Strings
	 * into lines in the text file fileName.
	 * @param lines The ArrayList of Strings to be written
	 * @param fileName Name of the text file to save the ArrayList to
	 */
	public static void writeLinesToTextFile(ArrayList<String> lines, String fileName) {
		String nl = System.getProperty("line.separator");
		try{
			BufferedWriter bw = new BufferedWriter(new FileWriter(fileName));

			for (int i = 0; i < lines.size(); i++) {
				bw.write(lines.get(i) + nl);
			}

			bw.close();
		}
		catch(IOException e){
			MessageGenerator.briefErrorAndExit("Error writing file: " +
				fileName + File.separator + e.getMessage());
		}
	}
	
	/**
	 * This is a simple method that will read in a text file and put each line in a
	 * string and put all the lines in an ArrayList.  The user is cautioned not
	 * to open extremely large files with this method.
	 * @param fileName Name of the text file to load into the {@code ArrayList<String>}.
	 * @return An ArrayList containing strings of each line in the file. 
	 */
	public static ArrayList<String> getLinesFromTextFile(String fileName){
		String line = null;
		BufferedReader br;
		ArrayList<String> lines = new ArrayList<String>();
		try{
			br = new BufferedReader(new FileReader(fileName));
			
			while((line = br.readLine()) != null){
				lines.add(line);
			}
		}
		catch(FileNotFoundException e){
			MessageGenerator.briefErrorAndExit("ERROR: Could not find file: " + fileName);
		} 
		catch(IOException e){
			MessageGenerator.briefErrorAndExit("ERROR: Could not read from file: " + fileName);
		}
		
		return lines;
	}
	
	//===================================================================================//
	/* Generic File Manipulation Methods                                                 */
	//===================================================================================//	

	/**
	 * Takes a file name and removes everything after the last '.' inclusive
	 * @param fileName The input file name 
	 * @return the substring of fileName if it contains a '.', it returns fileName otherwise
	 */
	public static String removeFileExtension(String fileName){
		int endIndex = fileName.lastIndexOf('.');
		if(endIndex != -1){
			return fileName.substring(0, endIndex);
		}
		else{
			return fileName;
		}
	}

	public static Path replaceFileExtension(Path path, String newExtension) {
		final Path filename = path.getName(path.getNameCount() - 1);
		String newFilename = removeFileExtension(filename.toString())+newExtension;
		return path.getParent().resolve(newFilename);
	}
	
	/**
	 * Creates a directory in the current path called dirName.
	 * @param dirName Name of the directory to be created.
	 * @return True if the directory was created or already exists, false otherwise.
	 */
	public static boolean makeDir(String dirName){
		File dir = new File(dirName); 
		if(!(dir.exists())){
			return dir.mkdir();
		}
		return true;
	}
	
	/**
	 * Creates a directory in the current path called dirName.
	 * @param dirName Name of the directory to be created.
	 * @return True if the directory and implicit parent directories were created, false otherwise.
	 */
	public static boolean makeDirs(String dirName){
		return new File(dirName).mkdirs();
	}
	
	/**
	 * Gets the size of the file in bytes.
	 * @param fileName Name of the file to get the size of.
	 * @return The number of bytes used by the file.
	 */
	public static long getFileSize(String fileName){
		return new File(fileName).length();
	}
	
	/**
	 * Delete the file/folder in the file system called fileName 
	 * @param fileName Name of the file to delete
	 * @return True for successful deletion, false otherwise.
	 */
	public static boolean deleteFile(String fileName){
	    // A File object to represent the filename
	    File f = new File(fileName);

	    // Make sure the file or directory exists and isn't write protected
	    if (!f.exists())
	      throw new IllegalArgumentException(
	          "Delete: no such file or directory: " + fileName);

	    if (!f.canWrite())
	      throw new IllegalArgumentException("Delete: write protected: "
	          + fileName);

	    // If it is a directory, make sure it is empty
	    if (f.isDirectory()) {
	      String[] files = f.list();
	      if (files.length > 0)
	        throw new IllegalArgumentException(
	            "Delete: directory not empty: " + fileName);
	    }

	    // Attempt to delete it
	    boolean success = f.delete();

	    if (!success)
	      throw new IllegalArgumentException("Delete: deletion failed");
		
		return success;
	}
	
	/**
	 * Deletes everything in the directory given by path, but does not
	 * delete the folder itself.
	 * @param path The path to the folder where all its contents will be deleted.
	 * @return True if operation was successful, false otherwise.
	 */
	public static boolean deleteFolderContents(String path){
		File currDirectory = new File(path);
		if(currDirectory.exists()){
			try {
				for(File file : currDirectory.listFiles()){
					if(file.isDirectory()){
						if(!deleteFolder(file.getCanonicalPath())){
							return false;
						}
					}
					else{
						if(!deleteFile(file.getCanonicalPath())){
							return false;
						}
					}
				}				
			}
			catch(IOException e){
				return false;
			}
			return true;
		}
		return false;
	}
	
	/**
	 * Delete the folder and recursively files and folders below
	 * @param folderName
	 * @return true for successful deletion, false otherwise
	 */
	public static boolean deleteFolder(String folderName){
		// A file object to represent the filename
		File f = new File(folderName);
		
		if(!f.exists() || !f.isDirectory()){
			throw new IllegalArgumentException("Delete: no such directory: " + folderName);
		}
		
		for(File i: f.listFiles()){
			if(i.isDirectory()){
				deleteFolder(i.getAbsolutePath());
			}else if(i.isFile()){
				if(!i.delete()){
					throw new IllegalArgumentException("Delete: deletion failed: " + i.getAbsolutePath());
				}
			}
		}
		return deleteFile(folderName);
	}

	public static boolean renameFile(String oldFileName, String newFileName){
		File oldFile = new File(oldFileName);
		return oldFile.renameTo(new File(newFileName));
	}
	
	/**
	 * Copies a file from one location (src) to another (dst).  This implementation uses the java.nio
	 * channels (because supposedly it is faster).
	 * @param src Source file to read from
	 * @param dst Destination file to write to
	 * @return True if operation was successful, false otherwise.
	 */
	public static boolean copyFile(String src, String dst){
	    FileChannel inChannel = null;
	    FileChannel outChannel = null;
		try {
			inChannel = new FileInputStream(new File(src)).getChannel();
			outChannel = new FileOutputStream(new File(dst)).getChannel();
			inChannel.transferTo(0, inChannel.size(), outChannel);
		} 
		catch (FileNotFoundException e){
			e.printStackTrace();
			MessageGenerator.briefError("ERROR could not find/access file(s): " + src + " and/or " + dst);
			return false;
		} 
		catch (IOException e){
			MessageGenerator.briefError("ERROR copying file: " + src + " to " + dst);
			return false;
		}
		finally {
			try {
				if(inChannel != null)
					inChannel.close();
				if(outChannel != null) 
					outChannel.close();
			} 
			catch (IOException e) {
				MessageGenerator.briefError("Error closing files involved in copying: " + src + " and " + dst);
				return false;
			}
		}
		return true;
	}
	
	/**
	 * Copies a folder and its files from the path defined in srcDirectoryPath to a new folder
	 * at dstDirectoryPath.  If the recursive flag is set it will also recursively copy subfolders
	 * from the source to the destination.
	 * @param srcDirectoryPath The name of the source folder to copy.
	 * @param dstDirectoryPath The destination of where the copy of the folder should be located.
	 * @param recursive A flag denoting if the sub folders of source should be copied.
	 * @return True if operation was successful, false otherwise.
	 */
	public static boolean copyFolder(String srcDirectoryPath, String dstDirectoryPath, boolean recursive){
		File srcDirectory = new File(srcDirectoryPath);
		File dstDirectory = new File(dstDirectoryPath + File.separator + srcDirectory.getName());
		if(srcDirectory.exists() && srcDirectory.isDirectory()){
			if(!dstDirectory.exists()){
				dstDirectory.mkdirs();
			}
			for(File file : srcDirectory.listFiles()){
				if(!file.isDirectory()){
					if(!copyFile(file.getAbsolutePath(), dstDirectory.getAbsolutePath() + File.separator + file.getName())){
						return false;
					}
				}
				else if(file.isDirectory() && recursive){
					if(!copyFolder(file.getAbsolutePath(), dstDirectory.getAbsolutePath(), true)){
						return false;
					}
				}

			}
			return true;
		}
		MessageGenerator.briefError("ERROR: copyFolder() - Cannot find directory: " + srcDirectoryPath);
		return false;
	}
	
	/**
	 * Copies the folder contents of the folder specified by src to folder specified as dst.  It will
	 * copy all files in it to the new location.  If the recursive
	 * flag is set, it will copy everything recursively in the folder src to dst.
	 * @param src The source folder to copy.
	 * @param dst The location of where the copy of the contents of src will be located.
	 * @param recursive A flag indicating if sub folders and their contents should be
	 * copied.
	 * @return True if operation is successful, false otherwise.
	 */
	public static boolean copyFolderContents(String src, String dst, boolean recursive){
		File srcDirectory = new File(src);
		File dstDirectory = new File(dst);
		if(srcDirectory.exists() && srcDirectory.isDirectory()){
			if(!dstDirectory.exists()){
				MessageGenerator.briefError("ERROR: Could find destination directory " + dstDirectory.getAbsolutePath());
			}
			for(File file : srcDirectory.listFiles()){
				if(!file.isDirectory()){
					if(!copyFile(file.getAbsolutePath(), dstDirectory.getAbsolutePath() + File.separator + file.getName())){
						return false;
					}
				}
				else if(file.isDirectory() && recursive){
					if(!copyFolder(file.getAbsolutePath(), dst, true)){
						MessageGenerator.briefError("ERROR: While copying folder " + file.getAbsolutePath() +
								" to " + dst + File.separator + file.getName());
						return false;
					}
				}
			}
			return true;
		}
		MessageGenerator.briefError("ERROR: copyFolderContents() - Cannot find directory: " + src);
		return false;
	}
	//===================================================================================//
	/* Simple Device/WireEnumeration Load Methods & Helpers                              */
	//===================================================================================//


	/**
	 * This method will get and return the current time as a string
	 * formatted in the same way used in most Xilinx report and XDL
	 * files.  The format used in the using the same syntax as SimpleDateFormat
	 * which is "EEE MMM dd HH:mm:ss yyyy".
	 * @return Current date and time as a formatted string.
	 */
	public static String getTimeString(){
		SimpleDateFormat formatter = new SimpleDateFormat("EEE MMM dd HH:mm:ss yyyy");
		return formatter.format(new java.util.Date());
	}

	/**
	 * Gets and returns the file separator character for the given OS
	 */
	public static String getDirectorySeparator(){
		if(FileTools.cygwinInstalled()){
			return "/";
		}
		else{
			return File.separator;
		}
	}

	/**
	 * Checks if Cygwin is installed on the system
	 */
	public static boolean cygwinInstalled(){
		return System.getenv("CYGWIN") != null;
	}
}
