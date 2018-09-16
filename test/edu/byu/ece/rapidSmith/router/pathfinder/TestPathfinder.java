package edu.byu.ece.rapidSmith.router.pathfinder;


import edu.byu.ece.rapidSmith.design.Design;
import edu.byu.ece.rapidSmith.design.Instance;
import edu.byu.ece.rapidSmith.design.Net;
import edu.byu.ece.rapidSmith.design.NetType;
import edu.byu.ece.rapidSmith.design.Pin;
import edu.byu.ece.rapidSmith.primitiveDefs.PrimitiveDefList;
import edu.byu.ece.rapidSmith.router.RoutingElement;
import edu.byu.ece.rapidSmith.timing.DesignWalker;
import edu.byu.ece.rapidSmith.timing.PathDelay;
import edu.byu.ece.rapidSmith.timing.TimingCalculator;
import edu.byu.ece.rapidSmith.timing.routing.LinearDelayModel;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

/**
 * Simple Testcases, as they appear in the original Pathfinder paper
 */
@Ignore
public class TestPathfinder {
    
    private class TestGraph {

        public final MockPathfinderNode s1;
        public final MockPathfinderNode s2;
        public final MockPathfinderNode s3;
        public final MockPathfinderNode d1;
        public final MockPathfinderNode d2;
        public final MockPathfinderNode d3;
        public final MockPathfinderNode a;
        public final MockPathfinderNode b;
        public final MockPathfinderNode c;
        private final MockNodeFactory mnf;
        private final Design design;
        //test

        public TestGraph() {
            design = new Design("","xc6slx16csg324");

            Instance i = new Instance();

            Pin s1pin = new Pin(true,"S1",i);
            Pin s2pin = new Pin(true,"S2",i);
            Pin s3pin = new Pin(true,"S3",i);


            Pin d1pin = new Pin(false,"D1",i);
            Pin d2pin = new Pin(false,"D2",i);
            Pin d3pin = new Pin(false,"D3",i);

            Net n1 = new Net("n1", NetType.WIRE);
            Net n2 = new Net("n2", NetType.WIRE);
            Net n3 = new Net("n3", NetType.WIRE);

            n1.addPin(s1pin);
            n2.addPin(s2pin);
            n3.addPin(s3pin);

            n1.addPin(d1pin);
            n2.addPin(d2pin);
            n3.addPin(d3pin);

            n1.setSource(s1pin);
            n2.setSource(s2pin);
            n3.setSource(s3pin);

            design.addInstance(i);

            design.addNet(n1);
            design.addNet(n2);
            design.addNet(n3);

            design.getDevice().getTileMap().put("mock",MockPathfinderNode.mockTile);

            mnf = new MockNodeFactory(design.getDevice(), design.getWireEnumerator());

            s1 = mnf.createNodeForPin(s1pin);
            s2 = mnf.createNodeForPin(s2pin);
            s3 = mnf.createNodeForPin(s3pin);


            d1 = mnf.createNodeForPin(d1pin);
            d2 = mnf.createNodeForPin(d2pin);
            d3 = mnf.createNodeForPin(d3pin);

            a = new MockPathfinderNode("A", design.getWireEnumerator());
            b = new MockPathfinderNode("B", design.getWireEnumerator());
            c = new MockPathfinderNode("C", design.getWireEnumerator());


			mnf.registerNode(a);
            mnf.registerNode(b);
            mnf.registerNode(c);
        }

        public Pathfinder createPathfinder() {
            PrimitiveDefList primitiveDefList = new PrimitiveDefList();
            TimingCalculator dummyTimingCalculator = new TimingCalculator(primitiveDefList,new LinearDelayModel(1,1,1,1,Collections.emptyMap()),design) {
                @Override
                public void calculateTimings(BiConsumer<RoutingElement, RoutingElement> unknownDelayConsumer) {
                    //Do nothing :)
                }

				@Override
				public Stream<PathDelay> getCriticalPaths(double epsilon) {
                	//Do nothing :)
					return Stream.empty();
				}
            };
            return new Pathfinder(design, mnf, new NodeCostFactory(), dummyTimingCalculator,primitiveDefList, false) {
				@Override
				protected double getNodeDelay(PathfinderNode current, PathfinderNode next) {
					return ((MockPathfinderNode) current).getCost(next);
				}

				@Override
				protected void printStatus() {
					printData(this);
				}

				@Override
				protected double estimateRemainingCost(PathfinderNode current, PathfinderNode sink) {
					return 0;
				}

				@Override
				protected void restrictRoutethroughs(PrimitiveDefList primitives) {
				}
			};
        }
    }

    @Test
    public void testFirstOrderCongestion() {
        TestGraph t = new TestGraph();

        t.s1.connectTo(t.a,2);
        t.s1.connectTo(t.b,1); 

        t.s2.connectTo(t.a,3); 
        t.s2.connectTo(t.b,1); 
        t.s2.connectTo(t.c,4); 

        t.s3.connectTo(t.b,1); 
        t.s3.connectTo(t.c,3); 

        t.a.connectTo(t.d1,2); 
        t.a.connectTo(t.d2,3); 

        t.b.connectTo(t.d1,1); 
        t.b.connectTo(t.d2,1); 
        t.b.connectTo(t.d3,1); 

        t.c.connectTo(t.d2,4); 
        t.c.connectTo(t.d3,3);


        Assert.assertEquals("Routing cost should be correct",12,route(t),1E-6);

    }

    @Test
    public void testSecondOrderCongestion() {
        TestGraph t = new TestGraph();

        t.s1.connectTo(t.a,2);
        t.s1.connectTo(t.b,1);

        t.s2.connectTo(t.b,2);
        t.s2.connectTo(t.c,1);


        t.s3.connectTo(t.c,1);

        t.a.connectTo(t.d1,2);

        t.b.connectTo(t.d1,1);
        t.b.connectTo(t.d2,2);

        t.c.connectTo(t.d2,1);
        t.c.connectTo(t.d3,1);


        Assert.assertEquals("Routing cost should be correct",10,route(t),1E-6);

    }

    private double route(TestGraph t) {
        Pathfinder pathfinder = t.createPathfinder();
        try {
            pathfinder.routeDesign();
        } catch (Exception e){
            printData(pathfinder);
            throw e;
        }
        return printData(pathfinder);
    }


    private double printData(Pathfinder pathfinder) {
        System.out.println();
        List<List<String>> allOutputs = new ArrayList<>();
        double designDelay = pathfinder.getNetDatas().keySet().stream().mapToDouble((Net net) -> {
            List<String> netOutput = new ArrayList<>(6);
            NetData netData = pathfinder.getNetDatas().get(net);

            netOutput.add(net.getName());
            netOutput.add("=============");


            double netDelay = DesignWalker.walkPath(((MockNodeFactory) pathfinder.getPathfinderNodeFactory()).getNode(net.getSource()),
                    (MockPathfinderNode n1, Boolean b)-> n1.neighbours.isEmpty(),
                    (MockPathfinderNode n, Integer i)-> n.neighbours.keySet().stream().filter(netData.routing::contains),
                    (List<MockPathfinderNode> r, MockPathfinderNode n) -> {
                        if (r==null)
                            return Collections.singletonList(n);
                        else {
                            List<MockPathfinderNode> res = new ArrayList<MockPathfinderNode>(r.size()+1);
                            res.add(n);
                            res.addAll(r);
                            return res;
                        }
                    },
                    null,
                    (List<MockPathfinderNode> l) -> { throw new RuntimeException("Loop"); }

                    ).mapToDouble((l) -> {
                        double totalDelay = 0;
                        for (int i = 0; i < l.size()-1; i++) {
                            MockPathfinderNode edgeStart = l.get(i);
                            MockPathfinderNode edgeEnd = l.get(i+1);

                            double delay = edgeStart.getCost(edgeEnd);
                            netOutput.add(String.format("%-2s -> %-2s: %3.1f",edgeStart,edgeEnd,delay));
                            totalDelay+=delay;
                        }
                        return totalDelay;
                    }).sum();
            netOutput.add("-------------");
            netOutput.add(String.format("%13.1f",netDelay));

            allOutputs.add(netOutput);

            return netDelay;
        }).sum();


        outputNetStats(allOutputs);


        System.out.println();
        System.out.format("Total:%37.1f%n",designDelay);
        System.out.println();


       /* Comparator<PathfinderNode> comparing = Comparator.comparing(PathfinderNode::toString);
        pathfinder.getPathfinderNodeFactory().getAllClusters().sorted(comparing).forEach(node -> {
            System.out.format("%-3s: %3d %3f%n",node.toString(),node.getCurrentUsage(),node.getHistoricalOveruse());
        });*/

        System.out.println();

        return designDelay;
    }

    private void outputNetStats(List<List<String>> allOutputs) {
        int numberOfLines = allOutputs.stream().mapToInt(List::size).max().orElse(0);
        int length = allOutputs.stream().flatMap(Collection::stream).mapToInt(String::length).max().orElse(0);
        String formatStr = "%-"+(length+2)+"s";
        for (int i = 0; i < numberOfLines; i++) {
            for (List<String> output : allOutputs) {
                String s = i<output.size() ? output.get(i) : "";
                System.out.format(formatStr,s);
            }
            System.out.println();
        }

    }

}