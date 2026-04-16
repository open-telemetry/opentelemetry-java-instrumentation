/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.testing.provider;

import static java.util.Objects.requireNonNull;

import com.google.auto.service.AutoService;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.internal.ComponentProvider;
import io.opentelemetry.sdk.metrics.export.MetricExporter;

@AutoService(ComponentProvider.class)
public class AgentTestMetricExporterComponentProvider implements ComponentProvider {

  private static MetricExporter metricExporter;

  @Override
  public Class<MetricExporter> getType() {
    return MetricExporter.class;
  }

  @Override
  public String getName() {
    return "agent_test";
  }

  @Override
  public MetricExporter create(DeclarativeConfigProperties config) {
    return requireNonNull(metricExporter, "metricExporter must not be null");
  }

  public static void setMetricExporter(MetricExporter metricExporter) {
    AgentTestMetricExporterComponentProvider.metricExporter = metricExporter;
  }
}
