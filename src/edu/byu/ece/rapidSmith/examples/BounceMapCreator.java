
package edu.byu.ece.rapidSmith.examples;

import edu.byu.ece.rapidSmith.device.Device;
import edu.byu.ece.rapidSmith.device.PrimitiveSite;
import edu.byu.ece.rapidSmith.device.PrimitiveType;
import edu.byu.ece.rapidSmith.device.SinkPin;
import edu.byu.ece.rapidSmith.device.Tile;
import edu.byu.ece.rapidSmith.device.TileType;
import edu.byu.ece.rapidSmith.device.WireConnection;
import edu.byu.ece.rapidSmith.device.WireEnumerator;
import edu.byu.ece.rapidSmith.util.DeviceDatabaseProvider;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;


public class BounceMapCreator {

	private static Device dev;
	private static WireEnumerator we;
	private static Tile switchMatrix;
	private static HashMap<String,HashSet<String>> BounceMap = new HashMap<String,HashSet<String>>();
	private static HashSet<Integer> sinkWires = new HashSet<Integer>();
	private static HashSet<String> TieoffWires= new HashSet<String>();
	private static HashSet<Integer> sliceSinkWires = new HashSet<Integer>();
	private static HashSet<TileType> visitedIntTypes = new HashSet<TileType>();

	private static void createSinkWireList(PrimitiveSite site)
	{
		HashMap<Integer,SinkPin> sinkMap = site.getTile().getSinks();
		Iterator<SinkPin> iter = sinkMap.values().iterator();

		while(iter.hasNext()) {
			SinkPin p = iter.next();
			if(p.switchMatrixSinkWire!= -1){

				sinkWires.add(p.switchMatrixSinkWire);
//				System.out.println("Sinkwire:    "+we.getWireName(p.switchMatrixSinkWire)+"   "+p.getXSwitchMatrixTileOffset() +" "+ p.getYSwitchMatrixTileOffset());

			}
		}
	}

	private static void putBounceMap(TileNode node)
	{
		HashSet<String> s = new HashSet<String>();

		s.add(we.getWireName(node.getRoot().getWire()));

		if(BounceMap.containsKey(we.getWireName(node.getWire())))
			s.addAll(BounceMap.get(we.getWireName(node.getWire())));

		BounceMap.put(we.getWireName(node.getWire()), s);		
	}

	private static void getConnections(TileNode tn) {	

		for(WireConnection wc : tn.getConnWires()) {
			TileNode currentNode = new TileNode();
			//populate currentNode with properties of the WireConnection and the parent
			currentNode.setWire(wc.getWire());
			currentNode.setConnWires(switchMatrix.getWireConnections(wc.getWire()));
			currentNode.setParent(tn);	
			currentNode.setLevel(tn.getLevel()+1);

//			String space="";

//			for(int i=0;i<currentNode.getLevel();i++)
//			{
//				space+=" ";
//			
//			}
//			System.out.printf(space+we.getWireName(currentNode.getWire())+" : "+we.getWireDirection(currentNode.getWire())  +"\n");

			if (tn.getLevel()!=0)
			{
				if(TieoffWires.contains(we.getWireName(currentNode.getWire())) )
					continue;

				if(currentNode.isLoop())
					continue;
				
				if(sinkWires.contains(currentNode.getWire()))
					putBounceMap(currentNode);
				
			}	
			if (currentNode.getConnWires() != null )
				getConnections(currentNode);
				
		}
	}


	public static void createBounceMap()
	{
		PrimitiveSite tieoff = switchMatrix.getPrimitiveSites()[0];

		//	System.out.println("PS: "+tieoff.getName());

		for(String pin : tieoff.getPins().keySet()){
			//if(pin.equals("HARD0") || pin.equals("KEEP1")) continue;
			if(!pin.equals("HARD0")) continue;

			TileNode root = new TileNode();
			root.setConnWires(switchMatrix.getWireConnections(tieoff.getExternalPinWireEnum(pin))); // all wire connections at tie-off

			if(root.getConnWires()==null)
				continue;

			for(WireConnection wc : root.getConnWires())
			{
				//System.out.println("   wc:"+we.getWireName(wc.getWire()));
				TieoffWires.add(we.getWireName(wc.getWire()));

			}
			/*
			for(WireConnection wirecon : root.getConnWires())
			{
				System.out.println("   wc:"+we.getWireName(wirecon.getWire()));
			}
			 */
			getConnections(root);			
		}	

	}

	public static void writeSliceMap()
	{

		sliceSinkWires.addAll(sinkWires);

		for(String key : BounceMap.keySet()){
			sliceSinkWires.remove(we.getWireEnum(key));
		}

		System.out.println(sliceSinkWires.toString());

		for(int key : sinkWires){
			System.out.print(we.getWireName(key)+", ");
		}
		System.out.println("");
		System.out.println(sinkWires.toString());

	}
	public static void writeBounceMap()
	{
		File file;
		FileWriter writer;
		file = new File("BounceMap.txt");


		try{
			writer = new FileWriter(file,false);

			writer.write("s6BounceMap=new HashMap<String,String[]>();");
			writer.write(System.getProperty("line.separator"));


			for(String key : BounceMap.keySet())
			{
				writer.write("s6BounceMap.put(\""+key+"\", new String[]{");

				Iterator<String> iter =BounceMap.get(key).iterator();

				writer.write("\""+iter.next()+"\"");

				while(iter.hasNext())
				{
					writer.write(",\""+iter.next()+"\"");
				}

				writer.write("});");
				writer.write(System.getProperty("line.separator"));
			}
			writer.close();
		}catch(IOException e) {	}		

	}


	public static HashMap<String,String[]> getBounceMap(Device dev)
	{

		we = dev.getWireEnumerator();


		for(PrimitiveSite site : dev.getPrimitiveSites().values()){

			createSinkWireList(site);
		}


		for(Tile[] tileArray : dev.getTiles())
		{
			for(Tile tile : tileArray )
			{
				if(tile.getType()==TileType.INT) {
					switchMatrix=tile;
					createBounceMap();
				}
			}
		}


		writeBounceMap();
		HashMap<String, String[]> returnMap = new HashMap<String, String[]>();
		for (String s : BounceMap.keySet()) {

			String [] values = new String[10];
			int i=0;
			for(String s2: BounceMap.get(s)){

				values[i]=s2;
				i++;
			}
			returnMap.put(s, values);
		}

		return returnMap;
	}

	public static void main(String[] args) {
		String spartan6Name = 	"xc5vlx20tff323"; //"xc6slx16csg324";  "xc6slx4tqg144"; "xc5vlx20tff323";

		dev=DeviceDatabaseProvider.getDeviceDatabase().loadDevice(spartan6Name);
		we = DeviceDatabaseProvider.getDeviceDatabase().loadWireEnumerator(spartan6Name);

		for(PrimitiveSite site : dev.getPrimitiveSites().values()){
//			System.out.println(site.getName());
			createSinkWireList(site);
		}

		int maxrow=0, maxcol=0;
		
		for(PrimitiveSite ps : dev.getAllPrimitiveSitesOfType(PrimitiveType.TIEOFF)){
			if(ps.getInstanceX()>maxrow)
				maxrow=ps.getInstanceX();

			if(ps.getInstanceY()>maxcol)
				maxcol=ps.getInstanceY();
			
		}
		System.out.println("col: "+maxcol+" row: "+maxrow);

	for(Tile[] tileArray : dev.getTiles())
		{
			for(Tile tile : tileArray )
			{
				if(tile.getPrimitiveSites()==null)
					continue;

				for(PrimitiveSite ps :tile.getPrimitiveSites()){
					if(ps.isCompatiblePrimitiveType(PrimitiveType.TIEOFF)){
						switchMatrix=tile;
						
					if(tile.getTileXCoordinate()==0 || tile.getTileYCoordinate()==0 || tile.getTileXCoordinate()==maxrow || tile.getTileYCoordinate()==maxcol)
						continue;
					
					
						if(visitedIntTypes.contains(tile.getType())) 
							continue;
						
						visitedIntTypes.add(tile.getType());
					System.out.println("Tile:"+tile.getName());
						createBounceMap();	
					
					}
				}
			}
		}

//		writeSliceMap();
		writeBounceMap();
	}
}

