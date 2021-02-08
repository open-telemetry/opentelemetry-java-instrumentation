/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure;

import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import java.util.Collections;
import java.util.List;
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
 * <p>Updates the sampler probability for the configured {@link TracerProvider}.
 */
@Configuration
@EnableConfigurationProperties(TracerProperties.class)
public class TracerAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public Tracer otelTracer(TracerProvider tracerProvider, TracerProperties tracerProperties) {
    return tracerProvider.get(tracerProperties.getName());
  }

  @Bean
  @ConditionalOnMissingBean
  public TracerProvider tracerProvider(
      TracerProperties tracerProperties, ObjectProvider<List<SpanExporter>> spanExportersProvider) {
    SdkTracerProviderBuilder tracerProviderBuilder = SdkTracerProvider.builder();

    spanExportersProvider.getIfAvailable(Collections::emptyList).stream()
        // todo SimpleSpanProcessor...is that really what we want here?
        .map(SimpleSpanProcessor::create)
        .forEach(tracerProviderBuilder::addSpanProcessor);

    SdkTracerProvider tracerProvider =
        tracerProviderBuilder
            .setSampler(Sampler.traceIdRatioBased(tracerProperties.getSamplerProbability()))
            .build();
    OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).buildAndRegisterGlobal();
    return tracerProvider;
  }
}
