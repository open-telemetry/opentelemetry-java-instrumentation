/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.jmx.engine.JmxMetricInsight;
import io.opentelemetry.instrumentation.jmx.engine.MetricConfiguration;
import java.util.List;
import java.util.function.Supplier;
import javax.management.MBeanServerConnection;
import javax.management.MBeanServerFactory;

/** Entrypoint for JMX metrics Insights */
public final class JmxTelemetry {

  private final JmxMetricInsight service;
  private final MetricConfiguration metricConfiguration;

  /** Returns a new {@link JmxTelemetryBuilder} configured with the given {@link OpenTelemetry}. */
  public static JmxTelemetryBuilder builder(OpenTelemetry openTelemetry) {
    return new JmxTelemetryBuilder(openTelemetry);
  }

  JmxTelemetry(
      OpenTelemetry openTelemetry, long discoveryDelayMs, MetricConfiguration metricConfiguration) {
    this.service = JmxMetricInsight.createService(openTelemetry, discoveryDelayMs);
    this.metricConfiguration = metricConfiguration;
  }

  /**
   * Starts JMX metrics collection on current JVM
   *
   * @return this
   */
  @CanIgnoreReturnValue
  public JmxTelemetry start() {
    return this.start(() -> MBeanServerFactory.findMBeanServer(null));
  }

  /**
   * Starts JMX metrics collection on provided (local or remote) connections
   *
   * @param connections connection provider
   * @return this
   */
  @CanIgnoreReturnValue
  public JmxTelemetry start(Supplier<List<? extends MBeanServerConnection>> connections) {
    service.start(metricConfiguration, connections);
    return this;
  }
}
