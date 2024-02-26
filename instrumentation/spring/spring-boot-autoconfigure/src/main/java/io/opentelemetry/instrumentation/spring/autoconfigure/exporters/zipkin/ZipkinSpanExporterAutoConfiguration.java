/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.exporters.zipkin;

import io.opentelemetry.exporter.zipkin.ZipkinSpanExporter;
import io.opentelemetry.exporter.zipkin.ZipkinSpanExporterBuilder;
import io.opentelemetry.instrumentation.spring.autoconfigure.OpenTelemetryAutoConfiguration;
import io.opentelemetry.instrumentation.spring.autoconfigure.exporters.internal.ExporterConfigEvaluator;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.SdkEnabled;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Configures {@link ZipkinSpanExporter} for tracing.
 *
 * <p>Initializes {@link ZipkinSpanExporter} bean if bean is missing.
 */
@Configuration
@AutoConfigureBefore(OpenTelemetryAutoConfiguration.class)
@EnableConfigurationProperties(ZipkinSpanExporterProperties.class)
@Conditional({ZipkinSpanExporterAutoConfiguration.CustomCondition.class, SdkEnabled.class})
@ConditionalOnClass(ZipkinSpanExporter.class)
public class ZipkinSpanExporterAutoConfiguration {

  @Bean(destroyMethod = "") // SDK components are shutdown from the OpenTelemetry instance
  @ConditionalOnMissingBean
  public ZipkinSpanExporter otelZipkinSpanExporter(ZipkinSpanExporterProperties properties) {

    ZipkinSpanExporterBuilder builder = ZipkinSpanExporter.builder();
    if (properties.getEndpoint() != null) {
      builder.setEndpoint(properties.getEndpoint());
    }
    return builder.build();
  }

  static final class CustomCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
      return ExporterConfigEvaluator.isExporterEnabled(
          context.getEnvironment(), "otel.traces.exporter", "zipkin", true);
    }
  }
}
