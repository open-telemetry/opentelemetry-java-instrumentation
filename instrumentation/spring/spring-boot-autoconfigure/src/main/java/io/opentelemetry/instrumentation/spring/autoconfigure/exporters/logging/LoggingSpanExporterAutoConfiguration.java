/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.exporters.logging;

import io.opentelemetry.exporters.logging.LoggingSpanExporter;
import io.opentelemetry.instrumentation.spring.autoconfigure.TracerAutoConfiguration;
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
@AutoConfigureBefore(TracerAutoConfiguration.class)
@ConditionalOnProperty(
    prefix = "opentelemetry.trace.exporter.logging",
    name = "enabled",
    matchIfMissing = true)
@ConditionalOnClass(LoggingSpanExporter.class)
public class LoggingSpanExporterAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public LoggingSpanExporter otelLoggingSpanExporter() {
    return new LoggingSpanExporter();
  }
}
