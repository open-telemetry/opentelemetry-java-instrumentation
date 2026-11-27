/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.thread;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.OtelEnabled;
import io.opentelemetry.instrumentation.thread.internal.ThreadDetailsSpanProcessor;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
@Conditional(OtelEnabled.class)
@ConditionalOnBean(OpenTelemetry.class)
@ConditionalOnProperty(name = "otel.spring-starter.capture-thread-attributes", havingValue = "true")
@Configuration
public class ThreadDetailsAutoConfiguration {

  @Bean
  public AutoConfigurationCustomizerProvider threadDetailOtelCustomizer() {
    return p ->
        p.addTracerProviderCustomizer((builder, config) -> {
          builder.addSpanProcessor(new ThreadDetailsSpanProcessor());
          return builder;
        });
  }
}
