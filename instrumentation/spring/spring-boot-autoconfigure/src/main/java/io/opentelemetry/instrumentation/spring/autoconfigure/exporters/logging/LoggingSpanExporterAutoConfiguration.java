/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.exporters.logging;

import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.instrumentation.spring.autoconfigure.OpenTelemetryAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Configures {@link LoggingSpanExporter} bean for tracing. */
@Configuration
@EnableConfigurationProperties(LoggingSpanExporterProperties.class)
@AutoConfigureBefore(OpenTelemetryAutoConfiguration.class)
@ConditionalOnProperty(prefix = "otel.exporter.logging", name = "enabled", matchIfMissing = true)
@ConditionalOnClass(LoggingSpanExporter.class)
public class LoggingSpanExporterAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public LoggingSpanExporter otelLoggingSpanExporter() {
    return LoggingSpanExporter.create();
  }
}
