/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.exporters.otlp;

import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter;
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporterBuilder;
import io.opentelemetry.instrumentation.spring.autoconfigure.OpenTelemetryAutoConfiguration;
import java.time.Duration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfigureBefore(OpenTelemetryAutoConfiguration.class)
@EnableConfigurationProperties(OtlpExporterProperties.class)
@ConditionalOnProperty(
    prefix = "otel.exporter.otlp",
    name = {"enabled", "logs.enabled"},
    matchIfMissing = true)
@ConditionalOnClass(OtlpGrpcLogRecordExporter.class)
public class OtlpLoggerExporterAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public OtlpGrpcLogRecordExporter otelOtlpGrpcLogRecordExporter(
      OtlpExporterProperties properties) {
    OtlpGrpcLogRecordExporterBuilder builder = OtlpGrpcLogRecordExporter.builder();

    String endpoint = properties.getLogs().getEndpoint();
    if (endpoint == null) {
      endpoint = properties.getEndpoint();
    }
    if (endpoint != null) {
      builder.setEndpoint(endpoint);
    }

    Duration timeout = properties.getLogs().getTimeout();
    if (timeout == null) {
      timeout = properties.getTimeout();
    }
    if (timeout != null) {
      builder.setTimeout(timeout);
    }

    return builder.build();
  }
}
