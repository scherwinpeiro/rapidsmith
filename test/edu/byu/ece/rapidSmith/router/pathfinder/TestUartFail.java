package edu.byu.ece.rapidSmith.router.pathfinder;


import edu.byu.ece.rapidSmith.design.Design;
import edu.byu.ece.rapidSmith.design.Instance;
import edu.byu.ece.rapidSmith.design.Net;
import edu.byu.ece.rapidSmith.design.NetType;
import edu.byu.ece.rapidSmith.design.Pin;
import edu.byu.ece.rapidSmith.device.PrimitiveType;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Iterator;
import java.util.stream.Collectors;

/**
 * Created by wenzel on 09.11.16.
 */
public class TestUartFail {
    private static void connectPin(Design design, Pin p) {
        p.getNet().removePin(p);

        Instance i = new Instance("CONN_"+p.getInstance().getName()+"/"+p.getName(), PrimitiveType.IOB);
        design.addInstance(i);

        Net net = new Net("NET_"+p.getInstance().getName()+"/"+p.getName(), NetType.WIRE);
        net.addPin(p);

        Pin connPin;
        if (p.isOutPin()) {
            connPin = new Pin(false,"O",i);
            net.addPin(connPin);
            net.setSource(p);
        } else {
            connPin = new Pin(true,"I",i);
            net.addPin(connPin);
            net.setSource(connPin);
        }

        design.addNet(net);
    }

    private static void connectPins(Design design, Collection<Instance> instances) {
        instances.forEach(inst -> {
            inst.getPins().forEach(pin -> {
                Net net = pin.getNet();
                for (Iterator<Pin> iterator = net.getPins().iterator(); iterator.hasNext(); ) {
                    Pin connPin = iterator.next();
                    if (!instances.contains(connPin.getInstance())) {
                        iterator.remove();
                    }
                }
                if (net.getPins().size()==1) {
                    connectPin(design, pin);
                } else {
                    if (!design.getNets().contains(net)) {

                        design.addNet(net);
                    }
                }
            });
        });
    }

    public static void main(String[] args) throws IOException, ClassNotFoundException {
        Design design = new Design(Paths.get(args[0]));
        Instance instance = design.getInstance("subsystem_0_uart_light_0/word_rx[4]");

        removePins(design,instance);

        Collection<Instance> instancesToKeep = instance.getNetList().stream().flatMap(n->n.getPins().stream()).map(p->p.getInstance()).collect(Collectors.toSet());
        instancesToKeep.add(instance);



        design.getNets().clear();
        design.getInstances().clear();
        instancesToKeep.forEach(design::addInstance);

        connectPins(design,instancesToKeep);

/*
        Collection<Net> nets = instance.getNetList();
        Collection<Instance> instances = instance.getPins().stream()
                /*.flatMap(pin -> {
                    if (pin.getNets().getSource()==pin) {
                        //Add all internal sinks and at most one external one
                        boolean externalAdded = false;


                        for (Iterator<Pin> iterator = pin.getNets().getPins().iterator(); iterator.hasNext(); ) {
                            Pin p = iterator.next();
                            if (p.getInstance()!=instance) {
                                if (externalAdded) {
                                    iterator.remove();
                                } else {
                                    externalAdded = true;
                                }
                            }
                            if (p!=pin && p!=pin.getNets().getSource())
                                iterator.remove();
                        }

                        return pin.getNets().getPins().stream()
                                .filter(connPin -> connPin != pin);
                    } else {
                        for (Iterator<Pin> iterator = pin.getNets().getPins().iterator(); iterator.hasNext(); ) {
                            Pin p = iterator.next();
                            if (p!=pin && p!=pin.getNets().getSource())
                                iterator.remove();
                        }
                        //Just add the source
                        return Stream.of(pin.getNets().getSource());
                    }
                })* /
                .map(Pin::getNets)
                .map(Net::getPins)
                .flatMap(Collection::stream)
                .map(Pin::getInstance)
                .distinct()
                .collect(Collectors.toSet());

        instances.add(instance);

        for (Iterator<Instance> iterator = design.getInstances().iterator(); iterator.hasNext(); ) {
            Instance inst = iterator.next();
            if (!instances.contains(inst))
                iterator.remove();
        }

        for (Iterator<Net> iterator = design.getNets().iterator(); iterator.hasNext(); ) {
            Net net = iterator.next();
            if (!nets.contains(net))
                iterator.remove();
            else net.getPIPs().clear();
        }
*/
        design.saveXDLFile(Paths.get(args[1]));

        String[] pfArgs = {
                args[1],
                "calibration.cal",
                args[1]+"out"
        };
        //Pathfinder.main(pfArgs);

    }

    private static void removePins(Design design, Instance instance) {
        /*instance.getPins().forEach(pin -> {
            Net net = pin.getNets();

            if (net.getSource()==pin) {
                //All sinks are irrelevant
                /*net.getPins().clear();
                net.addPin(pin);* /
                //Will assign an out net later
            } else {
                //Clear all other sinks
                net.getPins().clear();
                net.addPin(net.getSource());
                net.addPin(pin);

            }
            net.unroute();
        });*/
    }
}
