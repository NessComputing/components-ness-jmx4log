/**
 * Copyright (C) 2012 Ness Computing, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.nesscomputing.log.jmx;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;

import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanServer;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Category;
import org.apache.log4j.LogManager;
import org.weakref.jmx.MBeanExporter;
import org.weakref.jmx.Managed;

import com.nesscomputing.logging.Log;

/**
 * Control the Log4j Loggers using JMX.
 */
public class Log4JMBean
{
	/** Default MBean prefix. */
    public static final String DEFAULT_OBJECT_NAME = "io.trumpet.log:name=Logger";

    /**
     * Number of base package levels that is shown as a single node in the tree at the
     * root level. Should be at least 2, because most packages start with "io.trumpet" or "org.apache" or "com.foo".
     */
    public static final int PRESERVE_PACKAGES = 2;

	private static final Log LOG = Log.findLog();

    private final Map<String, Log4JLevelMBean> levelMBeans = new ConcurrentSkipListMap<String, Log4JLevelMBean>();
    private final Map<Object, String> exportedObjects = new HashMap<Object, String>();

    // The generation determines "when" a given mbean was added. For every run of the LoggerThread, it pulls the list
    // of loggers out of log4j and updates the generation on the existing mbeans. So if a logger exists and there should
    // be an MBean, the MBean will have the current generation count.
    //
    // Once that is done, it will loop over the MBeans and check the generation count. If it is the same as the current
    // generation, then the MBean is valid. If it is smaller, then the MBean represents a logger that is no longer there
    // and it will henceforth removed.
    //
    private final AtomicLong generation = new AtomicLong(0L);

    private final MBeanExporter exporter;
    private final String jmxRoot;

    private LoggerThread loggerThread = null;

    public Log4JMBean(final MBeanServer mbeanServer, final String jmxRoot)
    {
        this.exporter = new MBeanExporter(mbeanServer);
    	this.jmxRoot = jmxRoot;
    }

    public synchronized void start()
    {
    	exportMBean(jmxRoot, this);

    	// The root MBean always exists, so it does not show up in the levelMBeans map.
        Log4JLevelMBean rootLevelMBean = new Log4JLevelMBean(LogManager.getRootLogger(), 0L);
        exportMBean(buildJMXName("_ROOT_"), rootLevelMBean);


    	if (loggerThread == null) {
    		loggerThread = new LoggerThread();
    		loggerThread.start();
    	}
    	else {
    		LOG.warn("Ignoring multiple start attempts!");
    	}
    }

    public synchronized void stop()
    {
        if (loggerThread != null) {
            loggerThread.terminate();
            loggerThread = null;
            levelMBeans.clear();
            unexportMBeans();
        }
    }

    @Managed
	public String [] getLoggerNames()
	{
        final Set<String> mbeans = levelMBeans.keySet();
		return mbeans.toArray(new String[mbeans.size()]);
    }

    private void exportMBean(final String name, final Object bean)
    {
    	synchronized(exportedObjects) {
    	    try {
    	        exporter.export(name, bean);
    	        exportedObjects.put(bean, name);
    	    } catch (RuntimeException re) {
    	        if (re.getCause() instanceof InstanceAlreadyExistsException) {
    	            LOG.warn("Could not export '%s', already exists!", name);
    	        }
    	        else {
    	            throw re;
    	        }
    	    }
    	}
    }

    private void unexportMBeans()
    {
    	synchronized(exportedObjects) {
    		for (String jmxName : exportedObjects.values()) {
                try {
    		        exporter.unexport(jmxName);
                } catch (RuntimeException re) {
                    LOG.warn("Could not unexport '%s'!", jmxName);
                }
    		}
    		exportedObjects.clear();
    	}
    }

    private void unexportMBean(final Object bean)
    {
    	synchronized(exportedObjects) {
    		final String key = exportedObjects.remove(bean);

    		if (key != null) {
                try {
                    exporter.unexport(key);
                } catch (RuntimeException re) {
                    LOG.warn("Could not unexport '%s'!", key);
                }
    		}
    	}
    }

    private Log4JLevelMBean locateLevelMBean(final Category logger)
    {
        final long currentGeneration = generation.get();

        final String loggerName = logger.getName();

        Log4JLevelMBean levelMBean = levelMBeans.get(loggerName);

        // Does a MBean for this level exist? If no, create a new one.
        if (levelMBean == null) {
            LOG.trace("No MBean for '%s', creating...", loggerName);
            levelMBean = new Log4JLevelMBean(logger, currentGeneration);
            exportMBean(buildJMXName(loggerName), levelMBean);

            levelMBeans.put(loggerName, levelMBean);
        }
        else {
            // Update the mbean to be valid in this generation
            levelMBean.setGeneration(currentGeneration);
        }
        return levelMBean;
    }

    private String buildJMXName(final String loggerName)
    {
    	String [] pieces = StringUtils.split(loggerName, ".");

    	StringBuilder jmxName = new StringBuilder(jmxRoot);
    	jmxName.append(",logger=");

    	if (pieces.length < PRESERVE_PACKAGES) {
    		jmxName.append(loggerName);
    	}
    	else {
    		jmxName.append(StringUtils.join(pieces, ".", 0, PRESERVE_PACKAGES));
    		for (int i = PRESERVE_PACKAGES; i < pieces.length; i++) {
    			jmxName.append(",logger");
    			jmxName.append(i);
    			jmxName.append("=");
    			jmxName.append(pieces[i]);
    		}
    	}
    	return jmxName.toString();
    }

    /**
     * Loggers can occasionally appear and/or disappear. Go through the list of loggers
     * in log4j and that we have registered to update the JMX view.
     */
    private class LoggerThread extends Thread
    {
        private volatile boolean running = true;

        private LoggerThread()
        {
            super("jmx-log4j");
            setDaemon(true);
            updateLevelMBeans(generation.get());
        }

        private void terminate()
        {
            running = false;
            this.interrupt();
            try {
            	this.join();
            }
            catch (InterruptedException ioe) {
            	Thread.currentThread().interrupt();
            }
        }

        @Override
        public void run()
        {
            while(running) {
                try {
                	// tick every five seconds.
                    Thread.sleep(5000L);
                }
                catch (InterruptedException ioe) {
                    // If the thread was interrupted, someone wants us to
                    // call it quits. So end it.
                    Thread.currentThread().interrupt();
                    running = false;
                    break;
                }
                long currentGeneration = generation.incrementAndGet();
                updateLevelMBeans(currentGeneration);
            }
        }

        @SuppressWarnings("unchecked")
		private void updateLevelMBeans(final long currentGeneration)
        {
            final Enumeration<Category> e = LogManager.getCurrentLoggers();

            while (e.hasMoreElements()) {
                final Category logger = e.nextElement();
                locateLevelMBean(logger); // Does all the magic for non-existing loggers.
            }

            // See which MBeans were not hit by the update above (then they don't have the
            // right generation count).
            for (Iterator<Log4JLevelMBean> it = levelMBeans.values().iterator(); it.hasNext(); ) {
                final Log4JLevelMBean levelMBean = it.next();

                if (levelMBean.getGeneration() < currentGeneration) {
                    LOG.trace("MBean for '%s' no longer active, removing", levelMBean.getName());
                    it.remove();
                    unexportMBean(levelMBean);
                }
            }
        }
    }
}
