/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.exporters.otlp;

import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter;
import io.opentelemetry.exporter.otlp.internal.OtlpMetricExporterProvider;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.instrumentation.spring.autoconfigure.OpenTelemetryAutoConfiguration;
import io.opentelemetry.instrumentation.spring.autoconfigure.exporters.internal.ExporterConfigEvaluator;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.SdkEnabled;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;

@Configuration
@AutoConfigureBefore(OpenTelemetryAutoConfiguration.class)
@Conditional({OtlpMetricExporterAutoConfiguration.CustomCondition.class, SdkEnabled.class})
@ConditionalOnClass(OtlpGrpcMetricExporter.class)
public class OtlpMetricExporterAutoConfiguration {

  @Bean(destroyMethod = "") // SDK components are shutdown from the OpenTelemetry instance
  @ConditionalOnMissingBean({OtlpGrpcMetricExporter.class, OtlpHttpMetricExporter.class})
  public MetricExporter otelOtlpMetricExporter(ConfigProperties configProperties) {
    return new OtlpMetricExporterProvider().createExporter(configProperties);
  }

  static final class CustomCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
      return ExporterConfigEvaluator.isExporterEnabled(
          context.getEnvironment(), "otel.metrics.exporter", "otlp", true);
    }
  }
}
