/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.exporters.jaeger;

import io.grpc.ManagedChannel;
import io.opentelemetry.exporter.jaeger.JaegerGrpcSpanExporter;
import io.opentelemetry.exporter.jaeger.JaegerGrpcSpanExporterBuilder;
import io.opentelemetry.instrumentation.spring.autoconfigure.TracerAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures {@link JaegerGrpcSpanExporter} for tracing.
 *
 * <p>Initializes {@link JaegerGrpcSpanExporter} bean if bean is missing.
 */
@Configuration
@AutoConfigureBefore(TracerAutoConfiguration.class)
@EnableConfigurationProperties(JaegerSpanExporterProperties.class)
@ConditionalOnProperty(
    prefix = "opentelemetry.trace.exporter.jaeger",
    name = "enabled",
    matchIfMissing = true)
@ConditionalOnClass({JaegerGrpcSpanExporter.class, ManagedChannel.class})
public class JaegerSpanExporterAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public JaegerGrpcSpanExporter otelJaegerSpanExporter(
      JaegerSpanExporterProperties jaegerSpanExporterProperties) {

    JaegerGrpcSpanExporterBuilder builder = JaegerGrpcSpanExporter.builder();
    if (jaegerSpanExporterProperties.getEndpoint() != null) {
      builder.setEndpoint(jaegerSpanExporterProperties.getEndpoint());
    }
    if (jaegerSpanExporterProperties.getSpanTimeout() != null) {
      builder.setTimeout(jaegerSpanExporterProperties.getSpanTimeout());
    }
    return builder.build();
  }
}
