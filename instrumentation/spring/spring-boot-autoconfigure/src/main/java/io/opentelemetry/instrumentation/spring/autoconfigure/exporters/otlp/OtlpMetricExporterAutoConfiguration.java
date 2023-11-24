/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.exporters.otlp;

import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter;
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporterBuilder;
import io.opentelemetry.exporter.otlp.internal.OtlpConfigUtil;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporterBuilder;
import io.opentelemetry.instrumentation.spring.autoconfigure.OpenTelemetryAutoConfiguration;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@AutoConfigureBefore(OpenTelemetryAutoConfiguration.class)
@EnableConfigurationProperties(OtlpExporterProperties.class)
@ConditionalOnProperty(
    prefix = "otel.exporter.otlp",
    name = {"enabled", "metrics.enabled"},
    matchIfMissing = true)
@ConditionalOnClass(OtlpGrpcMetricExporter.class)
public class OtlpMetricExporterAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public MetricExporter otelOtlpGrpcMetricExporter(OtlpExporterProperties properties) {
    return OtlpExporterUtil.applySignalProperties(
        OtlpConfigUtil.DATA_TYPE_METRICS,
        properties,
        properties.getLogs(),
        OtlpGrpcMetricExporter::builder,
        OtlpHttpMetricExporter::builder,
        OtlpGrpcMetricExporterBuilder::setEndpoint,
        OtlpHttpMetricExporterBuilder::setEndpoint,
        (builder, entry) -> {
          builder.addHeader(entry.getKey(), entry.getValue());
        },
        (builder, entry) -> {
          builder.addHeader(entry.getKey(), entry.getValue());
        },
        OtlpGrpcMetricExporterBuilder::setTimeout,
        OtlpHttpMetricExporterBuilder::setTimeout,
        OtlpGrpcMetricExporterBuilder::build,
        OtlpHttpMetricExporterBuilder::build);
  }
}
