/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.common.ComponentLoader;
import io.opentelemetry.instrumentation.jmx.internal.engine.JmxMetricInsight;
import io.opentelemetry.instrumentation.jmx.internal.engine.MetricConfiguration;
import java.util.List;
import java.util.function.Supplier;
import javax.management.MBeanServerConnection;
import javax.management.MBeanServerFactory;

/** Entrypoint for JMX metrics Insights */
public final class JmxTelemetry {
  private final JmxMetricInsight service;
  private final MetricConfiguration metricConfiguration;
  private final ComponentLoader componentLoader;

  /** Returns a new instance configured with the given {@link OpenTelemetry} instance. */
  public static JmxTelemetry create(OpenTelemetry openTelemetry) {
    return builder(openTelemetry).build();
  }

  /** Returns a new {@link JmxTelemetryBuilder} configured with the given {@link OpenTelemetry}. */
  public static JmxTelemetryBuilder builder(OpenTelemetry openTelemetry) {
    return new JmxTelemetryBuilder(openTelemetry);
  }

  JmxTelemetry(
      OpenTelemetry openTelemetry,
      long discoveryDelayMs,
      MetricConfiguration metricConfiguration,
      ComponentLoader componentLoader) {
    this.service = JmxMetricInsight.createService(openTelemetry, discoveryDelayMs);
    this.metricConfiguration = metricConfiguration;
    this.componentLoader = componentLoader;
  }

  /** Starts JMX metrics collection on current JVM */
  public AutoCloseable start() {
    return start(() -> MBeanServerFactory.findMBeanServer(null));
  }

  /**
   * Starts JMX metrics collection on provided (local or remote) connections
   *
   * @param connections connection provider
   */
  public AutoCloseable start(Supplier<List<? extends MBeanServerConnection>> connections) {
    return service.start(metricConfiguration, connections, componentLoader);
  }
}
