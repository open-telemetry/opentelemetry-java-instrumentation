/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.exporters.otlp;

import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporterBuilder;
import io.opentelemetry.instrumentation.spring.autoconfigure.OpenTelemetryAutoConfiguration;
import java.time.Duration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@AutoConfigureBefore(OpenTelemetryAutoConfiguration.class)
@EnableConfigurationProperties(OtlpExporterProperties.class)
@ConditionalOnProperty(
    prefix = "otel.exporter.otlp",
    name = {"enabled", "metrics.enabled"},
    matchIfMissing = true)
@ConditionalOnClass(OtlpGrpcMetricExporter.class)
public class OtlpMetricExporterAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public OtlpGrpcMetricExporter otelOtlpGrpcMetricExporter(OtlpExporterProperties properties) {
    OtlpGrpcMetricExporterBuilder builder = OtlpGrpcMetricExporter.builder();

    String endpoint = properties.getMetrics().getEndpoint();
    if (endpoint == null) {
      endpoint = properties.getEndpoint();
    }
    if (endpoint != null) {
      builder.setEndpoint(endpoint);
    }

    Duration timeout = properties.getMetrics().getTimeout();
    if (timeout == null) {
      timeout = properties.getTimeout();
    }
    if (timeout != null) {
      builder.setTimeout(timeout);
    }

    return builder.build();
  }
}
