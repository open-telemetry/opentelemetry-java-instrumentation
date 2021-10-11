/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.testing.exporter;

import com.google.auto.service.AutoService;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.traces.SdkTracerProviderConfigurer;
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;

@AutoService(SdkTracerProviderConfigurer.class)
public class AgentTestingTracingCustomizer implements SdkTracerProviderConfigurer {

  static final AgentTestingSpanProcessor spanProcessor =
      new AgentTestingSpanProcessor(
          SimpleSpanProcessor.create(AgentTestingExporterFactory.spanExporter));

  static void reset() {
    spanProcessor.forceFlushCalled = false;
  }

  @Override
  public void configure(SdkTracerProviderBuilder tracerProviderBuilder, ConfigProperties config) {
    tracerProviderBuilder.addSpanProcessor(spanProcessor);
  }
}
