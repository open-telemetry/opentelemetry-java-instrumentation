/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.logging;

import static java.util.Collections.emptyList;

import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.OtelEnabled;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
@Conditional(OtelEnabled.class)
// to match "otel.javaagent.debug" system property
@ConditionalOnProperty(name = "otel.spring-starter.debug", havingValue = "true")
@ConditionalOnClass(LoggingSpanExporter.class)
@Configuration
public class LoggingExporterAutoConfiguration {

  @Bean
  public AutoConfigurationCustomizerProvider loggingOtelCustomizer() {
    return p ->
        p.addTracerProviderCustomizer(
            (builder, config) -> {
              enableLoggingExporter(builder, config);
              return builder;
            });
  }

  public static void enableLoggingExporter(
      SdkTracerProviderBuilder builder, ConfigProperties config) {
    // don't install another instance if the user has already explicitly requested it.
    if (loggingExporterIsNotAlreadyConfigured(config)) {
      builder.addSpanProcessor(SimpleSpanProcessor.create(LoggingSpanExporter.create()));
    }
  }

  private static boolean loggingExporterIsNotAlreadyConfigured(ConfigProperties config) {
    return !config.getList("otel.traces.exporter", emptyList()).contains("logging");
  }
}
