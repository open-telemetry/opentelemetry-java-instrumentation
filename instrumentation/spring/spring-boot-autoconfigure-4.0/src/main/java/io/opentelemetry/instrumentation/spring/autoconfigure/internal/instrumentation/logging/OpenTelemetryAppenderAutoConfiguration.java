/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.logging;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.ConditionalOnEnabledInstrumentation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
@Configuration
@SuppressWarnings("OtelPrivateConstructorForUtilityClass")
public class OpenTelemetryAppenderAutoConfiguration {

  @ConditionalOnEnabledInstrumentation(module = "log4j-appender")
  @ConditionalOnClass(org.apache.logging.log4j.core.LoggerContext.class)
  @Configuration
  static class Log4jAppenderConfig {

    @Bean
    ApplicationListener<ApplicationReadyEvent> log4jOtelAppenderInitializer(
        OpenTelemetry openTelemetry) {
      return event -> {
        io.opentelemetry.instrumentation.log4j.appender.v2_17.OpenTelemetryAppender.install(
            openTelemetry);
      };
    }
  }

  @ConditionalOnEnabledInstrumentation(module = "logback-appender")
  @ConditionalOnClass(ch.qos.logback.classic.LoggerContext.class)
  @Configuration
  static class LogbackAppenderConfig {

    @Bean
    ApplicationListener<ApplicationReadyEvent> logbackOtelAppenderInitializer(
        OpenTelemetry openTelemetry) {
      return event -> {
        io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender.install(
            openTelemetry);
      };
    }
  }
}
