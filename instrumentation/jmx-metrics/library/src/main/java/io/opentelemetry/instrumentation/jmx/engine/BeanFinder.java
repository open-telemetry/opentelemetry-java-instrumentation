/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.engine;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.ObjectName;

/**
 * A class responsible for finding MBeans that match metric definitions specified by a set of
 * MetricDefs.
 */
class BeanFinder {

  private final MetricRegistrar registrar;
  private MetricConfiguration conf;
  private final ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();
  private final long discoveryDelay;
  private final long maxDelay;
  private long delay = 1000; // number of milliseconds until first attempt to discover MBeans

  BeanFinder(MetricRegistrar registrar, long discoveryDelay) {
    this.registrar = registrar;
    this.discoveryDelay = Math.max(1000, discoveryDelay); // Enforce sanity
    this.maxDelay = Math.max(60000, discoveryDelay);
  }

  void discoverBeans(MetricConfiguration conf) {
    this.conf = conf;

    if (!conf.isEmpty()) {
      // Issue 9336: Corner case: PlatformMBeanServer will remain unitialized until a direct
      // reference to it is made. This call makes sure that the PlatformMBeanServer will be in
      // the set of MBeanServers reported by MBeanServerFactory.
      ManagementFactory.getPlatformMBeanServer();
    }

    exec.schedule(
        new Runnable() {
          @Override
          public void run() {
            refreshState();
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
   */
  private void refreshState() {
    List<MBeanServer> servers = MBeanServerFactory.findMBeanServer(null);

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
   * @param servers the list of MBeanServers to query
   */
  private void resolveBeans(MetricDef metricDef, List<MBeanServer> servers) {
    BeanGroup beans = metricDef.getBeanGroup();

    for (MBeanServer server : servers) {
      // The set of all matching ObjectNames recognized by the server
      Set<ObjectName> allObjectNames = new HashSet<>();

      for (ObjectName pattern : beans.getNamePatterns()) {
        Set<ObjectName> objectNames = server.queryNames(pattern, beans.getQueryExp());
        allObjectNames.addAll(objectNames);
      }

      if (!allObjectNames.isEmpty()) {
        resolveAttributes(allObjectNames, server, metricDef);

        // Assuming that only one MBeanServer has the required MBeans
        break;
      }
    }
  }

  /**
   * Go over the collection of matching MBeans and try to find all matching attributes. For every
   * successful match, activate metric value collection.
   *
   * @param objectNames the collection of ObjectNames identifying the MBeans
   * @param server the MBeanServer which recognized the collection of ObjectNames
   * @param metricDef the MetricDef describing the attributes to look for
   */
  private void resolveAttributes(
      Set<ObjectName> objectNames, MBeanServer server, MetricDef metricDef) {
    for (MetricExtractor extractor : metricDef.getMetricExtractors()) {
      // For each MetricExtractor, find the subset of MBeans that have the required attribute
      List<ObjectName> validObjectNames = new ArrayList<>();
      AttributeInfo attributeInfo = null;
      for (ObjectName objectName : objectNames) {
        AttributeInfo attr =
            extractor.getMetricValueExtractor().getAttributeInfo(server, objectName);
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
        registrar.enrollExtractor(server, validObjectNames, extractor, attributeInfo);
      }
    }
  }
}
