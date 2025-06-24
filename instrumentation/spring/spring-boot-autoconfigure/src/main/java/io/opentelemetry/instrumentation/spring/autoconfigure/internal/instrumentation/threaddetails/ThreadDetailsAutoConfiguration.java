/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.threaddetails;

import io.opentelemetry.instrumentation.spring.autoconfigure.internal.ConditionalOnEnabledInstrumentation;
import io.opentelemetry.instrumentation.tracer.AddThreadDetailsSpanProcessor;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
@ConditionalOnEnabledInstrumentation(module = "thread-details")
@Configuration
@SuppressWarnings("OtelPrivateConstructorForUtilityClass")
public class ThreadDetailsAutoConfiguration {

  @Bean
  public AutoConfigurationCustomizerProvider threadDetailOtelCustomizer() {
    return p ->
        p.addTracerProviderCustomizer(
            (builder, config) -> {
              builder.addSpanProcessor(new AddThreadDetailsSpanProcessor());
              return builder;
            });
  }
}
