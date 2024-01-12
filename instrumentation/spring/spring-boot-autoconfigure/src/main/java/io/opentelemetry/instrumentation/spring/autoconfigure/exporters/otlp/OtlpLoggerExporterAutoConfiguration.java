/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.exporters.otlp;

import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter;
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporterBuilder;
import io.opentelemetry.exporter.otlp.internal.OtlpConfigUtil;
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter;
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporterBuilder;
import io.opentelemetry.instrumentation.spring.autoconfigure.OpenTelemetryAutoConfiguration;
import io.opentelemetry.instrumentation.spring.autoconfigure.exporters.internal.ExporterConfigEvaluator;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.Conditional;

@AutoConfigureBefore(OpenTelemetryAutoConfiguration.class)
@EnableConfigurationProperties(OtlpExporterProperties.class)
@Conditional(OtlpLoggerExporterAutoConfiguration.CustomCondition.class)
@ConditionalOnClass(OtlpGrpcLogRecordExporter.class)
public class OtlpLoggerExporterAutoConfiguration {

  @Bean(destroyMethod = "")
  @ConditionalOnMissingBean({OtlpGrpcLogRecordExporter.class, OtlpHttpLogRecordExporter.class})
  public LogRecordExporter otelOtlpLogRecordExporter(OtlpExporterProperties properties) {

    return OtlpExporterUtil.applySignalProperties(
        OtlpConfigUtil.DATA_TYPE_LOGS,
        properties,
        properties.getLogs(),
        OtlpGrpcLogRecordExporter::builder,
        OtlpHttpLogRecordExporter::builder,
        OtlpGrpcLogRecordExporterBuilder::setEndpoint,
        OtlpHttpLogRecordExporterBuilder::setEndpoint,
        (builder, entry) -> {
          builder.addHeader(entry.getKey(), entry.getValue());
        },
        (builder, entry) -> {
          builder.addHeader(entry.getKey(), entry.getValue());
        },
        OtlpGrpcLogRecordExporterBuilder::setTimeout,
        OtlpHttpLogRecordExporterBuilder::setTimeout,
        OtlpGrpcLogRecordExporterBuilder::build,
        OtlpHttpLogRecordExporterBuilder::build);
  }

  static final class CustomCondition implements Condition {
    @Override
    public boolean matches(
        org.springframework.context.annotation.ConditionContext context,
        org.springframework.core.type.AnnotatedTypeMetadata metadata) {
      return ExporterConfigEvaluator.isExporterEnabled(
          context.getEnvironment(),
          "otel.exporter.otlp.enabled",
          "otel.exporter.otlp.logs.enabled",
          "otel.logs.exporter",
          "otlp",
          true);
    }
  }
}
