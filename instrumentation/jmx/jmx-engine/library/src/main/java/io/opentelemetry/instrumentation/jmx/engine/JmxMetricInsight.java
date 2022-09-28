/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.engine;

import static java.util.logging.Level.CONFIG;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.util.logging.Logger;

/** Collecting and exporting JMX metrics. */
public class JmxMetricInsight {

  private static final Logger logger = Logger.getLogger(JmxMetricInsight.class.getName());

  private static final String INSTRUMENTATION_SCOPE = "io.opentelemetry.jmx";

  private final OpenTelemetry openTelemetry;
  private final ConfigProperties configProperties;

  public static JmxMetricInsight createService(OpenTelemetry ot, ConfigProperties config) {
    return new JmxMetricInsight(ot, config);
  }

  public static Logger getLogger() {
    return logger;
  }

  private JmxMetricInsight(OpenTelemetry ot, ConfigProperties config) {
    openTelemetry = ot;
    configProperties = config;
  }

  public void start(MetricConfiguration conf) {
    if (conf.isEmpty()) {
      logger.log(
          CONFIG,
          "Empty JMX configuration, no metrics will be collected for InstrumentationScope "
              + INSTRUMENTATION_SCOPE);
    } else {
      MetricRegistrar registrar = new MetricRegistrar(openTelemetry, INSTRUMENTATION_SCOPE);
      BeanFinder finder = new BeanFinder(registrar, configProperties);
      finder.discoverBeans(conf);
    }
  }
}
