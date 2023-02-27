/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.engine;

import static java.util.logging.Level.FINE;

import io.opentelemetry.api.OpenTelemetry;
import java.util.logging.Logger;

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

  public void start(MetricConfiguration conf) {
    if (conf.isEmpty()) {
      logger.log(
          FINE,
          "Empty JMX configuration, no metrics will be collected for InstrumentationScope "
              + INSTRUMENTATION_SCOPE);
    } else {
      MetricRegistrar registrar = new MetricRegistrar(openTelemetry, INSTRUMENTATION_SCOPE);
      BeanFinder finder = new BeanFinder(registrar, discoveryDelay);
      finder.discoverBeans(conf);
    }
  }
}
