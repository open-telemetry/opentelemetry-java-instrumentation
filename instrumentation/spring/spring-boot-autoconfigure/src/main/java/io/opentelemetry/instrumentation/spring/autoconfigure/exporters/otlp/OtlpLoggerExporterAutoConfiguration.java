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
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfigureBefore(OpenTelemetryAutoConfiguration.class)
@EnableConfigurationProperties(OtlpExporterProperties.class)
@ConditionalOnProperty(
    prefix = "otel.exporter.otlp",
    name = {"enabled", "logs.enabled"},
    matchIfMissing = true)
@ConditionalOnClass(OtlpGrpcLogRecordExporter.class)
public class OtlpLoggerExporterAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public LogRecordExporter otelOtlpGrpcLogRecordExporter(OtlpExporterProperties properties) {

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
}
