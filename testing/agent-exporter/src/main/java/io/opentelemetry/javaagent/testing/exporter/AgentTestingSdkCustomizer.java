/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.testing.exporter;

import com.google.auto.service.AutoService;
import io.opentelemetry.api.metrics.GlobalMetricsProvider;
import io.opentelemetry.javaagent.spi.TracerCustomizer;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.IntervalMetricReader;
import io.opentelemetry.sdk.trace.SdkTracerManagement;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.util.Collections;

@AutoService(TracerCustomizer.class)
public class AgentTestingSdkCustomizer implements TracerCustomizer {

  static final AgentTestingSpanProcessor spanProcessor =
      new AgentTestingSpanProcessor(
          SimpleSpanProcessor.builder(AgentTestingExporterFactory.spanExporter).build());

  static void reset() {
    spanProcessor.forceFlushCalled = false;
  }

  @Override
  public void configure(SdkTracerManagement tracerManagement) {
    tracerManagement.addSpanProcessor(spanProcessor);
    IntervalMetricReader.builder()
        .setExportIntervalMillis(100)
        .setMetricExporter(AgentTestingExporterFactory.metricExporter)
        .setMetricProducers(Collections.singleton((SdkMeterProvider) GlobalMetricsProvider.get()))
        .build();
  }
}
