/*
 * ################################################################
 *
 * ProActive Parallel Suite(TM): The Java(TM) library for
 *    Parallel, Distributed, Multi-Core Computing for
 *    Enterprise Grids & Clouds
 *
 * Copyright (C) 1997-2011 INRIA/University of
 *                 Nice-Sophia Antipolis/ActiveEon
 * Contact: proactive@ow2.org or contact@activeeon.com
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; version 3 of
 * the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307
 * USA
 *
 * If needed, contact us to obtain a release under GPL Version 2 or 3
 * or a different license than the AGPL.
 *
 *  Initial developer(s):               The ProActive Team
 *                        http://proactive.inria.fr/team_members.htm
 *  Contributor(s):
 *
 * ################################################################
 * $$PROACTIVE_INITIAL_DEV$$
 */
package org.objectweb.proactive.examples.jmx.remote.management.client.entities;

import java.io.Serializable;

import javax.management.ObjectName;

import org.objectweb.proactive.core.jmx.ProActiveConnection;


public class RemoteCommand extends ManageableEntity implements Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = 500L;
    /**
     *
     */
    private RemoteTransaction parent;
    private String name;

    public RemoteCommand(RemoteTransaction parent, String name) {
        this.parent = parent;
        this.name = name;
    }

    @Override
    public void addEntity(ManageableEntity entity) {
        // TODO Auto-generated method stub
    }

    @Override
    public Object[] getChildren() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public ManageableEntity getParent() {
        return this.parent;
    }

    @Override
    public boolean hasChildren() {
        return false;
    }

    @Override
    public void remove() {
        this.parent.removeEntity(this);
    }

    @Override
    public void removeEntity(ManageableEntity entity) {
        // TODO Auto-generated method stub
    }

    @Override
    public ProActiveConnection getConnection() {
        return this.parent.getConnection();
    }

    @Override
    public ObjectName getObjectName() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getUrl() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String toString() {
        return this.name;
    }
}
