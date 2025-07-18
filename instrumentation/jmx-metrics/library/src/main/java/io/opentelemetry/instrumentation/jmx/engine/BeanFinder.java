/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.engine;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;

/**
 * A class responsible for finding MBeans that match metric definitions specified by a set of
 * MetricDefs.
 */
class BeanFinder {

  private static final Logger logger = Logger.getLogger(BeanFinder.class.getName());

  private final MetricRegistrar registrar;
  private MetricConfiguration conf;
  private final ScheduledExecutorService exec =
      Executors.newSingleThreadScheduledExecutor(
          runnable -> {
            Thread result = new Thread(runnable, "jmx_bean_finder");
            result.setDaemon(true);
            result.setContextClassLoader(null);
            return result;
          });
  private final long discoveryDelay;
  private final long maxDelay;
  private long delay = 1000; // number of milliseconds until first attempt to discover MBeans

  BeanFinder(MetricRegistrar registrar, long discoveryDelay) {
    this.registrar = registrar;
    this.discoveryDelay = Math.max(1000, discoveryDelay); // Enforce sanity
    this.maxDelay = Math.max(60000, discoveryDelay);
  }

  /**
   * Starts bean discovery on a list of local or remote connections
   *
   * @param conf metric configuration
   * @param connections supplier for instances of {@link MBeanServerConnection}, will be invoked
   *     after delayed JMX initialization once the {@link #discoveryDelay} has expired.
   */
  void discoverBeans(
      MetricConfiguration conf, Supplier<List<? extends MBeanServerConnection>> connections) {
    this.conf = conf;

    exec.schedule(
        () -> {
          // Issue 9336: Corner case: PlatformMBeanServer will remain uninitialized until a direct
          // reference to it is made. This call makes sure that the PlatformMBeanServer will be in
          // the set of MBeanServers reported by MBeanServerFactory.
          // Issue 11143: This call initializes java.util.logging.LogManager. We should not call it
          // before application has had a chance to configure custom log manager. This is needed for
          // wildfly.
          ManagementFactory.getPlatformMBeanServer();
        },
        discoveryDelay,
        TimeUnit.MILLISECONDS);

    exec.schedule(
        new Runnable() {
          @Override
          public void run() {
            refreshState(connections);
            // Use discoveryDelay as the increment for the actual delay
            delay = Math.min(delay + discoveryDelay, maxDelay);
            exec.schedule(this, delay, TimeUnit.MILLISECONDS);
          }
        },
        delay,
        TimeUnit.MILLISECONDS);
  }

  /**
   * Go over all configured metric definitions and try to find matching MBeans. Once a match is
   * found for a given metric definition, submit the definition to MetricRegistrar for further
   * handling. Successive invocations of this method may find matches that were previously
   * unavailable, in such cases MetricRegistrar will extend the coverage for the new MBeans
   *
   * @param connections supplier providing {@link MBeanServerConnection} instances to query
   */
  private void refreshState(Supplier<List<? extends MBeanServerConnection>> connections) {
    List<? extends MBeanServerConnection> servers = connections.get();

    for (MetricDef metricDef : conf.getMetricDefs()) {
      resolveBeans(metricDef, servers);
    }
  }

  /**
   * Go over the specified list of MBeanServers and try to find any MBeans matching the specified
   * MetricDef. If found, verify that the MBeans support the specified attributes, and set up
   * collection of corresponding metrics.
   *
   * @param metricDef the MetricDef used to find matching MBeans
   * @param connections the list of {@link MBeanServerConnection} to query
   */
  private void resolveBeans(
      MetricDef metricDef, List<? extends MBeanServerConnection> connections) {
    BeanGroup beans = metricDef.getBeanGroup();

    for (MBeanServerConnection connection : connections) {
      // The set of all matching ObjectNames recognized by the server
      Set<ObjectName> allObjectNames = new HashSet<>();

      for (ObjectName pattern : beans.getNamePatterns()) {
        try {
          allObjectNames.addAll(connection.queryNames(pattern, beans.getQueryExp()));
        } catch (IOException e) {
          logger.log(Level.WARNING, "IO error while resolving mbean", e);
        }
      }

      if (!allObjectNames.isEmpty()) {
        resolveAttributes(allObjectNames, connection, metricDef);

        // Assuming that only one MBeanServer has the required MBeans
        break;
      }
    }
  }

  /**
   * Go over the collection of matching MBeans and try to find all matching attributes. For every
   * successful match, activate metric value collection.
   *
   * @param objectNames the collection of {@link ObjectName}s identifying the MBeans
   * @param connection the {@link MBeanServerConnection} which recognized the collection of
   *     ObjectNames
   * @param metricDef the {@link MetricDef} describing the attributes to look for
   */
  private void resolveAttributes(
      Set<ObjectName> objectNames, MBeanServerConnection connection, MetricDef metricDef) {
    for (MetricExtractor extractor : metricDef.getMetricExtractors()) {
      // For each MetricExtractor, find the subset of MBeans that have the required attribute
      List<ObjectName> validObjectNames = new ArrayList<>();
      AttributeInfo attributeInfo = null;
      for (ObjectName objectName : objectNames) {
        AttributeInfo attr =
            extractor.getMetricValueExtractor().getAttributeInfo(connection, objectName);
        if (attr != null) {
          if (attributeInfo == null) {
            attributeInfo = attr;
          } else {
            attributeInfo.updateFrom(attr);
          }
          validObjectNames.add(objectName);
        }
      }
      if (!validObjectNames.isEmpty()) {
        // Ready to collect metric values
        registrar.enrollExtractor(connection, validObjectNames, extractor, attributeInfo);
      }
    }
  }
}
