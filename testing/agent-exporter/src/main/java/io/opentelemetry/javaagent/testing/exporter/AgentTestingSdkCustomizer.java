/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.testing.exporter;

import com.google.auto.service.AutoService;
import io.opentelemetry.api.metrics.GlobalMeterProvider;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.traces.SdkTracerProviderConfigurer;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.IntervalMetricReader;
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.util.Collections;

@AutoService(SdkTracerProviderConfigurer.class)
public class AgentTestingSdkCustomizer implements SdkTracerProviderConfigurer {

  static final AgentTestingSpanProcessor spanProcessor =
      new AgentTestingSpanProcessor(
          SimpleSpanProcessor.create(AgentTestingExporterFactory.spanExporter));

  static void reset() {
    spanProcessor.forceFlushCalled = false;
  }

  @Override
  public void configure(SdkTracerProviderBuilder tracerProviderBuilder, ConfigProperties config) {
    tracerProviderBuilder.addSpanProcessor(spanProcessor);

    // Until metrics story settles down there is no SPI for it, we rely on the fact that metrics is
    // already set up when tracing configuration begins.
    IntervalMetricReader.builder()
        .setExportIntervalMillis(100)
        .setMetricExporter(AgentTestingExporterFactory.metricExporter)
        .setMetricProducers(Collections.singleton((SdkMeterProvider) GlobalMeterProvider.get()))
        .buildAndStart();
  }
}
