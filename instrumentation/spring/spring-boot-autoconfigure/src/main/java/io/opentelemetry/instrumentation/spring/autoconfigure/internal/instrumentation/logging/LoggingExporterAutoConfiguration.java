/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.logging;

import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.instrumentation.logging.LoggingSpanExporterConfigurer;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.SdkEnabled;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
@Conditional(SdkEnabled.class)
// for backward compatibility with declarative configuration
@ConditionalOnProperty(name = "otel.log_level", havingValue = "debug")
@ConditionalOnClass(LoggingSpanExporter.class)
@Configuration
public class LoggingExporterAutoConfiguration {

  @Bean
  public AutoConfigurationCustomizerProvider loggingOtelCustomizer() {
    return p ->
        p.addTracerProviderCustomizer(
            (builder, config) -> {
              LoggingSpanExporterConfigurer.enableLoggingExporter(builder, config);
              return builder;
            });
  }
}
