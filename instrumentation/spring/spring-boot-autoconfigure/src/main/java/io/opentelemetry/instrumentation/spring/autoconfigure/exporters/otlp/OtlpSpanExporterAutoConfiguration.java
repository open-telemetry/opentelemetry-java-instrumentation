/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.exporters.otlp;

import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporterBuilder;
import io.opentelemetry.exporter.otlp.internal.OtlpConfigUtil;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporterBuilder;
import io.opentelemetry.instrumentation.spring.autoconfigure.OpenTelemetryAutoConfiguration;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures {@link OtlpGrpcSpanExporter} for tracing.
 *
 * <p>Initializes {@link OtlpGrpcSpanExporter} bean if bean is missing.
 */
@Configuration
@AutoConfigureBefore(OpenTelemetryAutoConfiguration.class)
@EnableConfigurationProperties(OtlpExporterProperties.class)
@ConditionalOnProperty(
    prefix = "otel.exporter.otlp",
    name = {"enabled", "traces.enabled"},
    matchIfMissing = true)
@ConditionalOnClass(OtlpGrpcSpanExporter.class)
public class OtlpSpanExporterAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean({OtlpHttpSpanExporterBuilder.class})
  public OtlpHttpSpanExporterBuilder otelOtlpHttpSpanExporterBuilder() {
    // used for testing only - the builder is final
    return OtlpHttpSpanExporter.builder();
  }

  @Bean
  @ConditionalOnMissingBean({OtlpGrpcSpanExporter.class, OtlpHttpSpanExporter.class})
  public SpanExporter otelOtlpSpanExporter(
      OtlpExporterProperties properties, OtlpHttpSpanExporterBuilder otlpHttpSpanExporterBuilder) {
    return OtlpExporterUtil.applySignalProperties(
        OtlpConfigUtil.DATA_TYPE_TRACES,
        properties,
        properties.getLogs(),
        OtlpGrpcSpanExporter::builder,
        () -> otlpHttpSpanExporterBuilder,
        OtlpGrpcSpanExporterBuilder::setEndpoint,
        OtlpHttpSpanExporterBuilder::setEndpoint,
        (builder, entry) -> {
          builder.addHeader(entry.getKey(), entry.getValue());
        },
        (builder, entry) -> {
          builder.addHeader(entry.getKey(), entry.getValue());
        },
        OtlpGrpcSpanExporterBuilder::setTimeout,
        OtlpHttpSpanExporterBuilder::setTimeout,
        OtlpGrpcSpanExporterBuilder::build,
        OtlpHttpSpanExporterBuilder::build);
  }
}
