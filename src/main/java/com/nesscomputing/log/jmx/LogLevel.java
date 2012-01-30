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

import org.apache.log4j.Level;

/**
 * Represents the log levels of log4j as an enum. Reasonable log levels are the ones supported
 * by {@link io.trumpet.log.Log} and only those can be set through JMX.
 */
public enum LogLevel
{
	ALL(Level.ALL, false), TRACE(Level.TRACE, true),
	DEBUG(Level.DEBUG, true), INFO(Level.INFO, true), WARN(Level.WARN, true), ERROR(Level.ERROR, true),
	FATAL(Level.FATAL, false), OFF(Level.OFF, false);

    private final Level level;
    private final boolean reasonable;

    private LogLevel(final Level level, final boolean reasonable)
    {
        this.level = level;
        this.reasonable = reasonable;
    }

    public Level getLevel()
    {
        return level;
    }

    /**
     * Only reasonable levels can be set through JMX.
     */
    public boolean isReasonable()
    {
    	return reasonable;
    }

    public static final LogLevel getLogLevel(final Level level)
    {
        for (LogLevel logLevel : LogLevel.values()) {
            if (logLevel.getLevel().equals(level)) {
                return logLevel;
            }
        }
        throw new IllegalArgumentException("Unknown level " + level + " encountered!");
    }

    public static final LogLevel getLogLevel(final String levelName)
    {
        for (LogLevel logLevel : LogLevel.values()) {
        	// Don't do stupid things...
            if (logLevel.name().equals(levelName)  && logLevel.isReasonable()) {
                return logLevel;
            }
        }

        return null;
    }
}
