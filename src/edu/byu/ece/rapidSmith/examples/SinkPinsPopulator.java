
package edu.byu.ece.rapidSmith.examples;

import java.util.ArrayList;
import java.util.HashSet;

import edu.byu.ece.rapidSmith.device.Device;
import edu.byu.ece.rapidSmith.device.SinkPin;
import edu.byu.ece.rapidSmith.device.Tile;
import edu.byu.ece.rapidSmith.device.TileType;
import edu.byu.ece.rapidSmith.device.WireConnection;
import edu.byu.ece.rapidSmith.device.WireEnumerator;
import edu.byu.ece.rapidSmith.device.WireType;

public class SinkPinsPopulator {

	private static HashSet<Integer> setOfExternalPrimitivePins = new HashSet<Integer>();
	private static HashSet<TileType> switchMatrixTypes = new HashSet<TileType>();
	private static ArrayList<Integer> visited = new ArrayList();
	

	private void setSwitchMatrixTileTypes(){
		
			for (TileType type : TileType.values()) {
				if (type.name().contains("INT_") || type.name().endsWith("_INT") || type.name().equals("INT")) {
					switchMatrixTypes.add(type);
					System.out.println("INT Type: "+type.name());
				}
			}
//		//"xc6slx16csg324"
//		switchMatrixTypes.add(TileType.INT_INTERFACE);
//		switchMatrixTypes.add(TileType.INT_BRAM);
//		switchMatrixTypes.add(TileType.MCB_INT);
//		switchMatrixTypes.add(TileType.LIOI_INT);
//		switchMatrixTypes.add(TileType.IOI_INT);
//		switchMatrixTypes.add(TileType.HCLK_IOIR_INT);
//		switchMatrixTypes.add(TileType.HCLK_IOIL_INT);
//		switchMatrixTypes.add(TileType.HCLK_CLB_XL_INT);
//		switchMatrixTypes.add(TileType.HCLK_CLB_XM_INT);
//		switchMatrixTypes.add(TileType.DSP_INT_HCLK_FEEDTHRU);
//		switchMatrixTypes.add(TileType.INT_INTERFACE_IOI);
//		switchMatrixTypes.add(TileType.MCB_CAP_INT);
//		
//		//xc6slx4tqg144
//		switchMatrixTypes.add(TileType.BRAM_INT_INTERFACE_TOP);
//		switchMatrixTypes.add(TileType.DSP_INT_EMP_TOP);
//		switchMatrixTypes.add(TileType.DSP_INT_TTERM);
//		switchMatrixTypes.add(TileType.MCB_INT);
//		switchMatrixTypes.add(TileType.IOI_INT);
//		switchMatrixTypes.add(TileType.INT_BRAM);
//		switchMatrixTypes.add(TileType.INT_INTERFACE);
//		switchMatrixTypes.add(TileType.LIOI_INT);
//		switchMatrixTypes.add(TileType.MCB_INT_DUMMY);
//		switchMatrixTypes.add(TileType.INT_BRAM_BRK);
//		
//		switchMatrixTypes.add(TileType.INT);
//		switchMatrixTypes.add(TileType.INT_BRK);
//		switchMatrixTypes.add(TileType.INT_SO);
//		switchMatrixTypes.add(TileType.INT_SO_DCM0);
	}
	
	private static void findSinkPins(WireEnumerator we, edu.byu.ece.rapidSmith.examples.WCNode tn, Tile tile) {

		for(WireConnection wc : tn.getConnWires()) {
			edu.byu.ece.rapidSmith.examples.WCNode currentNode = new edu.byu.ece.rapidSmith.examples.WCNode(tn.getSwitchMatrixSinkwire(),tn.getXOffset(), tn.getYOffset());
			//populate currentNode with properties of the WireConnection and the parent
			currentNode.setWire(wc.getWire());
			currentNode.setConnWires(wc.getTile(tile).getWireConnections(wc.getWire()));
			currentNode.setParent(tn);	
			currentNode.setLevel(tn.getLevel()+1);
			currentNode.addOffset(wc.getColumnOffset(), wc.getRowOffset());
			
//		String space="";
//			
//	
//			for(int i=0;i<currentNode.getLevel();i++)
//			{
//				space+=" ";
//			
//			}
//			System.out.printf(space+we.getWireName(currentNode.getWire())+" : "+we.getWireDirection(currentNode.getWire())  +"\n");
			
			if (tn.getLevel()!=0)
			{
				
				if(currentNode.isLoop())	
				{
					
//		System.out.println("Loop!");
					continue;
				}
				
				if(setOfExternalPrimitivePins.contains(wc.getWire()) && !switchMatrixTypes.contains(wc.getTile(tile).getType())){
					SinkPin found = wc.getTile(tile).getSinks().get(wc.getWire());
				
					
					
//
					if(found == null){
		
						continue;
					}
					//System.out.println("Eintrag in "+tile.getName()+"!  switchmatrixsinkwire:"+we.getWireName(currentNode.getSwitchMatrixSinkwire())+" xoffset:"+currentNode.getXOffset()+" yoffset:"+currentNode.getYOffset());
					
					found.switchMatrixSinkWire = currentNode.getSwitchMatrixSinkwire();
					found.switchMatrixTileOffset = (currentNode.getXOffset() << 16) | (currentNode.getYOffset() & 0xFFFF);
					
					//visited.add(currentNode.getSwitchMatrixSinkwire());
				}
				
				if ((currentNode.getConnWires() == null) || (switchMatrixTypes.contains(wc.getTile(tile).getType()) && wc.getColumnOffset()!=0 && wc.getRowOffset()!=0) || we.getWireType(wc.getWire()).equals(WireType.INT_SINK) || visited.contains(wc.getWire()))//|| (we.getWireName(currentNode.getParent().getWire()).contains("FAN_B")) )//|| currentNode.getLevel() > 5) //nur ein BOUNCE erlaubt || currentNode.getLevel() > 4)
					{	// Cases to abort recursion
		
//			System.out.println("Abort! "+wc.getTile(tile).getType().name()+"  "+visited.contains(wc.getWire()));
					visited.add(wc.getWire());
				}
				else {
					visited.add(wc.getWire());
					findSinkPins(we, currentNode,wc.getTile(tile));
				}
			}
			else if (currentNode.getConnWires() != null)
			{
				findSinkPins(we, currentNode,wc.getTile(tile));
			}
		}
	}
	
	private static void createTree(WireEnumerator we,Tile tile)
	{
			
			for (int key : tile.getWireHashMap().keys) {
				if(we.getWireType(key).equals(WireType.INT_SINK)){
					edu.byu.ece.rapidSmith.examples.WCNode root = new edu.byu.ece.rapidSmith.examples.WCNode(key,0,0);
					root.setConnWires(tile.getWireHashMap().get(key));
							
//System.out.println("SwitchMatrixSinkWire: "+we.getWireName(key));

					findSinkPins(we, root, tile);
				}
			}
	}
	
	public void populateSinkPins(Device dev,WireEnumerator we){
		
		setSwitchMatrixTileTypes();
		

		for(String wire : we.getWires()) {
			int w = we.getWireEnum(wire);
			if(we.getWireType(w).equals(WireType.SITE_SINK)){
				setOfExternalPrimitivePins.add(w);
			}
		}

		for(Tile[] tileArray : dev.getTiles())
		{
			for(Tile tile : tileArray )
			{
				
				if(switchMatrixTypes.contains(tile.getType())){
	System.out.println("======================================================");			
	System.out.println("Tile:"+tile.getName());
	System.out.println("======================================================");
	
					createTree(we,tile);
					visited.clear();
				}

				
			}
		}
	}
}
