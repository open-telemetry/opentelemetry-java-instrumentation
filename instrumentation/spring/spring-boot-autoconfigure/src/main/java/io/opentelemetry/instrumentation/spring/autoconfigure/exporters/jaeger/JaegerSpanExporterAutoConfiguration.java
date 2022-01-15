/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.exporters.jaeger;

import io.opentelemetry.exporter.jaeger.JaegerGrpcSpanExporter;
import io.opentelemetry.exporter.jaeger.JaegerGrpcSpanExporterBuilder;
import io.opentelemetry.instrumentation.spring.autoconfigure.OpenTelemetryAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures {@link JaegerGrpcSpanExporter} for tracing.
 *
 * <p>Initializes {@link JaegerGrpcSpanExporter} bean if bean is missing.
 */
@Configuration
@AutoConfigureBefore(OpenTelemetryAutoConfiguration.class)
@EnableConfigurationProperties(JaegerSpanExporterProperties.class)
@ConditionalOnProperty(prefix = "otel.exporter.jaeger", name = "enabled", matchIfMissing = true)
@ConditionalOnClass(JaegerGrpcSpanExporter.class)
public class JaegerSpanExporterAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public JaegerGrpcSpanExporter otelJaegerSpanExporter(JaegerSpanExporterProperties properties) {

    JaegerGrpcSpanExporterBuilder builder = JaegerGrpcSpanExporter.builder();
    if (properties.getEndpoint() != null) {
      builder.setEndpoint(properties.getEndpoint());
    }
    if (properties.getTimeout() != null) {
      builder.setTimeout(properties.getTimeout());
    }
    return builder.build();
  }
}
