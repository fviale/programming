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
package org.objectweb.proactive.core.group.topology;

import org.objectweb.proactive.core.group.Group;
import org.objectweb.proactive.core.group.ProxyForGroup;
import org.objectweb.proactive.core.mop.ConstructionOfReifiedObjectFailedException;


/**
 * This class represents a group by a two-dimensional topology.
 *
 * @author The ProActive Team
 */
public class Plan<E> extends Line<E> { // implements Topology2D {

    /** height of the two-dimensional topology group */
    protected int height; //  => Y => number of lines

    /**
     * Construtor. The members of <code>g</code> are used to fill the topology group.
     * @param g - the group used a base for the new group (topology)
     * @param height - the heigth of the two-dimensional topology group
     * @param width - the width of the two-dimensional topology group
     * @throws ConstructionOfReifiedObjectFailedException
     */
    public Plan(Group<E> g, int height, int width) throws ConstructionOfReifiedObjectFailedException {
        super(g, height * width);
        this.height = height;
        this.width = width;
    }

    /**
     * Construtor. The members of <code>g</code> are used to fill the topology group.
     * @param g - the group used a base for the new group (topology)
     * @param nbMembers - the number of member in the two-dimensional topology group
     * @throws ConstructionOfReifiedObjectFailedException
     */
    protected Plan(Group<E> g, int nbMembers) throws ConstructionOfReifiedObjectFailedException {
        super(g, nbMembers);
    }

    /**
     * Return the size of the lines
     * @return the size of the the lines
     */
    @Override
    public int getWidth() {
        return this.width;
    }

    /**
     * Returns the height of the two-dimensional topology group (number of lines)
     * @return the height of the two-dimensional topology group
     */
    public int getHeight() {
        return this.height;
    }

    /**
     * Returns the coordonate X for the specified position, according to the dimension
     * @param position
     * @return the coordonate X
     */
    private int getX(int position) {
        return position % this.width;
    }

    /**
     * Returns the coordonate Y for the specified position, according to the dimension
     * @param position
     * @return the coordonate Y
     */
    private int getY(int position) {
        return position % this.height;
    }

    /**
     * Returns the object at the left of the specified object in the two-dimensional topology group
     * @param o - the specified object
     * @return the object at the left of <code>o<code>. If there is no object at the left of <code>o</code>, return <code>null</code>
     */
    @Override
    public Object left(Object o) {
        int pos = this.indexOf(o);
        if ((pos % this.getWidth()) == 0) {
            return null;
        } else {
            return this.get(pos - 1);
        }
    }

    /**
     * Returns the X-coordonate of the specified object
     * @param o - the object
     * @return the position (in X) of the object in the Plan
     */
    @Override
    public int getX(Object o) {
        return this.indexOf(o) % this.getWidth();
    }

    /**
     * Returns the Y-coordonate of the specified object
     * @param o - the object
     * @return the position (in Y) of the object in the Plan
     */
    public int getY(Object o) {
        return this.indexOf(o) / this.getWidth();
    }

    /**
     * Returns the object at the right of the specified object in the two-dimensional topology group
     * @param o - the specified object
     * @return the object at the right of <code>o<code>. If there is no object at the right of <code>o</code>, return <code>null</code>
     */
    @Override
    public Object right(Object o) {
        int pos = this.indexOf(o);
        if ((pos % this.getWidth()) == (this.getWidth() - 1)) {
            return null;
        } else {
            return this.get(pos + 1);
        }
    }

    /**
     * Returns the object at the up of the specified object in the two-dimensional topology group
     * @param o - the specified object
     * @return the object at the up of <code>o<code>. If there is no object at the up of <code>o</code>, return <code>null</code>
     */
    public Object up(Object o) {
        int pos = this.indexOf(o);
        if (pos < this.getWidth()) {
            return null;
        } else {
            return this.get(pos - this.getWidth());
        }
    }

    /**
     * Returns the object at the down of the specified object in the two-dimensional topology group
     * @param o - the specified object
     * @return the object at the down of <code>o<code>. If there is no object at the down of <code>o</code>, return <code>null</code>
     */
    public Object down(Object o) {
        int pos = this.indexOf(o);
        if (pos > (((this.getHeight() - 1) * this.getWidth()) - 1)) {
            return null;
        } else {
            return this.get(pos + this.getWidth());
        }
    }

    /**
     * Returns the line (one-dimensional topology group) with the specified number
     * @param line - the number of the line
     * @return the one-dimensional topology group formed by the line in the two-dimensional topology group, return <code>null</code> if the the specified line is incorrect
     */
    public Line<E> line(int line) {
        if ((line < 0) || (line > this.getWidth())) {
            return null;
        }
        ProxyForGroup<E> tmp = null;
        try {
            tmp = new ProxyForGroup<E>(this.getTypeName());
        } catch (ConstructionOfReifiedObjectFailedException e) {
            logger.error("", e);
        }
        int begining = line * this.getWidth();
        for (int i = begining; i < (begining + this.getWidth()); i++) {
            tmp.add(this.get(i));
        }
        Line<E> result = null;
        try {
            result = new Line<E>(tmp, this.getWidth());
        } catch (ConstructionOfReifiedObjectFailedException e) {
            logger.error("", e);
        }
        return result;
    }

    /**
     * Returns the line that contains the specified object
     * @param o - the object
     * @return the one-dimensional topology group formed by the line of the object in the two-dimensional topology group
     */
    public Line<E> line(Object o) {
        return this.line(this.getY(this.indexOf(o)));
    }

    /**
     * Returns the column (one-dimensional topology group) with the specified number
     * @param column - the number of the line
     * @return the one-dimensional topology group formed by the column in the two-dimensional topology group, return <code>null</code> if the the specified line is incorrect
     */
    public Line<E> column(int column) {
        if ((column < 0) || (column > this.getHeight())) {
            return null;
        }
        ProxyForGroup<E> tmp = null;
        try {
            tmp = new ProxyForGroup<E>(this.getTypeName());
        } catch (ConstructionOfReifiedObjectFailedException e) {
            logger.error("", e);
        }
        for (int i = 0; i < this.getHeight(); i++) {
            tmp.add(this.get(column + (i * this.getWidth())));
        }
        Line<E> result = null;
        try {
            result = new Line<E>(tmp, this.getHeight());
        } catch (ConstructionOfReifiedObjectFailedException e) {
            logger.error("", e);
        }
        return result;
    }

    /**
     * Returns the column that contains the specified object
     * @param o - the object
     * @return the one-dimensional topology group formed by the column of the object in the two-dimensional topology group
     */
    public Line<E> column(Object o) {
        return this.column(this.getX(this.indexOf(o)));
    }
}
