/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.scheduling;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.ConditionalOnEnabledInstrumentation;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Configures an aspect for tracing.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
@ConditionalOnEnabledInstrumentation(module = "spring-scheduling")
@ConditionalOnClass({Scheduled.class, Aspect.class})
@Configuration
class SpringSchedulingInstrumentationAutoConfiguration {
  @Bean
  SpringSchedulingInstrumentationAspect springSchedulingInstrumentationAspect(
      OpenTelemetry openTelemetry) {
    return new SpringSchedulingInstrumentationAspect(openTelemetry);
  }
}
