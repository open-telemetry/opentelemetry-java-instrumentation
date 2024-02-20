/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.instrumentation.annotations;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.autoconfigure.condition.AllNestedConditions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;

/** Configures {@link WithSpanAspect} to trace bean methods annotated with {@link WithSpan}. */
@ConditionalOnBean(OpenTelemetry.class)
@ConditionalOnClass(Aspect.class)
@Conditional(InstrumentationAnnotationsAutoConfiguration.Condition.class)
@Configuration
public class InstrumentationAnnotationsAutoConfiguration {

  static final class Condition extends AllNestedConditions {
    public Condition() {
      super(ConfigurationPhase.PARSE_CONFIGURATION);
    }

    @ConditionalOnProperty(name = "otel.instrumentation.annotations.enabled", matchIfMissing = true)
    static class Annotation {}

    @ConditionalOnProperty(name = "otel.sdk.disabled", havingValue = "false", matchIfMissing = true)
    static class SdkEnabled {}
  }

  private final ParameterNameDiscoverer parameterNameDiscoverer =
      new DefaultParameterNameDiscoverer();

  @Bean
  @ConditionalOnClass(WithSpan.class)
  InstrumentationWithSpanAspect otelInstrumentationWithSpanAspect(OpenTelemetry openTelemetry) {
    return new InstrumentationWithSpanAspect(openTelemetry, parameterNameDiscoverer);
  }
}
