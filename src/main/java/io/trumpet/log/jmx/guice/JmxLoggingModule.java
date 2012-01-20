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
package io.trumpet.log.jmx.guice;

import io.trumpet.log.jmx.Log4JMBean;

import javax.management.MBeanServer;

import com.google.common.base.Preconditions;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.name.Names;
import com.likeness.lifecycle.Lifecycle;
import com.likeness.lifecycle.LifecycleStage;
import com.likeness.lifecycle.guice.AbstractLifecycleProvider;
import com.likeness.lifecycle.guice.LifecycleAction;

public class JmxLoggingModule extends AbstractModule
{
    private final String context;

    public JmxLoggingModule(final String context)
    {
        this.context = context;
    }

    @Override
    public void configure()
    {
        bind(Log4JMBean.class).annotatedWith(Names.named(context)).toProvider(new Log4JMBeanProvider(context)).asEagerSingleton();
    }


    public final class Log4JMBeanProvider extends AbstractLifecycleProvider<Log4JMBean> implements Provider<Log4JMBean>
    {
        private final String context;

        private Lifecycle lifecycle = null;
        private MBeanServer mbeanServer = null;

        private Log4JMBeanProvider(final String context)
        {
            this.context = context;

            addAction(LifecycleStage.START_STAGE, new LifecycleAction<Log4JMBean>() {
                    @Override
                    public void performAction(final Log4JMBean log4jMBean) {
                        log4jMBean.start();
                    }
                });

            addAction(LifecycleStage.STOP_STAGE, new LifecycleAction<Log4JMBean>() {
                    @Override
                    public void performAction(final Log4JMBean log4jMBean) {
                        log4jMBean.stop();
                    }
                });
        }

        @Inject
        public void setDependencies(final Lifecycle lifecycle,
                                    final MBeanServer mbeanServer)
        {
            this.lifecycle = lifecycle;
            this.mbeanServer = mbeanServer;
        }

        @Override
        protected Log4JMBean internalGet()
        {
            Preconditions.checkNotNull(lifecycle, "lifecycle is null, missed call to setDependencies()?");
            Preconditions.checkNotNull(mbeanServer, "mbeanServer is null, missed call to setDependencies()?");

            return new Log4JMBean(mbeanServer, Log4JMBean.DEFAULT_OBJECT_NAME + ",context=" + context);
        }
    }
}


