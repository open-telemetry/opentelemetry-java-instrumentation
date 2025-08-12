/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.scheduling;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.ConditionalOnEnabledInstrumentation;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
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
@ConditionalOnBean(OpenTelemetry.class)
@ConditionalOnEnabledInstrumentation(module = "spring-scheduling")
@ConditionalOnClass({Scheduled.class, Aspect.class})
@Configuration
class SpringSchedulingInstrumentationAutoConfiguration {
  @Bean
  SpringSchedulingInstrumentationAspect springSchedulingInstrumentationAspect(
      OpenTelemetry openTelemetry, ConfigProperties configProperties) {
    return new SpringSchedulingInstrumentationAspect(openTelemetry, configProperties);
  }
}
