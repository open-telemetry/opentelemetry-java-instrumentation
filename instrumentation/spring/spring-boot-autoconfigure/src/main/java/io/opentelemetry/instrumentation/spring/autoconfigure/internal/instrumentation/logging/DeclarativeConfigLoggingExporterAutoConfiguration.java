/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.logging;

import io.opentelemetry.common.ComponentLoader;
import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.instrumentation.logging.internal.AbstractSpanLoggingCustomizerProvider;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.OtelEnabled;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.SpringConfigProvider;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.DeclarativeConfigurationCustomizerProvider;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.OpenTelemetryConfigurationModel;
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
    // Spring supplies every scalar as a String, so the flag has to be read through
    // SpringConfigProvider, which coerces. The plain SdkConfigProvider does not, and returned
    // false for a configured debug: true.
    private static final ComponentLoader componentLoader =
        ComponentLoader.forClassLoader(SpanLoggingCustomizerProvider.class.getClassLoader());

    @Override
    protected boolean isEnabled(OpenTelemetryConfigurationModel model) {
      return SpringConfigProvider.create(model, componentLoader)
          .getInstrumentationConfig("spring_starter")
          .getBoolean("debug", false);
    }
  }
}
