/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.annotations;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.ConditionalOnEnabledInstrumentation;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;

/**
 * Configures {@link WithSpanAspect} to trace bean methods annotated with {@link WithSpan}.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
@ConditionalOnEnabledInstrumentation(module = "annotations")
@ConditionalOnClass(Aspect.class)
@Configuration
public class InstrumentationAnnotationsAutoConfiguration {
  private final ParameterNameDiscoverer parameterNameDiscoverer =
      new DefaultParameterNameDiscoverer();

  @Bean
  @ConditionalOnClass(WithSpan.class)
  InstrumentationWithSpanAspect otelInstrumentationWithSpanAspect(OpenTelemetry openTelemetry) {
    return new InstrumentationWithSpanAspect(openTelemetry, parameterNameDiscoverer);
  }
}
