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
package org.objectweb.proactive.extensions.pnp;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.google.common.collect.Lists;


public class PNPTimeoutHandlerTest {

    private void recordHeartBeats(PNPTimeoutHandler handler, List<Integer> beats) {
        for (long beat : beats) {
            handler.recordHeartBeat(beat);
        }
    }

    @Test
    public void testHeartBeatReceived() {
        PNPTimeoutHandler handler = new PNPTimeoutHandler(10, 2, 2);

        recordHeartBeats(handler, Lists.newArrayList(1));
        Assert.assertTrue("heartbeat was received", handler.getNotificationData().heartBeatReceived());

        handler.resetNotification();
        Assert.assertFalse("handler was reset and heartbeat was not received",
                           handler.getNotificationData().heartBeatReceived());

        Assert.assertEquals("last interval should not be set",
                            0,
                            handler.getNotificationData().getLastHeartBeatInterval());
        Assert.assertEquals("Timeout should be the default", 10 * 2, handler.getNotificationData().getTimeout());

        recordHeartBeats(handler, Lists.newArrayList(1 + 12));
        Assert.assertTrue("heartbeat was received", handler.getNotificationData().heartBeatReceived());
        Assert.assertEquals("last interval should be set",
                            12,
                            handler.getNotificationData().getLastHeartBeatInterval());
        Assert.assertEquals("Timeout should be set accordingly", 12 * 2, handler.getNotificationData().getTimeout());
    }

    @Test
    public void testDefaultZero() {
        PNPTimeoutHandler handler = new PNPTimeoutHandler(0, 2, 2);

        recordHeartBeats(handler, Lists.newArrayList(1, 1 + 10, 1 + 10 + 12, 1 + 10 + 12 + 14, 1 + 10 + 12 + 14 + 16));
        Assert.assertEquals("If default heartbeat period is 0, the timeout should be zero", 0, handler.getTimeout());

    }

    @Test
    public void testNoData() {
        PNPTimeoutHandler handler = new PNPTimeoutHandler(10, 2, 2);

        recordHeartBeats(handler, Lists.newArrayList(1));
        Assert.assertEquals("If not enough data to compute an interval, default should be used",
                            10 * 2,
                            handler.getTimeout());

    }

    @Test
    public void testGrowing() {
        PNPTimeoutHandler handler = new PNPTimeoutHandler(10, 2, 2);

        recordHeartBeats(handler, Lists.newArrayList(1, 1 + 10, 1 + 10 + 12, 1 + 10 + 12 + 14, 1 + 10 + 12 + 14 + 16));
        Assert.assertEquals("After growing beat delay, timeout should grow to the last beat interval",
                            16 * 2,
                            handler.getTimeout());

    }

    @Test
    public void testGrowingDecreasing() {
        PNPTimeoutHandler handler = new PNPTimeoutHandler(10, 2, 2);

        recordHeartBeats(handler, Lists.newArrayList(1,
                                                     1 + 10,
                                                     1 + 10 + 12,
                                                     1 + 10 + 12 + 14,
                                                     1 + 10 + 12 + 14 + 16,
                                                     1 + 10 + 12 + 14 + 16 + 10,
                                                     1 + 10 + 12 + 14 + 16 + 10 + 10));
        Assert.assertEquals("After growing beat delay and then normal period, timeout should grow then shrink to the average",
                            10 * 2,
                            handler.getTimeout());

    }

    @Test
    public void testUpsAndDowns1() {
        PNPTimeoutHandler handler = new PNPTimeoutHandler(10, 2, 2);

        recordHeartBeats(handler, Lists.newArrayList(1, 1 + 10, 1 + 10 + 20, 1 + 10 + 20 + 10, 1 + 10 + 20 + 10 + 20));
        Assert.assertEquals("When beat interval is oscillating between two values, if last value is bigger than average, then it should be used",
                            20 * 2,
                            handler.getTimeout());

    }

    @Test
    public void testUpsAndDowns2() {
        PNPTimeoutHandler handler = new PNPTimeoutHandler(10, 2, 2);

        recordHeartBeats(handler, Lists.newArrayList(1,
                                                     1 + 10,
                                                     1 + 10 + 20,
                                                     1 + 10 + 20 + 10,
                                                     1 + 10 + 20 + 10 + 20,
                                                     1 + 10 + 20 + 10 + 20 + 10));
        Assert.assertEquals("When beat interval is oscillating between two values, if last value is lower than average, then the average should be used",
                            15 * 2,
                            handler.getTimeout());

    }
}
