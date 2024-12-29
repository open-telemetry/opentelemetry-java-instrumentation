/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.engine;

import static java.util.logging.Level.FINE;

import io.opentelemetry.api.OpenTelemetry;
import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Logger;
import javax.management.MBeanServerConnection;
import javax.management.MBeanServerFactory;

/** Collecting and exporting JMX metrics. */
public class JmxMetricInsight {

  private static final Logger logger = Logger.getLogger(JmxMetricInsight.class.getName());

  private static final String INSTRUMENTATION_SCOPE = "io.opentelemetry.jmx";

  private final OpenTelemetry openTelemetry;
  private final long discoveryDelay;

  public static JmxMetricInsight createService(OpenTelemetry ot, long discoveryDelay) {
    return new JmxMetricInsight(ot, discoveryDelay);
  }

  public static Logger getLogger() {
    return logger;
  }

  private JmxMetricInsight(OpenTelemetry openTelemetry, long discoveryDelay) {
    this.openTelemetry = openTelemetry;
    this.discoveryDelay = discoveryDelay;
  }

  /**
   * Starts metric registration for local JVM
   *
   * @param conf metric configuration
   */
  public void startLocal(MetricConfiguration conf) {
    start(conf, () -> MBeanServerFactory.findMBeanServer(null));
  }

  /**
   * Starts metric registration for a remote JVM connection
   *
   * @param conf metric configuration
   * @param connections supplier for list of remote connections
   */
  public void startRemote(
      MetricConfiguration conf, Supplier<List<? extends MBeanServerConnection>> connections) {
    start(conf, connections);
  }

  private void start(
      MetricConfiguration conf, Supplier<List<? extends MBeanServerConnection>> connections) {
    if (conf.isEmpty()) {
      logger.log(
          FINE,
          "Empty JMX configuration, no metrics will be collected for InstrumentationScope "
              + INSTRUMENTATION_SCOPE);
    } else {
      MetricRegistrar registrar = new MetricRegistrar(openTelemetry, INSTRUMENTATION_SCOPE);
      BeanFinder finder = new BeanFinder(registrar, discoveryDelay);
      finder.discoverBeans(conf, connections);
    }
  }
}
