/*  Copyright 2007 Niclas Hedhman.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ops4j.pax.logging.spi.support;

import org.ops4j.pax.logging.PaxLogger;
import org.ops4j.pax.logging.PaxLoggingConstants;
import org.ops4j.pax.logging.PaxLoggingManager;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.FrameworkListener;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.SynchronousBundleListener;
import org.osgi.service.log.LogLevel;

/**
 * One instance of this class will be registered to listen for events generated by
 * the OSGi framework and pass those events to log service.
 *
 * By default, all events log by this class are logged at the DEBUG level. This can be
 * changed to a different level by setting the context or system property
 * {@code org.ops4j.pax.logging.service.frameworkEventsLogLevel} to DEBUG, INFO, WARNING, or ERROR.
 *
 * According OSGi Compendium R7, 101.8 Mapping of Events, each even has precise logging level associated and using
 * {@link PaxLoggingConstants#LOGGING_CFG_FRAMEWORK_EVENTS_LOG_LEVEL} we can filter out some events.
 */
public class FrameworkHandler
        implements SynchronousBundleListener, FrameworkListener, ServiceListener {

    private final PaxLoggingManager m_manager;

    /**
     * Threshold to filter framework/bundle/service events according to 101.8 chapter of OSGi Cmpn specification.
     */
    private final LogLevel loggingThreshold;

    public FrameworkHandler(BundleContext context, final PaxLoggingManager manager) {
        m_manager = manager;

        String defaultThreshold = "ERROR";
        String threshold = OsgiUtil.systemOrContextProperty(context, PaxLoggingConstants.LOGGING_CFG_FRAMEWORK_EVENTS_LOG_LEVEL);
        if (threshold != null && !"".equals(threshold)) {
            defaultThreshold = threshold;
        }
        loggingThreshold = BackendSupport.convertR7LogLevel(threshold, LogLevel.ERROR);
    }

    /**
     * {@link BundleEvent bundle events} are logged with {@link LogLevel#INFO} level (according to spec) unless
     * other level is specified by {@link PaxLoggingConstants#LOGGING_CFG_FRAMEWORK_EVENTS_LOG_LEVEL}.
     * @param bundleEvent
     */
    @Override
    public void bundleChanged(final BundleEvent bundleEvent) {
        String message;
        final int type = bundleEvent.getType();
        // 101.8.1 "Bundle Events Mapping"
        final LogLevel level = LogLevel.INFO;
        switch (type) {
            case BundleEvent.INSTALLED:
                message = "BundleEvent INSTALLED";
                break;
            case BundleEvent.STARTED:
                message = "BundleEvent STARTED";
                break;
            case BundleEvent.STOPPED:
                message = "BundleEvent STOPPED";
                break;
            case BundleEvent.UPDATED:
                message = "BundleEvent UPDATED";
                break;
            case BundleEvent.UNINSTALLED:
                message = "BundleEvent UNINSTALLED";
                break;
            case BundleEvent.RESOLVED:
                message = "BundleEvent RESOLVED";
                break;
            case BundleEvent.UNRESOLVED:
                message = "BundleEvent UNRESOLVED";
                break;
            case BundleEvent.STARTING:
                message = "BundleEvent STARTING";
                break;
            case BundleEvent.STOPPING:
                message = "BundleEvent STOPPING";
                break;
            default:
                message = "BundleEvent [unknown: " + type + "]";
                break;
        }
        // bundle event messages don't have to add " - <bundle symbolic name>", because SN is part of the event anyway
//        if (bundle != null) {
//            message += " - " + bundle.getSymbolicName();
//        }
        final Bundle bundle = bundleEvent.getBundle();
        doLog(level, bundle, "Events.Bundle", message);
    }

    /**
     * Specification determines logging level for given framework events. But we're overriding the values.
     * @param frameworkEvent
     */
    @Override
    public void frameworkEvent(final FrameworkEvent frameworkEvent) {
        final int type = frameworkEvent.getType();
        String message;
        // 101.8.3 "Service Events Mapping"
        LogLevel level = LogLevel.INFO;
        switch (type) {
            case FrameworkEvent.ERROR:
                message = "FrameworkEvent ERROR";
                level = LogLevel.ERROR;
                break;
            case FrameworkEvent.INFO:
                message = "FrameworkEvent INFO";
                break;
            case FrameworkEvent.PACKAGES_REFRESHED:
                message = "FrameworkEvent PACKAGES REFRESHED";
                break;
            case FrameworkEvent.STARTED:
                message = "FrameworkEvent STARTED";
                break;
            case FrameworkEvent.STARTLEVEL_CHANGED:
                message = "FrameworkEvent STARTLEVEL CHANGED";
                break;
            case FrameworkEvent.WARNING:
                message = "FrameworkEvent WARNING";
                level = LogLevel.WARN;
                break;
            default:
                message = "FrameworkEvent [unknown:" + type + "]";
                break;
        }
        final Bundle bundle = frameworkEvent.getBundle();
        final Throwable exception = frameworkEvent.getThrowable();
        doLog(level, bundle, "Events.Framework", message, exception);
    }

    @Override
    public void serviceChanged(final ServiceEvent serviceEvent) {
        final ServiceReference<?> serviceRef = serviceEvent.getServiceReference();
        String message;
        final int type = serviceEvent.getType();
        // 101.8.2 "Service Events Mapping"
        LogLevel level = LogLevel.INFO;
        switch (type) {
            case ServiceEvent.MODIFIED:
                message = "ServiceEvent MODIFIED";
                level = LogLevel.DEBUG;
                break;
            case ServiceEvent.REGISTERED:
                message = "ServiceEvent REGISTERED";
                break;
            case ServiceEvent.UNREGISTERING:
                message = "ServiceEvent UNREGISTERING";
                break;
            default:
                message = "ServiceEvent [unknown:" + type + "]";
                break;
        }
        // service events, even if specification doesn't say so, have serviceRef.toString() appended to the message
        message += " - " + serviceRef;
        Bundle bundle = serviceRef.getBundle();
        doLog(level, bundle, "Events.Service", message, serviceRef);
    }

    private void doLog(LogLevel loggingLevel, Bundle bundle, String category, String message, Object... args) {
        if (loggingLevel == null) {
            // OFF or NONE specified as logging level
            return;
        }

        PaxLogger logger = m_manager.getLogger(bundle, category, "");

        switch (loggingLevel) {
            case AUDIT:
                logger.audit(message, args);
                break;
            case ERROR:
                logger.error(message, args);
                break;
            case WARN:
                logger.warn(message, args);
                break;
            case INFO:
                logger.info(message, args);
                break;
            case DEBUG:
                logger.debug(message, args);
                break;
            case TRACE:
                logger.trace(message, args);
                break;
            default:
                break;
        }
    }

}
