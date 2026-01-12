/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.thread;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.OtelEnabled;
import io.opentelemetry.instrumentation.thread.internal.AddThreadDetailsSpanProcessor;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
@ConditionalOnBean(OpenTelemetry.class)
@Conditional({OtelEnabled.class, ThreadDetailsAutoConfiguration.ThreadDetailsEnabled.class})
@Configuration
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

  static class ThreadDetailsEnabled implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
      Environment environment = context.getEnvironment();
      return environment.getProperty(
          "otel.instrumentation.common.thread-details.enabled", Boolean.class, false);
    }
  }
}
