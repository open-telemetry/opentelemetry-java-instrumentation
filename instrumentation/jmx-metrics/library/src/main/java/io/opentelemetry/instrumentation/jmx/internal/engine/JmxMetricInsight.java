/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.internal.engine;

import static java.util.logging.Level.FINE;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.jmx.internal.handler.HandlerRegistry;
import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Logger;
import javax.management.MBeanServerConnection;

/**
 * Collecting and exporting JMX metrics.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public class JmxMetricInsight {

  private static final Logger logger = Logger.getLogger(JmxMetricInsight.class.getName());

  private static final String INSTRUMENTATION_SCOPE = "io.opentelemetry.jmx";
  // version file is generated from the gradle module name; look it up explicitly so the legacy
  // scope name still resolves to a version
  private static final String VERSION_LOOKUP_NAME = "io.opentelemetry.jmx-metrics";

  private final OpenTelemetry openTelemetry;
  private final long discoveryDelay;

  public static JmxMetricInsight createService(OpenTelemetry ot, long discoveryDelay) {
    return new JmxMetricInsight(ot, discoveryDelay);
  }

  private JmxMetricInsight(OpenTelemetry openTelemetry, long discoveryDelay) {
    this.openTelemetry = openTelemetry;
    this.discoveryDelay = discoveryDelay;
  }

  /**
   * Starts metric registration on the provided list of connections
   *
   * @param conf metric configuration
   * @param connections supplier for list of connections (remote or local)
   */
  public AutoCloseable start(
      MetricConfiguration conf,
      Supplier<List<? extends MBeanServerConnection>> connections,
      HandlerRegistry handlerRegistry,
      IncludeExclude metrics) {
    if (conf.isEmpty()) {
      logger.log(
          FINE,
          "Empty JMX configuration, no metrics will be collected for InstrumentationScope "
              + INSTRUMENTATION_SCOPE);
      return () -> {};
    } else {

      MetricRegistrar registrar =
          new MetricRegistrar(openTelemetry, INSTRUMENTATION_SCOPE, VERSION_LOOKUP_NAME, metrics);
      BeanFinder finder = new BeanFinder(conf, registrar, handlerRegistry, discoveryDelay);
      finder.discoverBeans(connections);

      return () -> {
        try {
          finder.shutdown();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw e;
        } finally {
          registrar.close();
        }
      };
    }
  }
}
