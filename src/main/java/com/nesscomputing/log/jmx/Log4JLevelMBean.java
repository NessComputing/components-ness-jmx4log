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

import org.apache.log4j.Category;
import org.apache.log4j.Level;
import org.weakref.jmx.Managed;

/**
 * Represents a single log4j Logger/Category in JMX.
 */
public class Log4JLevelMBean
{
    private final Category logger;

    private volatile long generation = 0L;

    public Log4JLevelMBean(final Category logger, final long generation)
    {
        this.logger = logger;
        this.generation = generation;
    }


    void setGeneration(final long generation)
    {
    	this.generation = generation;
    }

    long getGeneration()
    {
    	return generation;
    }

    @Managed
    public String getName()
    {
        return logger.getName();
    }

    @Managed
	public String getEffectiveLevel()
	{
		final Level level = logger.getEffectiveLevel();
		return (level != null) ? level.toString() : "<unset>";
	}

    @Managed
	public String getLevel()
	{
		final Level level = logger.getLevel();
		return (level != null) ? level.toString() : "<unset>";
	}

    @Managed
	public void setLevel(final String levelString)
	{
    	// Allow erasing the current level using the empty string.
    	if (levelString == null || levelString.trim().isEmpty()) {
    		logger.setLevel(null);
    	}
    	else {
    		final LogLevel level = LogLevel.getLogLevel(levelString);
    		if (level != null) {
    			logger.setLevel(level.getLevel());
    		}
    	}
	}

    @Managed
    public String getParentName()
    {
        final Category parent = logger.getParent();
        return (parent != null) ? parent.getName() : "_ROOT_";
    }
}