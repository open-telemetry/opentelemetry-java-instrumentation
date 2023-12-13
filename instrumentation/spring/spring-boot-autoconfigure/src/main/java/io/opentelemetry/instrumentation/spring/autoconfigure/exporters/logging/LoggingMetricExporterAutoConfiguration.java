/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.exporters.logging;

import io.opentelemetry.exporter.logging.LoggingMetricExporter;
import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.instrumentation.spring.autoconfigure.OpenTelemetryAutoConfiguration;
import io.opentelemetry.instrumentation.spring.autoconfigure.exporters.internal.ExporterUtil;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

/** Configures {@link LoggingSpanExporter} bean for tracing. */
@Configuration
@EnableConfigurationProperties(LoggingExporterProperties.class)
@AutoConfigureBefore(OpenTelemetryAutoConfiguration.class)
@Conditional(LoggingMetricExporterAutoConfiguration.CustomCondition.class)
@ConditionalOnClass(LoggingMetricExporter.class)
public class LoggingMetricExporterAutoConfiguration {

  @Bean
  public LoggingMetricExporter otelLoggingMetricExporter() {
    return LoggingMetricExporter.create();
  }

  static final class CustomCondition implements Condition {
    @Override
    public boolean matches(
        org.springframework.context.annotation.ConditionContext context,
        org.springframework.core.type.AnnotatedTypeMetadata metadata) {
      return ExporterUtil.isExporterEnabled(
          context.getEnvironment(),
          "otel.exporter.logging.enabled",
          "otel.exporter.logging.metrics.enabled",
          "otel.metrics.exporter",
          "logging",
          false);
    }
  }
}
