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
package org.objectweb.proactive.core.body.proxy;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.objectweb.proactive.Body;
import org.objectweb.proactive.core.Constants;
import org.objectweb.proactive.core.ProActiveRuntimeException;
import org.objectweb.proactive.core.body.LocalBodyStore;
import org.objectweb.proactive.core.body.exceptions.FutureCreationException;
import org.objectweb.proactive.core.body.exceptions.SendRequestCommunicationException;
import org.objectweb.proactive.core.body.future.Future;
import org.objectweb.proactive.core.body.future.FutureProxy;
import org.objectweb.proactive.core.config.CentralPAPropertyRepository;
import org.objectweb.proactive.core.mop.MOP;
import org.objectweb.proactive.core.mop.MOPException;
import org.objectweb.proactive.core.mop.MethodCall;
import org.objectweb.proactive.core.mop.MethodCallExecutionFailedException;
import org.objectweb.proactive.core.mop.MethodCallInfo;
import org.objectweb.proactive.core.mop.Proxy;
import org.objectweb.proactive.core.mop.StubObject;
import org.objectweb.proactive.core.util.log.Loggers;
import org.objectweb.proactive.core.util.log.ProActiveLogger;


public abstract class AbstractBodyProxy extends AbstractProxy implements BodyProxy, java.io.Serializable {
    //
    // -- STATIC MEMBERS -----------------------------------------------
    //
    private static Logger syncCallLogger = ProActiveLogger.getLogger(Loggers.SYNC_CALL);

    private static String UNKNOWN = "[unknown]";

    private static Logger logger = ProActiveLogger.getLogger(Loggers.PAPROXY);

    private static boolean enableStack = CentralPAPropertyRepository.PA_STACKTRACE.getValue();

    //
    // -- PROTECTED MEMBERS -----------------------------------------------
    //
    protected Integer cachedHashCode = null;

    protected String sourceBodyUrl = UNKNOWN;

    protected String targetBodyUrl = UNKNOWN;

    //
    // -- CONSTRUCTORS -----------------------------------------------
    //
    public AbstractBodyProxy() {
    }

    //
    // -- PUBLIC METHODS -----------------------------------------------
    //

    //
    // -- implements Proxy -----------------------------------------------
    //

    /**
     * Performs operations on the Call object created by the stub, thus changing the semantics of
     * message-passing to asynchronous message-passing with future objects
     * 
     * 
     * The semantics of message-passing implemented by this proxy class may be definied as follows :
     * <UL>
     * <LI>Asynchronous message-passing
     * <LI>Creation of future objects where possible (which leads to wait-by-necessity).
     * <LI>Synchronous, blocking calls where futures are not available.
     * <LI>The Call <code>methodCall</code> is passed to the skeleton for execution.
     * </UL>
     */
    public Object reify(MethodCall methodCall) throws Throwable {
        Object cachedMethodResult = checkOptimizedMethod(methodCall);
        if (cachedMethodResult != null) {
            return cachedMethodResult;
        }

        return invokeOnBody(methodCall);
    }

    /*
     * HACK: toString() can be implicitly called by log4j, which may result in a deadlock if we call
     * log4j inside log4j, so for now, we disable the message for toString().
     */
    private static boolean isToString(MethodCall methodCall) {
        return (methodCall.getNumberOfParameter() == 0) && "toString".equals(methodCall.getName());
    }

    private static boolean isHashCode(MethodCall methodCall) {
        return (methodCall.getNumberOfParameter() == 0) && "hashCode".equals(methodCall.getName());
    }

    private static Set<String> loggedSyncCalls = Collections.synchronizedSet(new HashSet<String>());

    private Object invokeOnBody(MethodCall methodCall) throws Exception, Throwable {
        initUrls();
        // Now gives the MethodCall object to the body

        MethodCallInfo mci = methodCall.getMethodCallInfo();

        try {

            if (mci.getType() == MethodCallInfo.CallType.OneWay) {
                reifyAsOneWay(methodCall);
                return null;
            }

            if (mci.getType() == MethodCallInfo.CallType.Asynchronous) {
                return reifyAsAsynchronous(methodCall);
            }

            if (!isToString(methodCall) && !isHashCode(methodCall) && syncCallLogger.isEnabledFor(Level.DEBUG)) {
                String msg = "[Proxy DEBUG: synchronous call] All calls to the method below are synchronous " +
                             "(not an error, but may lead to performance issues or deadlocks):" +
                             System.getProperty("line.separator") + methodCall.getReifiedMethod() +
                             System.getProperty("line.separator") + "They are synchronous for the following reason: " +
                             mci.getMessage();

                if (loggedSyncCalls.add(msg)) {
                    syncCallLogger.debug(msg);
                }
            }

            return reifyAsSynchronous(methodCall);
        } catch (MethodCallExecutionFailedException e) {
            throw new ProActiveRuntimeException(e.getMessage(), e.getTargetException());
        }
    }

    private void initUrls() {

        if (sourceBodyUrl.equals(UNKNOWN)) {
            try {
                sourceBodyUrl = urls2String(LocalBodyStore.getInstance().getContext().getBody().getUrls());
            } catch (Exception e) {
                logger.debug("[Proxy] Could not retrieve urls of the source active object (" + e.getMessage() + ")");
            }
        }

        if (targetBodyUrl.equals(UNKNOWN)) {
            try {
                targetBodyUrl = urls2String(this.getBody().getUrls());
            } catch (Exception e) {
                logger.debug("[Proxy] Could not retrieve urls of the target active object (" + e.getMessage() + ")");
            }
        }

    }

    private String urls2String(String[] urls) {
        if (urls.length == 1) {
            return urls[0];
        } else {
            return Arrays.asList(urls).toString();
        }
    }

    // optimization may be a local execution or a caching mechanism
    // returns null if not applicable
    private Object checkOptimizedMethod(MethodCall methodCall) throws Exception, Throwable {
        if (methodCall.getName().equals("equals") && (methodCall.getNumberOfParameter() == 1)) {
            Object arg = methodCall.getParameter(0);
            if (MOP.isReifiedObject(arg)) {
                Proxy proxy = ((StubObject) arg).getProxy();
                if (proxy instanceof AbstractBodyProxy) {
                    return Boolean.valueOf(getBodyID().equals(((AbstractBodyProxy) proxy).getBodyID()));
                }
            }

            return Boolean.valueOf(false);
        }

        if (methodCall.getName().equals("hashCode") && (methodCall.getNumberOfParameter() == 0)) {
            if (cachedHashCode == null) {
                return cachedHashCode = (Integer) invokeOnBody(methodCall);
            } else {
                return cachedHashCode;
            }
        }

        return null;
    }

    /**
     * 
     */
    protected void reifyAsOneWay(MethodCall methodCall) throws Exception {

        logger.debug("[Proxy] reify " + methodCall.getName() + "() as one-way from " + sourceBodyUrl + " to " +
                     targetBodyUrl);

        sendRequest(methodCall, null);
    }

    /*
     * Dummy Future used to reply to a one-way method call with exceptions Declared as public to
     * accomodate the MOP
     */
    public static class VoidFuture {
        public VoidFuture() {
        }
    }

    protected Object reifyAsAsynchronous(MethodCall methodCall) throws Exception {
        StubObject futureobject = null;

        // Creates a stub + FutureProxy for representing the result

        logger.debug("[Proxy] reify " + methodCall.getName() + "() as asynchronous from " + sourceBodyUrl + " to " +
                     targetBodyUrl);

        try {

            Class<?> returnType = null;
            Type t = methodCall.getReifiedMethod().getGenericReturnType();
            if (t instanceof TypeVariable) {
                returnType = methodCall.getGenericTypesMapping().get(t);
            } else {
                returnType = methodCall.getReifiedMethod().getReturnType();
            }

            if (returnType.equals(java.lang.Void.TYPE)) {
                /* A future for a void call is used to put the potential exception inside */
                futureobject = (StubObject) MOP.newInstance(VoidFuture.class,
                                                            null,
                                                            Constants.DEFAULT_FUTURE_PROXY_CLASS_NAME,
                                                            null);
            } else {
                futureobject = (StubObject) MOP.newInstance(returnType,
                                                            null,
                                                            Constants.DEFAULT_FUTURE_PROXY_CLASS_NAME,
                                                            null);
            }
        } catch (MOPException e) {
            throw new FutureCreationException("[Proxy] Exception occurred in reifyAsAsynchronous from " +
                                              sourceBodyUrl + " to " + targetBodyUrl +
                                              " while creating future for methodcall = " + methodCall.getName(), e);
        } catch (ClassNotFoundException e) {
            throw new FutureCreationException("[Proxy] Exception occurred in reifyAsAsynchronous from " +
                                              sourceBodyUrl + " to " + targetBodyUrl +
                                              " while creating future for methodcall = " + methodCall.getName(), e);
        }

        // Set the id of the body creator in the created future
        FutureProxy fp = (FutureProxy) (futureobject.getProxy());
        fp.setCreatorID(this.getBodyID());
        fp.setCreator(this.getBody());
        fp.setUpdater(this.getBody());
        fp.setOriginatingProxy(this);
        Method m = methodCall.getReifiedMethod();
        fp.setCreatorStackTraceElement(new StackTraceElement(m.getDeclaringClass().getName(),
                                                             m.getName(),
                                                             targetBodyUrl,
                                                             -1));

        try {
            if (enableStack) {
                fp.setCallerContext(new Exception().getStackTrace());
            }
            sendRequest(methodCall, fp);
        } catch (java.io.IOException e) {
            throw new SendRequestCommunicationException("[Proxy] Exception occurred in reifyAsAsynchronous while sending request for methodcall = " +
                                                        methodCall.getName() + " from " + sourceBodyUrl + " to " +
                                                        targetBodyUrl, e);
        }

        // And return the future object
        return futureobject;
    }

    protected Object reifyAsSynchronous(MethodCall methodCall) throws Throwable, Exception {
        // Setting methodCall.res to null means that we do not use the future mechanism
        FutureProxy fp = FutureProxy.getFutureProxy();
        fp.setCreatorID(this.getBodyID());
        fp.setCreator(this.getBody());
        fp.setUpdater(this.getBody());
        Method m = methodCall.getReifiedMethod();
        fp.setCreatorStackTraceElement(new StackTraceElement(m.getDeclaringClass().getName(),
                                                             m.getName(),
                                                             targetBodyUrl,
                                                             -1));

        logger.debug("[Proxy] reify " + methodCall.getName() + "() as synchronous from " + sourceBodyUrl + " to " +
                     targetBodyUrl);

        try {
            sendRequest(methodCall, fp);
        } catch (java.io.IOException e) {
            throw new SendRequestCommunicationException("[Proxy] Exception occurred in reifyAsSynchronous while sending request for methodcall = " +
                                                        methodCall.getName() + " from " + sourceBodyUrl + " to " +
                                                        targetBodyUrl, e);
        }

        // PROACTIVE_1180
        // explicit waitfor with PA_FUTURE_TIMEOUT to leave a chance
        // to have synchronous method calls
        // useful when the call can block for ever and is made by a threadpool.
        // 
        fp.waitFor(CentralPAPropertyRepository.PA_FUTURE_SYNCHREQUEST_TIMEOUT.getValue());

        // Returns the result or throws the exception
        if (fp.getRaisedException() != null) {
            // if there is an exception to be thrown, we add the local context to this exception
            ArrayList<StackTraceElement> totalContext = new ArrayList<StackTraceElement>();
            totalContext.addAll(Arrays.asList(new Exception().getStackTrace()));
            FutureProxy.updateStackTraceContext(totalContext, fp, false);
            fp.updateContext();
            throw fp.getRaisedException();
        }
        return fp.getResult();
    }

    protected abstract void sendRequest(MethodCall methodCall, Future future) throws java.io.IOException;

    protected abstract void sendRequest(MethodCall methodCall, Future future, Body sourceBody)
            throws java.io.IOException;
}
