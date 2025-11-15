/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.jmx.engine.JmxMetricInsight;
import io.opentelemetry.instrumentation.jmx.engine.MetricConfiguration;
import java.util.List;
import java.util.function.Supplier;
import javax.management.MBeanServerConnection;

public class JmxTelemetry {

  private final JmxMetricInsight service;
  private final MetricConfiguration metricConfiguration;

  public static JmxTelemetryBuilder builder(OpenTelemetry openTelemetry) {
    return new JmxTelemetryBuilder(openTelemetry);
  }

  JmxTelemetry(
      OpenTelemetry openTelemetry, long discoveryDelayMs, MetricConfiguration metricConfiguration) {
    this.service = JmxMetricInsight.createService(openTelemetry, discoveryDelayMs);
    this.metricConfiguration = metricConfiguration;
  }

  @SuppressWarnings("unused") // used by jmx-scraper with remote connection
  public void startRemote(Supplier<List<? extends MBeanServerConnection>> connections) {
    service.startRemote(metricConfiguration, connections);
  }

  public void startLocal() {
    service.startLocal(metricConfiguration);
  }
}
