/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.exporters.logging;

import io.opentelemetry.exporter.logging.LoggingMetricExporter;
import io.opentelemetry.instrumentation.spring.autoconfigure.OpenTelemetryAutoConfiguration;
import io.opentelemetry.instrumentation.spring.autoconfigure.exporters.internal.ExporterConfigEvaluator;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.SdkEnabled;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;

/** Configures {@link LoggingMetricExporter} bean for tracing. */
@Configuration
@AutoConfigureBefore(OpenTelemetryAutoConfiguration.class)
@Conditional({LoggingMetricExporterAutoConfiguration.CustomCondition.class, SdkEnabled.class})
@ConditionalOnClass(LoggingMetricExporter.class)
public class LoggingMetricExporterAutoConfiguration {

  @Bean(destroyMethod = "") // SDK components are shutdown from the OpenTelemetry instance
  @ConditionalOnMissingBean
  public LoggingMetricExporter otelLoggingMetricExporter() {
    return LoggingMetricExporter.create();
  }

  static final class CustomCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
      return ExporterConfigEvaluator.isExporterEnabled(
          context.getEnvironment(), "otel.metrics.exporter", "logging", false);
    }
  }
}
