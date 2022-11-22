/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.exporters.logging;

import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.instrumentation.spring.autoconfigure.OpenTelemetryAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

/** Configures {@link LoggingSpanExporter} bean for tracing. */
@Configuration
@EnableConfigurationProperties(LoggingExporterProperties.class)
@AutoConfigureBefore(OpenTelemetryAutoConfiguration.class)
@Conditional(LoggingSpanExporterAutoConfiguration.AnyPropertyEnabled.class)
@ConditionalOnClass(LoggingSpanExporter.class)
public class LoggingSpanExporterAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public LoggingSpanExporter otelLoggingSpanExporter() {
    return LoggingSpanExporter.create();
  }

  static final class AnyPropertyEnabled extends AnyNestedCondition {

    AnyPropertyEnabled() {
      super(ConfigurationPhase.PARSE_CONFIGURATION);
    }

    @ConditionalOnProperty("otel.exporter.logging.enabled")
    static class LoggingEnabled {}

    @ConditionalOnProperty("otel.exporter.logging.traces.enabled")
    static class LoggingTracesEnabled {}
  }
}
