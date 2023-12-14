/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.exporters.jaeger;

import io.opentelemetry.instrumentation.spring.autoconfigure.OpenTelemetryAutoConfiguration;
import io.opentelemetry.instrumentation.spring.autoconfigure.exporters.internal.ExporterConfigEvaluator;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

/**
 * Configures {@link io.opentelemetry.exporter.jaeger.JaegerGrpcSpanExporter} for tracing.
 *
 * <p>Initializes {@link io.opentelemetry.exporter.jaeger.JaegerGrpcSpanExporter} bean if bean is
 * missing.
 */
@Configuration
@AutoConfigureBefore(OpenTelemetryAutoConfiguration.class)
@EnableConfigurationProperties(JaegerSpanExporterProperties.class)
@Conditional(JaegerSpanExporterAutoConfiguration.CustomCondition.class)
@ConditionalOnClass(io.opentelemetry.exporter.jaeger.JaegerGrpcSpanExporter.class)
@Deprecated
public class JaegerSpanExporterAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public io.opentelemetry.exporter.jaeger.JaegerGrpcSpanExporter otelJaegerSpanExporter(
      JaegerSpanExporterProperties properties) {

    io.opentelemetry.exporter.jaeger.JaegerGrpcSpanExporterBuilder builder =
        io.opentelemetry.exporter.jaeger.JaegerGrpcSpanExporter.builder();
    if (properties.getEndpoint() != null) {
      builder.setEndpoint(properties.getEndpoint());
    }
    if (properties.getTimeout() != null) {
      builder.setTimeout(properties.getTimeout());
    }
    return builder.build();
  }

  static final class CustomCondition implements Condition {
    @Override
    public boolean matches(
        org.springframework.context.annotation.ConditionContext context,
        org.springframework.core.type.AnnotatedTypeMetadata metadata) {
      return ExporterConfigEvaluator.isExporterEnabled(
          context.getEnvironment(),
          null,
          "otel.exporter.jaeger.enabled",
          "otel.traces.exporter",
          "jaeger",
          true);
    }
  }
}
