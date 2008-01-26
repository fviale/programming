/*
 * ################################################################
 *
 * ProActive: The Java(TM) library for Parallel, Distributed,
 *            Concurrent computing with Security and Mobility
 *
 * Copyright (C) 1997-2007 INRIA/University of Nice-Sophia Antipolis
 * Contact: proactive@objectweb.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version
 * 2 of the License, or any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307
 * USA
 *
 *  Initial developer(s):               The ProActive Team
 *                        http://proactive.inria.fr/team_members.htm
 *  Contributor(s):
 *
 * ################################################################
 */
package functionalTests.activeobject.migration.strategy;

import static junit.framework.Assert.assertTrue;

import org.objectweb.proactive.api.PAActiveObject;
import org.objectweb.proactive.core.node.NodeFactory;

import functionalTests.FunctionalTestDefaultNodes;
import functionalTests.GCMDeploymentReady;


/**
 * Test migration strategy, with onDeparture and onArrival method
 * @author cmathieu
 *
 */
@GCMDeploymentReady
public class TestStrategy extends FunctionalTestDefaultNodes {
    A a;

    public TestStrategy() {
        super(DeploymentType._2x1);
    }

    @org.junit.Test
    public void action() throws Exception {
        String[] nodesUrl = new String[3];
        nodesUrl[0] = super.getANode().getNodeInformation().getURL();
        nodesUrl[1] = super.getANode().getNodeInformation().getURL();
        nodesUrl[2] = NodeFactory.getDefaultNode().getNodeInformation().getURL();

        A a = (A) PAActiveObject.newActive(A.class.getName(), new Object[] { nodesUrl });

        assertTrue(a.getCounter() == 7);
    }
}