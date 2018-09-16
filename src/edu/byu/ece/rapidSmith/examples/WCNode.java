
package edu.byu.ece.rapidSmith.examples;

public class WCNode extends TileNode{

	private int xOffset;
	private int yOffset;
	private int switchMatrixSinkwire;
	

	public WCNode(int switchMatrixSinkwire, int x, int y){
		super();
		this.xOffset=x;
		this.yOffset=y;
		this.switchMatrixSinkwire=switchMatrixSinkwire;
	}
	
	public void addOffset(int x, int y){
		this.xOffset+=x;
		this.yOffset+=y;
	}
	
	public int getXOffset(){
		return this.xOffset;
	}
	
	public int getYOffset(){
		return this.yOffset;
	}
	
	public int getSwitchMatrixSinkwire(){
		return this.switchMatrixSinkwire;
	}
}
