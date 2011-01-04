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
package functionalTests.configuration;

import java.util.List;
import java.util.Map;

import junit.framework.Assert;

import org.junit.Test;
import org.objectweb.proactive.core.config.CentralPAPropertyRepository;
import org.objectweb.proactive.core.config.PAProperties;
import org.objectweb.proactive.core.config.PAProperty;
import org.objectweb.proactive.core.config.PAPropertyBoolean;
import org.objectweb.proactive.core.config.PAPropertyInteger;
import org.objectweb.proactive.core.config.PAPropertyString;


public class TestUnsetProperty {

    @Test
    public void test() {

        PAPropertyString s = CentralPAPropertyRepository.PA_HOME;
        s.setValue("toto");
        s.unset();
        Assert.assertFalse(s.isSet());
        Assert.assertNull(s.getValue());

        PAPropertyInteger i = CentralPAPropertyRepository.PA_DGC_TTA;
        i.setValue(12);
        i.unset();
        Assert.assertFalse(i.isSet());
        Assert.assertNull(i.getValueAsString());

        PAPropertyBoolean b = CentralPAPropertyRepository.PA_DEBUG;
        b.setValue(true);
        b.unset();
        Assert.assertFalse(b.isSet());
        Assert.assertNull(b.getValueAsString());
    }

}