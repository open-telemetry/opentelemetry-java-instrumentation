/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.config.TraceConfig;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Create {@link io.opentelemetry.api.trace.Tracer} bean if bean is missing.
 *
 * <p>Adds span exporter beans to the active tracer provider.
 *
 * <p>Updates the sampler probability in the active {@link TraceConfig}
 */
@Configuration
@EnableConfigurationProperties(TracerProperties.class)
public class TracerAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public Tracer otelTracer(
      TracerProperties tracerProperties, ObjectProvider<List<SpanExporter>> spanExportersProvider)
      throws Exception {
    Tracer tracer = GlobalOpenTelemetry.getTracer(tracerProperties.getName());

    List<SpanExporter> spanExporters = spanExportersProvider.getIfAvailable();
    if (spanExporters == null || spanExporters.isEmpty()) {
      return tracer;
    }

    addSpanProcessors(spanExporters);
    setSampler(tracerProperties);

    return tracer;
  }

  private void addSpanProcessors(List<SpanExporter> spanExporters) {
    List<SpanProcessor> spanProcessors =
        spanExporters.stream()
            .map(spanExporter -> SimpleSpanProcessor.builder(spanExporter).build())
            .collect(Collectors.toList());

    OpenTelemetrySdk.getGlobalTracerManagement()
        .addSpanProcessor(SpanProcessor.composite(spanProcessors));
  }

  private void setSampler(TracerProperties tracerProperties) {
    TraceConfig updatedTraceConfig =
        OpenTelemetrySdk.getGlobalTracerManagement().getActiveTraceConfig().toBuilder()
            .setSampler(Sampler.traceIdRatioBased(tracerProperties.getSamplerProbability()))
            .build();

    OpenTelemetrySdk.getGlobalTracerManagement().updateActiveTraceConfig(updatedTraceConfig);
  }
}
