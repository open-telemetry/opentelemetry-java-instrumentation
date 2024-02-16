/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.exporters.otlp;

import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter;
import io.opentelemetry.exporter.otlp.internal.OtlpLogRecordExporterProvider;
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter;
import io.opentelemetry.instrumentation.spring.autoconfigure.OpenTelemetryAutoConfiguration;
import io.opentelemetry.instrumentation.spring.autoconfigure.exporters.internal.ExporterConfigEvaluator;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.type.AnnotatedTypeMetadata;

@AutoConfigureBefore(OpenTelemetryAutoConfiguration.class)
@Conditional(OtlpLogRecordExporterAutoConfiguration.CustomCondition.class)
@ConditionalOnClass(OtlpGrpcLogRecordExporter.class)
public class OtlpLogRecordExporterAutoConfiguration {

  @Bean(destroyMethod = "") // SDK components are shutdown from the OpenTelemetry instance
  @ConditionalOnMissingBean({OtlpGrpcLogRecordExporter.class, OtlpHttpLogRecordExporter.class})
  public LogRecordExporter otelOtlpLogRecordExporter(ConfigProperties configProperties) {
    return new OtlpLogRecordExporterProvider().createExporter(configProperties);
  }

  static final class CustomCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
      return ExporterConfigEvaluator.isExporterEnabled(
          context.getEnvironment(), "otel.logs.exporter", "otlp", true);
    }
  }
}
