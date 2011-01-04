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
package org.objectweb.proactive.extensions.processbuilder;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

import org.objectweb.proactive.extensions.processbuilder.exception.CoreBindingException;
import org.objectweb.proactive.extensions.processbuilder.exception.FatalProcessBuilderException;
import org.objectweb.proactive.extensions.processbuilder.exception.OSUserException;
import org.rzo.yajsw.os.ms.win.w32.WindowsProcess;


/**
 * Class that extends the {@link OSProcessBuilder} for machines running Windows.<br>
 * It relies on yajsw API (see http://yajsw.sourceforge.net) that exposes the Windows native
 * API calls to create process under a specific user.
 * <p>
 * This builder does not accept OSUser with a private key, only username and password
 * authentication is possible.
 * 
 * @since ProActive 4.4.0
 */
public final class WindowsProcessBuilder implements OSProcessBuilder {
    /**
     * Windows error codes.
     */
    private static final int ERROR_FILE_NOT_FOUND = 2;
    private static final int ERROR_LOGON_FAILURE = 1326;

    // the underlying ProcessBuilder to whom all work will be delegated
    // if no specified user
    protected final ProcessBuilder delegatedPB;

    // user - this should be a valid OS user entity (username and maybe a
    // password). The launched process will be run under this user's environment and rights.
    private final OSUser user;

    // descriptor of the core-binding (subset of cores on which the user's
    // process can execute)
    private final CoreBindingDescriptor cores;

    /**
     * Creates a new instance of this class.
     */
    protected WindowsProcessBuilder(final OSUser user, final CoreBindingDescriptor cores, final String paHome) {
        this.delegatedPB = new ProcessBuilder();
        this.user = user;
        this.cores = cores;
    }

    public boolean isCoreBindingSupported() {
        return false;
    }

    public Process start() throws IOException, OSUserException, CoreBindingException,
            FatalProcessBuilderException {
        Process p = null;

        if (user() != null || cores() != null) {
            // user or core binding is specified - do the fancy stuff
            p = setupAndStart();

        } else {
            // no extra service needed, just fall through to the delegated pb
            delegatedPB.environment().putAll(environment());
            p = delegatedPB.start();
        }

        return p;
    }

    public List<String> command() {
        return this.delegatedPB.command();
    }

    public OSProcessBuilder command(String... command) {
        this.delegatedPB.command(command);
        return this;
    }

    public OSUser user() {
        return this.user;
    }

    public CoreBindingDescriptor cores() {
        return this.cores;
    }

    public CoreBindingDescriptor getAvaliableCoresDescriptor() {
        return this.cores;
    }

    public File directory() {
        return this.delegatedPB.directory();
    }

    public OSProcessBuilder directory(File directory) {
        this.delegatedPB.directory(directory);
        return this;
    }

    public Map<String, String> environment() {
        return this.delegatedPB.environment();
    }

    public boolean redirectErrorStream() {
        return this.delegatedPB.redirectErrorStream();
    }

    public OSProcessBuilder redirectErrorStream(boolean redirectErrorStream) {
        this.delegatedPB.redirectErrorStream(redirectErrorStream);
        return this;
    }

    public boolean canExecuteAsUser(OSUser user) throws FatalProcessBuilderException {
        if (user.hasPrivateKey()) {
            // remove this when SSH support has been added to the product ;)
            throw new FatalProcessBuilderException("SSH support is not implemented!");
        }
        if (!user.hasPassword()) {
            return false;
        }
        return true;
    }

    /**
     * Create a native representation of a process that will run in background
     * that emans no interaction with the desktop 
     */
    protected Process setupAndStart() throws IOException, OSUserException, CoreBindingException,
            FatalProcessBuilderException {
        // Create the windows process from yajsw lib 
        final WindowsProcess p = new WindowsProcess();
        p.setUser(this.user().getUserName());
        p.setPassword(this.user().getPassword());

        // Inherit environment (Currently not work ... must be defined later)
        //p.setEnvironment(super.delegatedPB.environment());

        // This will force CREATE_NO_WINDOW | CREATE_UNICODE_ENVIRONMENT;
        p.setVisible(false);

        // Makes the stdin, stdout and stderr available
        p.setPipeStreams(true, false);

        // Inherit the working dir from the original process builder
        final File wdir = this.delegatedPB.directory();
        if (wdir != null) {
            p.setWorkingDir(wdir.getCanonicalPath());
        }

        // Inherit the command from the original process builder
        final StringBuilder commandBuilder = new StringBuilder();
        final List<String> command = this.delegatedPB.command();
        // Merge into a single string to get the length
        for (int i = 0; i < command.size(); i++) {
            commandBuilder.append(command.get(i));
            if (i + 1 < command.size()) {
                commandBuilder.append(' ');
            }
        }
        final String str = commandBuilder.toString();

        p.setCommand(str);
        if (!p.start()) {
            // Get the last error and depending on the error code
            // throw the correct exception
            final int err = WindowsProcess.getLastError();
            final String localizedMessage = WindowsProcess.formatMessageFromLastErrorCode(err);
            final String message = localizedMessage + " error=" + err;
            switch (err) {
                case ERROR_FILE_NOT_FOUND:
                    throw new IOException(message);
                case ERROR_LOGON_FAILURE:
                    throw new OSUserException(message);
                default:
                    throw new FatalProcessBuilderException(message);
            }
        }
        return new ProcessWrapper(p, command.get(0));
    }

    /**
     * Wraps a WindowsProcess and exposes it as a java.lang.Process 
     */
    private static final class ProcessWrapper extends Process {
        private final String name;
        private final WindowsProcess wp;

        public ProcessWrapper(final WindowsProcess wp, final String name) {
            this.wp = wp;
            this.name = name;
        }

        @Override
        public void destroy() {
            this.wp.destroy();
        }

        @Override
        public int exitValue() {
            int ec = this.wp.getExitCode();
            // -2 is returned by WindowsProcess if the process is still active
            // in order to mimic the behavior of java.lang.Process.exitValue()
            if (ec == -2) {
                throw new IllegalThreadStateException("Process " + this.name + " is not terminated");
            } else {
                return ec;
            }
        }

        @Override
        public InputStream getErrorStream() {
            return this.wp.getErrorStream();
        }

        @Override
        public InputStream getInputStream() {
            return this.wp.getInputStream();
        }

        @Override
        public OutputStream getOutputStream() {
            return this.wp.getOutputStream();
        }

        @Override
        public int waitFor() throws InterruptedException {
            return (this.wp.waitFor() ? 0 : -1);
        }
    }

    public static void main(String[] args) {
        WindowsProcessBuilder b = new WindowsProcessBuilder(new OSUser("tutu", "tutu"), null, null);
        String s = "C:\\Program Files\\Java\\jdk1.6.0_21\\\\jre\\bin\\java -Dproactive.scheduler.logs.maxsize=0 -Dproactive.configuration=file:C:\\vbodnart\\workspace12\\scheduling\\config\\scheduler\\forkedJavaTask\\forkedTask-paconf.xml -Djava.security.policy=file:C:\\vbodnart\\workspace12\\scheduling\\bin\\windows\\..\\../config/security.java.policy-client -Dlog4j.configuration=file:///C:\\vbodnart\\workspace12\\scheduling\\config\\scheduler\\forkedJavaTask\\forkedTask-log4j -D32 -cp .;.;C:\\vbodnart\\workspace12\\scheduling\\classes\\common;C:\\vbodnart\\workspace12\\scheduling\\classes\\resource-manager;C:\\vbodnart\\workspace12\\scheduling\\classes\\scheduler;;C:\\vbodnart\\workspace12\\scheduling\\lib\\ProActive\\ProActive.jar;C:\\vbodnart\\workspace12\\scheduling\\lib\\common\\c-java-mysql-enterprise-plugin-1.0.0.42.jar;C:\\vbodnart\\workspace12\\scheduling\\lib\\common\\commons-codec-1.3.jar;C:\\vbodnart\\workspace12\\scheduling\\lib\\common\\commons-collections-3.2.jar;C:\\vbodnart\\workspace12\\scheduling\\lib\\common\\derby.jar;C:\\vbodnart\\workspace12\\scheduling\\lib\\common\\derbytools.jar;C:\\vbodnart\\workspace12\\scheduling\\lib\\common\\isorelax.jar;C:\\vbodnart\\workspace12\\scheduling\\lib\\common\\msv.jar;C:\\vbodnart\\workspace12\\scheduling\\lib\\common\\mysql-connector-java-5.1.12-bin.jar;C:\\vbodnart\\workspace12\\scheduling\\lib\\common\\mysql-connector-java-5.1.7-bin.jar;C:\\vbodnart\\workspace12\\scheduling\\lib\\common\\relaxngDatatype.jar;C:\\vbodnart\\workspace12\\scheduling\\lib\\common\\rngpack-1.1a.jar;C:\\vbodnart\\workspace12\\scheduling\\lib\\common\\wstx-lgpl-3.9.2.jar;C:\\vbodnart\\workspace12\\scheduling\\lib\\common\\xsdlib.jar;C:\\vbodnart\\workspace12\\scheduling\\lib\\common\\script\\jruby-engine.jar;C:\\vbodnart\\workspace12\\scheduling\\lib\\common\\script\\jruby.jar;C:\\vbodnart\\workspace12\\scheduling\\lib\\common\\script\\js.jar;C:\\vbodnart\\workspace12\\scheduling\\lib\\common\\script\\jsch-0.1.38.jar;C:\\vbodnart\\workspace12\\scheduling\\lib\\common\\script\\jython-engine.jar;C:\\vbodnart\\workspace12\\scheduling\\lib\\common\\script\\jython.jar;C:\\vbodnart\\workspace12\\scheduling\\lib\\common\\script\\script-api.jar;C:\\vbodnart\\workspace12\\scheduling\\lib\\common\\script\\script-js.jar;C:\\vbodnart\\workspace12\\scheduling\\lib\\hibernate\\annotation\\ejb3-persistence.jar;C:\\vbodnart\\workspace12\\scheduling\\lib\\hibernate\\annotation\\hibernate-annotations.jar;C:\\vbodnart\\workspace12\\scheduling\\lib\\hibernate\\annotation\\hibernate-commons-annotations.jar;C:\\vbodnart\\workspace12\\scheduling\\lib\\hibernate\\core\\antlr-2.7.6.jar;C:\\vbodnart\\workspace12\\scheduling\\lib\\hibernate\\core\\dom4j-1.6.1.jar;C:\\vbodnart\\workspace12\\scheduling\\lib\\hibernate\\core\\geronimo-spec-jta-1.0.1B-rc4.jar;C:\\vbodnart\\workspace12\\scheduling\\lib\\hibernate\\core\\hibernate-core.jar;C:\\vbodnart\\workspace12\\scheduling\\lib\\hibernate\\core\\slf4j-api-1.5.6.jar;C:\\vbodnart\\workspace12\\scheduling\\lib\\hibernate\\core\\slf4j-log4j12-1.5.6.jar;C:\\vbodnart\\workspace12\\scheduling\\addons org.objectweb.proactive.core.runtime.StartPARuntime -p rmi://optimus.activeeon.com:1100/PA_JVM1151458586 -c 1 -d 679109";
        b.command(s);//"cmd.exe /c notepad.exe");
        try {
            Process p = b.start();
            Thread.sleep(35000);

            p.destroy();

            System.out.println("enclosing_type.enclosing_method() ----> exitValue " + p.exitValue());
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}