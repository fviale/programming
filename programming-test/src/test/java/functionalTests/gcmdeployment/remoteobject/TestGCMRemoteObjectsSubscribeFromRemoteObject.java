/*
 * ProActive Parallel Suite(TM):
 * The Open Source library for parallel and distributed
 * Workflows & Scheduling, Orchestration, Cloud Automation
 * and Big Data Analysis on Enterprise Grids & Clouds.
 *
 * Copyright (c) 2007 - 2017 ActiveEon
 * Contact: contact@activeeon.com
 *
 * This library is free software: you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation: version 3 of
 * the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 * If needed, contact us to obtain a release under GPL Version 2 or 3
 * or a different license than the AGPL.
 */
package functionalTests.gcmdeployment.remoteobject;

import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.Semaphore;

import org.junit.Test;
import org.objectweb.proactive.api.PAActiveObject;
import org.objectweb.proactive.core.ProActiveException;
import org.objectweb.proactive.core.node.Node;
import org.objectweb.proactive.core.remoteobject.RemoteObject;
import org.objectweb.proactive.core.remoteobject.RemoteObjectExposer;
import org.objectweb.proactive.core.remoteobject.RemoteObjectHelper;
import org.objectweb.proactive.gcmdeployment.GCMVirtualNode;

import functionalTests.GCMFunctionalTest;


public class TestGCMRemoteObjectsSubscribeFromRemoteObject extends GCMFunctionalTest {
    public TestGCMRemoteObjectsSubscribeFromRemoteObject() throws ProActiveException {
        super(1, 1);
        super.startDeployment();
    }

    @Test
    public void testRemote() throws InterruptedException, ProActiveException, URISyntaxException {

        GCMVirtualNode vn1 = super.gcmad.getVirtualNode(DEFAULT_VN_NAME);

        Node node = super.getANode();

        RemoteAO rao = PAActiveObject.newActive(RemoteAO.class, new Object[] {}, node);
        String url = rao.createRemoteObject();
        RemoteObject<RO> remoteObject = (RemoteObject<RO>) RemoteObjectHelper.lookup(new URI(url));
        RO ro = (RO) RemoteObjectHelper.generatedObjectStub(remoteObject);

        rao.setVirtualNode(vn1);
        ro.setVirtualNode(vn1);
        ro.setRemoteObject(ro);
        ro.setCallback();
        ro.waitSuccess();
    }

    static public class RO {
        private GCMVirtualNode vn;

        private RO _this;

        private Semaphore sem = new Semaphore(0);

        boolean success = false;

        public RO() {

        }

        public void setVirtualNode(GCMVirtualNode vn) {
            this.vn = vn;
        }

        public void setRemoteObject(RO _this) {
            this._this = _this;
        }

        public void setCallback() {
            try {
                vn.subscribeNodeAttachment(_this, "callback", true);
            } catch (Throwable e) {
                e.printStackTrace();
            }
            System.out.println("SUBSCRIBED");
        }

        public void callback(Node node, String vnName) {
            System.out.println("Callback occured !");
            sem.release();
        }

        public void waitSuccess() throws InterruptedException {
            System.out.println("Waiting for semaphore");
            sem.acquire();
            System.out.println("Semaphore acquired");
        }

    }

    static public class RemoteAO implements Serializable {
        GCMVirtualNode vn;

        public RemoteAO() {

        }

        public void setVirtualNode(GCMVirtualNode vn) {
            this.vn = vn;
        }

        public String createRemoteObject() throws ProActiveException {
            RO ro = new RO();
            RemoteObjectExposer<RO> roe = new RemoteObjectExposer<RO>(RO.class.getName(), ro);
            roe.createRemoteObject("remoteObject", false);
            return roe.getURL();

        }
    }
}
