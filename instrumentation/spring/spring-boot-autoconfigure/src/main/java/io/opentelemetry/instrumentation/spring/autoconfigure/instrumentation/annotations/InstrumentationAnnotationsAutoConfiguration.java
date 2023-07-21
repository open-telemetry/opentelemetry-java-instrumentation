/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.instrumentation.annotations;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;

/** Configures {@link WithSpanAspect} to trace bean methods annotated with {@link WithSpan}. */
@ConditionalOnBean(OpenTelemetry.class)
@ConditionalOnClass(Aspect.class)
@ConditionalOnProperty(name = "otel.instrumentation.annotations.enabled", matchIfMissing = true)
@Configuration
public class InstrumentationAnnotationsAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  ParameterNameDiscoverer parameterNameDiscoverer() {
    return new DefaultParameterNameDiscoverer();
  }

  @Bean
  @ConditionalOnClass(WithSpan.class)
  InstrumentationWithSpanAspect otelInstrumentationWithSpanAspect(
      OpenTelemetry openTelemetry, ParameterNameDiscoverer parameterNameDiscoverer) {
    return new InstrumentationWithSpanAspect(openTelemetry, parameterNameDiscoverer);
  }

  @Bean
  @SuppressWarnings("deprecation") // instrumenting deprecated class for backwards compatibility
  @ConditionalOnClass(io.opentelemetry.extension.annotations.WithSpan.class)
  SdkExtensionWithSpanAspect otelSdkExtensionWithSpanAspect(
      OpenTelemetry openTelemetry, ParameterNameDiscoverer parameterNameDiscoverer) {
    return new SdkExtensionWithSpanAspect(openTelemetry, parameterNameDiscoverer);
  }
}
