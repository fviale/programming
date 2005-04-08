/*
 * ################################################################
 *
 * ProActive: The Java(TM) library for Parallel, Distributed,
 *            Concurrent computing with Security and Mobility
 *
 * Copyright (C) 1997-2002 INRIA/University of Nice-Sophia Antipolis
 * Contact: proactive-support@inria.fr
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307
 * USA
 *
 *  Initial developer(s):               The ProActive Team
 *                        http://www.inria.fr/oasis/ProActive/contacts.html
 *  Contributor(s):
 *
 * ################################################################
 */
package org.objectweb.proactive.examples.nbody.common;

import org.objectweb.proactive.ActiveObjectCreationException;
import org.objectweb.proactive.ProActive;
import org.objectweb.proactive.core.ProActiveException;
import org.objectweb.proactive.core.descriptor.data.ProActiveDescriptor;
import org.objectweb.proactive.core.descriptor.data.VirtualNode;
import org.objectweb.proactive.core.node.Node;
import org.objectweb.proactive.core.node.NodeException;

import java.io.IOException;
import java.io.Serializable;


/**
 * <P>
 * This launches the different versions of the nbody example
 * <ul>
 * <li> simple : simplest version, one-to-one communication and master</li>
 * <li> groupcom : group communication and master</li>
 * <li> groupdistrib : odd-even-synchronization</li>
 * <li> groupoospmd : oospmd synchronization</li>
 * <li> barneshut : an implementation of the Barnes-Hutalgorithm</li>
 * </ul>
 * </P>
 *
 * @author  ProActive Team
 * @version 1.0,  2005/04
 * @since   ProActive 2.2
 */
public class Start implements Serializable {
    private ProActiveDescriptor descriptorPad;

    /**
     * Options should be "java Start xmlFile [-nodisplay|-displayft] totalNbBodies maxIter"
     * Parameters can be <ul>
     * <li> -nodisplay, which is not compulsory, specifies whether a graphic display is to be created.</li>
     * <li> -displayft, which is not compulsory, specifies whether a fault-generating panel should be created.</li>
     * <li> xmlFile is the xml deployment file..</li>
     * <li> totalNbBodies  The number of Planets in the System.</li>
     * <li> maxIter The number of iterations before the program stops.</li>
     * </ul>
     */
    public static void main(String[] args) {
        new Start().run(args);
    }

    public void run(String[] args) {
        int input = 0;
        boolean display = true;
        boolean displayft = false;
        int totalNbBodies = 4;
        int maxIter = 10000;
        String xmlFileName;

        // Set arguments as read on command line
        switch (args.length) {
        case 0:
            usage();
            System.out.println("No xml descriptor specified - aborting");
            quit();
        case 2:
            if (args[1].equals("-nodisplay")) {
                display = false;
                break;
            } else if (args[1].equals("-displayft")) {
                displayft = true;
                break;
            }
        case 3:
            totalNbBodies = Integer.parseInt(args[1]);
            maxIter = Integer.parseInt(args[2]);
            break;
        case 4:
            if (args[1].equals("-nodisplay")) {
                display = false;
                totalNbBodies = Integer.parseInt(args[2]);
                maxIter = Integer.parseInt(args[3]);
                break;
            } else if (args[1].equals("-displayft")) {
                displayft = true;
                totalNbBodies = Integer.parseInt(args[2]);
                maxIter = Integer.parseInt(args[3]);
                break;
            }

        // else : don't break, which means go to the default case
        default:
            usage();
        }
        System.out.println("        Running with options set to " +
            totalNbBodies + " bodies, " + maxIter + " iterations, display " +
            display);
        xmlFileName = args[0];

        System.out.println(
            " 1 : Simplest version, one-to-one communication and master");
        System.out.println(" 2 : group communication and master");
        System.out.println(" 3 : group communication, odd-even-synchronization");
        if (displayft) {
            System.out.print("Choose which version you want to run [123] : ");
            try {
                while (true) {
                    // Read a character from keyboard
                    input = System.in.read();
                    if (((input >= 49) && (input <= 51)) || (input == -1)) {
                        break;
                    }
                }
            } catch (IOException ioe) {
                abort(ioe);
            }
        } else {
            System.out.println(
                " 4 : group communication, oospmd synchronization");
            System.out.println(" 5 : Barnes-Hut, and oospmd");
            System.out.print("Choose which version you want to run [12345] : ");
            try {
                while (true) {
                    // Read a character from keyboard
                    input = System.in.read();
                    if (((input >= 49) && (input <= 53)) || (input == -1)) {
                        break;
                    }
                }
            } catch (IOException ioe) {
                abort(ioe);
            }
        }
        System.out.println("Thank you!");
        // If need be, create a displayer
        Displayer displayer = null;
        if (display) {
            try {
                displayer = (Displayer) (ProActive.newActive(Displayer.class.getName(),
                        new Object[] {
                            new Integer(totalNbBodies), new Boolean(displayft)
                        }));
            } catch (ActiveObjectCreationException e) {
                abort(e);
            } catch (NodeException e) {
                abort(e);
            }
        }

        // Construct deployment-related variables: pad & nodes
        descriptorPad = null;
        VirtualNode vnode;
        try {
            descriptorPad = ProActive.getProactiveDescriptor(xmlFileName);
        } catch (ProActiveException e) {
            abort(e);
        }
        descriptorPad.activateMappings();
        vnode = descriptorPad.getVirtualNode("Workers");
        Node[] nodes = null;
        try {
            nodes = vnode.getNodes();
        } catch (NodeException e) {
            abort(e);
        }
        switch (input) {
        case 49:
            org.objectweb.proactive.examples.nbody.simple.Start.main(totalNbBodies,
                maxIter, displayer, nodes, this);
            break;
        case 50:
            org.objectweb.proactive.examples.nbody.groupcom.Start.main(totalNbBodies,
                maxIter, displayer, nodes, this);
            break;
        case 51:
            org.objectweb.proactive.examples.nbody.groupdistrib.Start.main(totalNbBodies,
                maxIter, displayer, nodes, this);
            break;
        case 52:
            org.objectweb.proactive.examples.nbody.groupoospmd.Start.main(totalNbBodies,
                maxIter, displayer, nodes, this);
            break;
        case 53:
            org.objectweb.proactive.examples.nbody.barneshut.Start.main(totalNbBodies,
                maxIter, displayer, nodes, this);
            break;
        }
    }

    /**
     * Shows what are the possible options to this program.
     */
    private void usage() {
        String options = "[-nodisplay | -displayft] totalNbBodies maxIter";
        System.out.println("        Usage : nbody.[bat|sh] " + options);
        System.out.println(
            "        from the command line, it would be   java Start xmlFile " +
            options);
    }

    /**
     * Stop with an error.
     * @param e the Exception which triggered the abrupt end of the program
     */
    public void abort(Exception e) {
        System.err.println("This is an unhandled behavior!");
        e.printStackTrace();
    }

    /**
     * End the program, removing extra JVM that have been created with the deployment of the Domains
     */
    public void quit() {
        System.out.println(" PROGRAM ENDS " + descriptorPad);
        try {
            descriptorPad.killall(true);
        } catch (ProActiveException e) {
            e.printStackTrace();
        }
        System.exit(0);
    }
}
