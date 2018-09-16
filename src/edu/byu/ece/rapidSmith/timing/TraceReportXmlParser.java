package edu.byu.ece.rapidSmith.timing;

import edu.byu.ece.rapidSmith.design.Design;
import edu.byu.ece.rapidSmith.design.Instance;
import edu.byu.ece.rapidSmith.design.Pin;
import edu.byu.ece.rapidSmith.device.PrimitiveSite;
import edu.byu.ece.rapidSmith.device.PrimitiveType;
import edu.byu.ece.rapidSmith.timing.logic.LogicPathElement;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

/**
 * Created by jakobw on 02.07.15.
 */
public class TraceReportXmlParser {

    private Design design = null;
    public Map<String,PrimitiveSite> sitesByName = new HashMap<>(); //TODO make private

    private ArrayList<PathDelay> pathDelays;
    private ArrayList<PathOffset> pathOffsets;

    public ArrayList<PathDelay> getPathDelays() {
        return pathDelays;
    }

    /**
     * @return the pathOffsets
     */
    public ArrayList<PathOffset> getPathOffsets() {
        return pathOffsets;
    }

    public void parseTWX(String twxFileName, java.nio.file.Path xdlFileName){
        design = new Design();
        design.loadXDLFile(xdlFileName);
        parseTWX(twxFileName, design);
    }

    public void parseTWX(String twxFileName, Design design){
        this.design = design;

        for (PrimitiveSite primitiveSite : design.getUsedPrimitiveSites()) {
            sitesByName.put(primitiveSite.getName(),primitiveSite);
        }

        parseTWX(twxFileName);
    }

    public void parseTWX(String twxFileName){
        pathDelays = new ArrayList<PathDelay>();
        pathOffsets = new ArrayList<PathOffset>();


        SAXParserFactory factory = SAXParserFactory.newInstance();
        try {
            SAXParser saxParser = factory.newSAXParser();
            DefaultHandler handler = new TwxHandler();
            saxParser.parse( new File(twxFileName), handler );
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        } catch (SAXException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }


    class TwxHandler extends DefaultHandler{

        private static final String TW_CONST_OFF_IN = "twConstOffIn";
        private static final String TW_CONST_OFF_OUT = "twConstOffOut";
        private static final String TW_CONST_PATH = "twConstPath";
        private static final String TW_DATA_PATH_TYPE = "twDataPathType";
        private static final String TW_DATA_PATH_MAX_DELAY = "twDataPathMaxDelay";
        private static final String TW_LOG_DEL = "twLogDel";
        private static final String TW_DET_PATH = "twDetPath";
        private static final String TW_TOT_DEL = "twTotDel";
        private static final String TW_DEST_CLK = "twDestClk";
        private static final String TW_SRC_CLK = "twSrcClk";
        private static final String TW_ROUTE_DEL = "twRouteDel";
        private static final String TW_SLACK = "twSlack";
        private static final String TW_DEL_TYPE = "twDelType";
        private static final String TW_SITE = "twSite";
        private static final String TW_PCT_ROUTE = "twPctRoute";
        private static final String TW_COMP = "twComp";
        private static final String TW_CLK_SKEW = "twClkSkew";
        private static final String TW_DEL_CONST = "twDelConst";
        private static final String TW_DEL_INFO = "twDelInfo";
        private static final String TW_FAN_CNT = "twFanCnt";
        private static final String TW_CLK_UNCERT = "twClkUncert";
        private static final String TW_LOG_LVLS = "twLogLvls";
        private static final String TW_TOT_PATH_DEL = "twTotPathDel";
        private static final String TW_DEST = "twDest";
        private static final String TW_PATH_DEL = "twPathDel";
        private static final String TW_SRC = "twSrc";
        private static final String TW_PCT_LOG = "twPctLog";
        private static final String TW_SRC_SITE = "twSrcSite";
        private static final String TW_BEL = "twBEL";
        private static final String NET = "net";
		private static final String TW_CLK_PATH = "twClkPath";

        PathDelay currentPath = null;

        //We only know if we should instantiate a LogicPathElement or a RoutingPathElement after we have seen the twDelType.
        //Therefore we save all data in a map and instantiate the element in endElement.
        Map<String,String> elementData = null;

        Set<String> bels = null;

        String characters = null;

        private boolean isIOB = false;
        private boolean inClkPath = false;



        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
            isIOB = false;
            switch (qName) {
                case TW_CONST_OFF_IN:
                case TW_CONST_OFF_OUT: isIOB = true; //fallthrough to next case!
                case TW_CONST_PATH:
                    //Only look at setup times for now.
                    if (attributes.getValue(TW_DATA_PATH_TYPE).equals(TW_DATA_PATH_MAX_DELAY)) {
                        currentPath = new PathDelay();
                        break;
                    }
                case TW_PATH_DEL:
                    if (currentPath!=null) {
                        elementData = new HashMap<>();
                        bels = new HashSet<>();
                    }
                    break;
				case TW_CLK_PATH:
					inClkPath = true;
					break;
            }
            characters=null;
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            switch (qName) {
                case TW_CONST_OFF_IN:
                case TW_CONST_OFF_OUT:
                case TW_CONST_PATH:
                    if (currentPath!=null)
                        pathDelays.add(currentPath);
                    currentPath=null;
                    break;
                case TW_SLACK:
                    if (currentPath!=null)
                        currentPath.setSlack(Float.parseFloat(characters));
                    break;
                case TW_SRC:
                    if (currentPath!=null && !inClkPath)
                        currentPath.setSource(characters);
                    break;
                case TW_DEST:
					if (currentPath!=null && !inClkPath)
                        currentPath.setDestination(characters);
                    break;
                case TW_TOT_PATH_DEL:
                    if (currentPath!=null)
                        currentPath.setDelay(Float.parseFloat(characters));
                    break;
                case TW_CLK_SKEW:
                    if (currentPath!=null)
                        currentPath.setClockPathSkew(Float.parseFloat(characters));
                    break;
                case TW_DEL_CONST:
                    if (currentPath!=null)
                        currentPath.setDelayConstraint(Float.parseFloat(characters));
                    break;
                case TW_CLK_UNCERT:
                    if (currentPath!=null)
                        currentPath.setClockPathSkew(Float.parseFloat(characters));
                    break;

                case TW_LOG_LVLS:
                    if (currentPath!=null)
                        currentPath.setLevelsOfLogic(Integer.parseInt(characters));
                    break;
                case TW_LOG_DEL:
                    if (currentPath!=null)
                        currentPath.setDataPathDelay(Float.parseFloat(characters));
                    break;
                case TW_ROUTE_DEL:
                    if (currentPath!=null)
                        currentPath.setRoutingDelay(Float.parseFloat(characters));
                    break;

                case TW_PATH_DEL:
					if (!inClkPath) {
						if (currentPath != null) {
							PathElement elem = makePathDel();
							if (elem != null)
								currentPath.getMaxDataPath().add(elem);
							else
								currentPath = null; //Broken path, discard...
						}

						bels = null;
						elementData = null;
					}
                    break;

                case TW_SITE:
                case TW_DEL_TYPE:
                case TW_FAN_CNT:
                case TW_DEL_INFO:
                case TW_COMP:
                    if (elementData!=null && !inClkPath)
                        elementData.put(qName,characters);
                    break;

                case TW_BEL:
                    if (bels!=null && !inClkPath)
                        bels.add(characters);
                    break;
				case TW_CLK_PATH:
					inClkPath = false;
					break;

            }
        }

        private PathElement makePathDel() {
            PathElement result;
            if (elementData.get(TW_DEL_TYPE).equals(NET)) {
                result = new RoutingPathElement();
            } else {
                result = new LogicPathElement();

            }
            String[] site = elementData.get(TW_SITE).split("\\.");

            PrimitiveSite primitiveSite = sitesByName.get(site[0]);
            if (primitiveSite==null)
                throw new NullPointerException("could not find primitive site for "+site[0]+", available: "+sitesByName.keySet());

            Instance instance = design.getInstanceAtPrimitiveSite(primitiveSite);
            if (instance == null)
                throw new NullPointerException("could not find instance for "+primitiveSite);


            String pinName = site[1];
            //Rewrite pin Name because of incorrect definition...
            if (instance.getType()== PrimitiveType.IODRP2 && pinName.equals("BUSY"))
                pinName="SDO";

            //PAD is not actually a pin, we replace it with O
            if (instance.isIOB() && pinName.equals("PAD"))
                pinName="O";

            Pin p = instance.getPin(pinName);
            if (p==null) {
                System.err.println("could not find pin " + site[1] + " at " + instance.getName() + " (" + instance.getType() + ", " + primitiveSite + "), discarding path");
                return null;
            }
            result.setPin(p);

            result.setType(elementData.get(TW_DEL_TYPE));
            result.setDelay(Float.parseFloat(elementData.get(TW_DEL_INFO)));
            result.setComponent(elementData.get(TW_COMP));

            if (result instanceof  RoutingPathElement) {
                ((RoutingPathElement) result).setNet(p.getNet());
                ((RoutingPathElement) result).setFanout(Integer.parseInt(elementData.get(TW_FAN_CNT)));
            } else if (result instanceof LogicPathElement) {
                ((LogicPathElement) result).setLogicalResources(new ArrayList<>(bels));
            }

            return result;
        }

        @Override
        public void characters (char ch[], int start, int length) {
            if (characters==null)
                characters=new String(ch,start,length);
            else characters+=new String(ch,start,length);
        }
    }
}
