/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.exporters.otlp;

import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporterBuilder;
import io.opentelemetry.instrumentation.spring.autoconfigure.OpenTelemetryAutoConfiguration;
import java.time.Duration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures {@link OtlpGrpcSpanExporter} for tracing.
 *
 * <p>Initializes {@link OtlpGrpcSpanExporter} bean if bean is missing.
 */
@Configuration
@AutoConfigureBefore(OpenTelemetryAutoConfiguration.class)
@EnableConfigurationProperties(OtlpExporterProperties.class)
@ConditionalOnProperty(
    prefix = "otel.exporter.otlp",
    name = {"enabled", "traces.enabled"},
    matchIfMissing = true)
@ConditionalOnClass(OtlpGrpcSpanExporter.class)
public class OtlpSpanExporterAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public OtlpGrpcSpanExporter otelOtlpGrpcSpanExporter(OtlpExporterProperties properties) {
    OtlpGrpcSpanExporterBuilder builder = OtlpGrpcSpanExporter.builder();

    String endpoint = properties.getTraces().getEndpoint();
    if (endpoint == null) {
      endpoint = properties.getEndpoint();
    }
    if (endpoint != null) {
      builder.setEndpoint(endpoint);
    }

    Duration timeout = properties.getTraces().getTimeout();
    if (timeout == null) {
      timeout = properties.getTimeout();
    }
    if (timeout != null) {
      builder.setTimeout(timeout);
    }

    return builder.build();
  }
}
