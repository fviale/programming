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
package org.objectweb.proactive.extensions.dataspaces.vfs;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.commons.vfs2.CacheStrategy;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystem;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.impl.DefaultFileSystemManager;
import org.apache.log4j.Logger;
import org.objectweb.proactive.core.config.CentralPAPropertyRepository;
import org.objectweb.proactive.core.util.MutableInteger;
import org.objectweb.proactive.core.util.log.Loggers;
import org.objectweb.proactive.core.util.log.ProActiveLogger;
import org.objectweb.proactive.extensions.dataspaces.api.UserCredentials;
import org.objectweb.proactive.extensions.vfsprovider.client.ProActiveFileName;
import org.objectweb.proactive.utils.StackTraceUtil;
import org.objectweb.proactive.utils.ThreadPools;


/**
 * VFSMountManagerHelper
 *
 * This helper serves as an interface between the VFSMountManager and Apache VFS.
 * It adds the possibility to mount in parallel several spaces and return the first available one (mountAny)
 *
 * @author The ProActive Team
 **/
public class VFSMountManagerHelper {

    private static final Logger logger = ProActiveLogger.getLogger(Loggers.DATASPACES_MOUNT_MANAGER);

    static final HashMap<String, Future<FileObject>> alreadyMountedSpaces = new HashMap<String, Future<FileObject>>();

    static final int MAX_THREADS = 20;

    static ThreadPoolExecutor executor;

    // read write lock used to control the map
    static final ReentrantReadWriteLock rwlock = new ReentrantReadWriteLock(true);

    static final ReentrantReadWriteLock.ReadLock readLock = rwlock.readLock();

    static final ReentrantReadWriteLock.WriteLock writeLock = rwlock.writeLock();

    static final Map<UserCredentials, DefaultFileSystemManager> perUserManager = new HashMap<>();

    static final UserCredentials emptyUser = new UserCredentials();

    private static void initManager(UserCredentials credentials) throws FileSystemException {
        if (credentials == null) {
            credentials = emptyUser;
        }
        if (!perUserManager.containsKey(credentials)) {
            try {
                writeLock.lock();
                logger.debug("Initializing spaces mount manager");
                executor = ThreadPools.newBoundedThreadPool(MAX_THREADS);
                try {
                    // FIXME: depends on VFS-256 (fixed in VFS fork)
                    // in vanilla VFS version, this manager will always return FileObjects with broken
                    // delete(FileSelector) method. Anyway, it is rather better to do it this way, than returning
                    // shared FileObjects with broken concurrency
                    DefaultFileSystemManager vfsManager = VFSFactory.createDefaultFileSystemManager(credentials);
                    perUserManager.put(credentials, vfsManager);
                } catch (FileSystemException x) {
                    logger.error("Could not create and configure VFS manager", x);
                    throw x;
                }
                logger.debug("Mount manager initialized, VFS instance created");

                Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                    @Override
                    public void run() {
                        terminate();
                    }
                }));
            } finally {
                writeLock.unlock();
            }
        }
    }

    /**
     * Tries to mount the given virtual file system. The method will block until the file system is mounted or an exception occurred
     * @param uri vfs url
     * @return a successfully mounted file system
     * @throws FileSystemException if an error occurred during the mounting process
     */
    public static FileObject mount(UserCredentials credentials, String uri) throws FileSystemException {
        initManager(credentials);

        try {
            return executor.submit(new Mounter(uri, credentials, null)).get();
        } catch (InterruptedException e) {
            throw new FileSystemException("Interruption occurred when trying to mount " + uri, e);
        } catch (ExecutionException e) {
            throw new FileSystemException("Could not access URL to mount " + uri, e);
        }
    }

    /**
     * Tries to mount any of the given virtual file systems. The method will block until the first file system is mounted or
     * when all file system mounting processes threw exceptions.
     * File systems using the file protocol will always be mounted (even though they correspond to a folder which does not exist)
     *
     * @param uris a list of vfs urls
     * @return a successfully mounted file system
     * @throws FileSystemException if all mounting operations led to exceptions
     */
    public static void mountAny(UserCredentials credentials, List<String> uris,
            ConcurrentHashMap<String, FileObject> fileSystems) throws FileSystemException {

        initManager(credentials);

        ArrayList<String> urisfiltered = filterDuplicates(uris);

        if (fileSystems == null) {
            throw new IllegalArgumentException("file systems map must be initialized, received null");
        }

        if (urisfiltered.isEmpty()) {
            throw new IllegalArgumentException("Invalid file system uri list : received empty list");
        }

        ArrayList<String> fileUris = new ArrayList<String>();
        ArrayList<String> otherUris = new ArrayList<String>();

        filterFileUris(urisfiltered, fileUris, otherUris);

        StringBuilder exceptionMessage = new StringBuilder();

        String nl = System.getProperty("line.separator");

        // the mountAny uses the following strategy :
        // - if there are only file protocol uris, mount them all and return (they will always be mounted, unless invalid)
        // - if there are a mix between file protocol and other protocols, mount until at least one other protocol is mounted or all led to exceptions

        MutableInteger exceptionCount = new MutableInteger(0);

        ArrayList<Mounter> fileMounters = new ArrayList<Mounter>();
        ArrayList<Mounter> otherMounters = new ArrayList<Mounter>();

        for (String uri : fileUris) {
            fileMounters.add(new Mounter(uri, credentials, fileSystems));
        }

        for (String uri : otherUris) {
            otherMounters.add(new Mounter(uri, credentials, fileSystems));
        }

        boolean atLeastOneFileDeployed = false;

        if (!fileMounters.isEmpty()) {
            try {
                List<Future<FileObject>> fileMounterFutures = executor.invokeAll(fileMounters);
                for (Future<FileObject> future : fileMounterFutures) {
                    handleFuture(future, urisfiltered, exceptionMessage, exceptionCount, nl);
                }
                if (exceptionCount.getValue() < fileUris.size()) {
                    atLeastOneFileDeployed = true;
                }

            } catch (InterruptedException e) {
                throw new FileSystemException("Interruption occurred when trying to mount " + urisfiltered, e);
            }
        }

        if (!otherMounters.isEmpty()) {
            try {
                FileObject successful = executor.invokeAny(otherMounters);
                // at this stage there will be in the map alreadyMountedSpaces a mix of failures and at least one valid result
                scanMapForResults(otherUris, exceptionMessage, exceptionCount, nl);

            } catch (InterruptedException e) {
                throw new FileSystemException("Interruption occurred when trying to mount " + urisfiltered, e);
            } catch (ExecutionException e) {
                // no other protocol was deployed successfully
                scanMapForResults(otherUris, exceptionMessage, exceptionCount, nl);

                // if at least one file uri was deployed successfully, but no other protocols, display a warning
                if (exceptionCount.getValue() < urisfiltered.size() && atLeastOneFileDeployed) {
                    logger.warn("[VFSMountManager] Only file protocol file systems were accessible when trying to mount " +
                                urisfiltered + ". Here are all the exception received : " + nl + exceptionMessage);
                }
            }
        }

        // no other protocol was deployed successfully
        // if the total number of exceptions match the uri list size then it is a failure
        if (exceptionCount.getValue() == urisfiltered.size()) {
            throw new FileSystemException("Only Exceptions occurred when trying to mount " + urisfiltered + " : " + nl +
                                          nl + exceptionMessage.toString());
        }

    }

    public static ArrayList<String> filterDuplicates(List<String> uris) {
        LinkedHashSet<String> uriset = new LinkedHashSet<String>(uris);
        return new ArrayList<String>(uriset);
    }

    private static void filterFileUris(List<String> uris, ArrayList<String> fileUris, ArrayList<String> otherUris)
            throws FileSystemException {
        for (String uri : uris) {
            URI uriuri;
            try {
                uriuri = new URI(uri);
            } catch (URISyntaxException e) {
                throw new FileSystemException("URI syntax exception in " + uri, e);
            }
            if ("file".equals(uriuri.getScheme()) || uriuri.getScheme() == null) {
                fileUris.add(uri);
            } else {
                otherUris.add(uri);
            }
        }
    }

    private static void scanMapForResults(List<String> uris, StringBuilder exceptionMessage,
            MutableInteger exceptionCount, String nl) throws FileSystemException {
        for (String uri : uris) {
            Future<FileObject> future;
            try {
                readLock.lock();
                future = alreadyMountedSpaces.get(uri);
            } finally {
                readLock.unlock();
            }
            if (future != null) {
                handleFuture(future, uris, exceptionMessage, exceptionCount, nl);
            }
        }
    }

    private static void handleFuture(Future<FileObject> future, List<String> uris, StringBuilder exceptionMessage,
            MutableInteger exceptionCount, String nl) throws FileSystemException {
        if (future.isDone()) {
            try {
                FileObject answer = future.get();

            } catch (InterruptedException e) {
                throw new FileSystemException("Interruption occurred when trying to mount " + uris, e);
            } catch (ExecutionException e) {
                exceptionMessage.append(StackTraceUtil.getStackTrace(e) + nl + nl);
                exceptionCount.add(1);

            }
        }
    }

    /**
     * tries to close the FileSystems represented by the given urls
     *
     * @param uris file system uris
     */
    public static void closeFileSystems(UserCredentials credentials, Collection<String> uris) {
        if (credentials == null) {
            credentials = emptyUser;
        }
        try {
            writeLock.lock();
            if (perUserManager.containsKey(credentials)) {
                logger.debug("Closing file systems : " + uris);
                for (String uri : uris) {
                    if (alreadyMountedSpaces.containsKey(uri)) {
                        Future<FileObject> future = alreadyMountedSpaces.remove(uri);
                        if (future.isDone()) {

                            try {
                                FileObject fo = future.get();
                                final FileSystem spaceFileSystem = fo.getFileSystem();

                                // we may not need to close FileObject, but with VFS you never know...
                                try {
                                    fo.close();
                                } catch (org.apache.commons.vfs2.FileSystemException x) {
                                    logger.debug("Could not close data space root file object : " + fo, x);
                                    ProActiveLogger.logEatedException(logger,
                                                                      String.format("Could not close data space %s root file object",
                                                                                    fo),
                                                                      x);
                                }

                                perUserManager.get(credentials).closeFileSystem(spaceFileSystem);

                                if (logger.isDebugEnabled())
                                    logger.debug("Unmounted space: " + fo);
                            } catch (InterruptedException e) {
                                // ignore
                            } catch (ExecutionException e) {
                                // ignore
                            }
                        } else {
                            future.cancel(true);
                        }
                    }
                }
                perUserManager.remove(credentials);
            }
        } finally {
            writeLock.unlock();
        }
    }

    private static boolean isProActiveBased(String uri) {
        return uri.startsWith(ProActiveFileName.VFS_PREFIX);
    }

    public static void terminate() {
        try {

            writeLock.lock();
            if (!perUserManager.isEmpty()) {
                logger.debug("Terminating spaces mount manager");

                executor.shutdownNow();

                for (DefaultFileSystemManager vfsManager : perUserManager.values()) {
                    if (vfsManager != null) {
                        vfsManager.close();
                    }
                }
                perUserManager.clear();

            }
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Callable used to mount file systems
     */
    private static class Mounter implements Callable<FileObject> {

        private String uriToMount;

        private Map<String, FileObject> fileSystems;

        private UserCredentials credentials;

        public Mounter(String uriToMount, UserCredentials credentials, Map<String, FileObject> fileSystems) {
            this.uriToMount = uriToMount;
            this.fileSystems = fileSystems;
            if (credentials == null) {
                credentials = emptyUser;
            }
            this.credentials = credentials;
        }

        @Override
        public FileObject call() throws Exception {

            // check for already mounted space
            Future<FileObject> future;
            try {
                readLock.lock();
                future = alreadyMountedSpaces.get(uriToMount);
            } finally {
                readLock.unlock();
            }

            if (future != null) {
                // reuse already mounted space or failure
                FileObject fo = future.get(); // will throw an exception if there was a failure
                if (fileSystems != null) {
                    fileSystems.put(uriToMount, fo);
                }
                return fo;
            } else {
                // start new mounting
                logger.debug("[" + VFSMountManagerHelper.class.getSimpleName() + "] Mounting " + uriToMount);
                FileObject mounted = null;
                try {
                    mounted = perUserManager.get(credentials).resolveFile(uriToMount);
                } catch (Exception e) {
                    // failure in mounting

                    // put the future result in the map
                    try {
                        writeLock.lock();
                        alreadyMountedSpaces.put(uriToMount, new FutureAnswer(null, e));
                    } finally {
                        writeLock.unlock();
                    }
                    // and throw the exception to indicate failure to the executor
                    throw e;
                }

                // mounting successful
                logger.debug("[" + VFSMountManagerHelper.class.getSimpleName() + "] " + uriToMount + " mounted.");

                if (fileSystems != null) {
                    fileSystems.put(uriToMount, mounted);
                }
                try {
                    writeLock.lock();
                    alreadyMountedSpaces.put(uriToMount, new FutureAnswer(mounted, null));
                } finally {
                    writeLock.unlock();
                }
                return mounted;
            }
        }
    }

    private static class FutureAnswer implements Future<FileObject> {

        FileObject answer;

        Throwable exception;

        public FutureAnswer(FileObject answer, Throwable exception) {
            this.answer = answer;
            this.exception = exception;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isDone() {
            return true;
        }

        @Override
        public FileObject get() throws InterruptedException, ExecutionException {
            if (exception != null) {
                throw new ExecutionException(exception);
            }
            return answer;
        }

        @Override
        public FileObject get(long timeout, TimeUnit unit)
                throws InterruptedException, ExecutionException, TimeoutException {
            throw new UnsupportedOperationException();
        }
    }

}
