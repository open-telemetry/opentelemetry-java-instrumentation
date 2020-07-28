/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.instrumentation.spring.autoconfigure;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.MultiSpanProcessor;
import io.opentelemetry.sdk.trace.Samplers;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.config.TraceConfig;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.trace.Tracer;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Create {@link io.opentelemetry.trace.Tracer} bean if bean is missing.
 *
 * <p>Adds span exporter beans to the active tracer provider {@code
 * OpenTelemetrySdk.getTracerProvider()}
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
    Tracer tracer = OpenTelemetry.getTracer(tracerProperties.getName());

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
            .map(spanExporter -> SimpleSpanProcessor.newBuilder(spanExporter).build())
            .collect(Collectors.toList());

    OpenTelemetrySdk.getTracerProvider()
        .addSpanProcessor(MultiSpanProcessor.create(spanProcessors));
  }

  private void setSampler(TracerProperties tracerProperties) {
    TraceConfig updatedTraceConfig =
        OpenTelemetrySdk.getTracerProvider()
            .getActiveTraceConfig()
            .toBuilder()
            .setSampler(Samplers.probability(tracerProperties.getSamplerProbability()))
            .build();

    OpenTelemetrySdk.getTracerProvider().updateActiveTraceConfig(updatedTraceConfig);
  }
}
