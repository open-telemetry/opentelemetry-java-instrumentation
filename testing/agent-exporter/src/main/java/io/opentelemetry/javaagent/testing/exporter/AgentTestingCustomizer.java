/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.testing.exporter;

import com.google.auto.service.AutoService;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.time.Duration;

@AutoService(AutoConfigurationCustomizerProvider.class)
public class AgentTestingCustomizer implements AutoConfigurationCustomizerProvider {

  static final AgentTestingSpanProcessor spanProcessor =
      new AgentTestingSpanProcessor(
          SimpleSpanProcessor.create(AgentTestingExporterFactory.spanExporter));

  static void reset() {
    spanProcessor.forceFlushCalled = false;
  }

  @Override
  public void customize(AutoConfigurationCustomizer autoConfigurationCustomizer) {
    autoConfigurationCustomizer.addTracerProviderCustomizer(
        (tracerProvider, config) -> tracerProvider.addSpanProcessor(spanProcessor));

    autoConfigurationCustomizer.addMeterProviderCustomizer(
        (meterProvider, config) ->
            meterProvider.registerMetricReader(
                PeriodicMetricReader.builder(AgentTestingExporterFactory.metricExporter)
                    .setInterval(Duration.ofMillis(100))
                    .build()));
  }
}
