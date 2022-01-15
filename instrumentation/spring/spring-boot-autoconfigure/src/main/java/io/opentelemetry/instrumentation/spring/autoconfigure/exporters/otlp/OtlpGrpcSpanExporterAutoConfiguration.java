/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.exporters.otlp;

import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporterBuilder;
import io.opentelemetry.instrumentation.spring.autoconfigure.OpenTelemetryAutoConfiguration;
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
@EnableConfigurationProperties(OtlpGrpcSpanExporterProperties.class)
@ConditionalOnProperty(prefix = "otel.exporter.otlp", name = "enabled", matchIfMissing = true)
@ConditionalOnClass(OtlpGrpcSpanExporter.class)
public class OtlpGrpcSpanExporterAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public OtlpGrpcSpanExporter otelOtlpGrpcSpanExporter(OtlpGrpcSpanExporterProperties properties) {

    OtlpGrpcSpanExporterBuilder builder = OtlpGrpcSpanExporter.builder();
    if (properties.getEndpoint() != null) {
      builder.setEndpoint(properties.getEndpoint());
    }
    if (properties.getTimeout() != null) {
      builder.setTimeout(properties.getTimeout());
    }
    return builder.build();
  }
}
