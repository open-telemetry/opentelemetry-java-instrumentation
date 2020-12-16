/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.testing.exporter;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.spi.TracerCustomizer;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.export.IntervalMetricReader;
import io.opentelemetry.sdk.trace.TracerSdkManagement;
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
  public void configure(TracerSdkManagement tracerManagement) {
    tracerManagement.addSpanProcessor(spanProcessor);
    IntervalMetricReader.builder()
        .setExportIntervalMillis(100)
        .setMetricExporter(AgentTestingExporterFactory.metricExporter)
        .setMetricProducers(
            Collections.singleton(OpenTelemetrySdk.getGlobalMeterProvider().getMetricProducer()))
        .build();
  }
}
