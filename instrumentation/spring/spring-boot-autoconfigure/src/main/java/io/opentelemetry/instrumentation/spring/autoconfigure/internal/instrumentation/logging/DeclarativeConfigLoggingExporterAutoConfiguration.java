/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.logging;

import static io.opentelemetry.api.incubator.config.DeclarativeConfigProperties.empty;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.instrumentation.logging.internal.AbstractSpanLoggingCustomizerProvider;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.OtelEnabled;
import io.opentelemetry.sdk.extension.incubator.fileconfig.DeclarativeConfigurationCustomizerProvider;
import io.opentelemetry.sdk.extension.incubator.fileconfig.SdkConfigProvider;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.OpenTelemetryConfigurationModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
@Conditional(OtelEnabled.class)
@ConditionalOnClass(LoggingSpanExporter.class)
@Configuration
public class DeclarativeConfigLoggingExporterAutoConfiguration {
  @Bean
  public DeclarativeConfigurationCustomizerProvider spanLoggingCustomizerProvider() {
    return new SpanLoggingCustomizerProvider();
  }

  static class SpanLoggingCustomizerProvider extends AbstractSpanLoggingCustomizerProvider {
    @Override
    protected boolean isEnabled(OpenTelemetryConfigurationModel model) {
      DeclarativeConfigProperties instrumentation =
          SdkConfigProvider.create(model).getInstrumentationConfig();
      if (instrumentation == null) {
        return false;
      }

      return instrumentation
          .getStructured("java", empty())
          .getStructured("spring_starter", empty())
          .getBoolean("debug", false);
    }
  }
}
