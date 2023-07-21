/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.instrumentation.logging;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class LogbackAppenderTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  @BeforeEach
  void setUp() {
    // reset the appender
    OpenTelemetryAppender.install(null);
  }

  @Configuration
  static class TestingOpenTelemetryConfiguration {

    @Bean
    public OpenTelemetry openTelemetry() {
      return testing.getOpenTelemetry();
    }
  }

  @Test
  void shouldInitializeAppender() {
    Map<String, Object> properties = new HashMap<>();
    properties.put("logging.config", "classpath:logback-test.xml");

    SpringApplication app =
        new SpringApplication(
            TestingOpenTelemetryConfiguration.class, OpenTelemetryAppenderAutoConfiguration.class);
    app.setDefaultProperties(properties);
    ConfigurableApplicationContext context = app.run();
    cleanup.deferCleanup(context);

    LoggerFactory.getLogger("test").info("test log message");

    assertThat(testing.logRecords())
        .anySatisfy(
            logRecord -> {
              assertThat(logRecord.getInstrumentationScopeInfo().getName()).isEqualTo("test");
              assertThat(logRecord.getBody().asString()).contains("test log message");
            });
  }

  @Test
  void shouldNotInitializeAppenderWhenDisabled() {
    Map<String, Object> properties = new HashMap<>();
    properties.put("logging.config", "classpath:logback-test.xml");
    properties.put("otel.springboot.logback-appender.enabled", "false");

    SpringApplication app =
        new SpringApplication(
            TestingOpenTelemetryConfiguration.class, OpenTelemetryAppenderAutoConfiguration.class);
    app.setDefaultProperties(properties);
    ConfigurableApplicationContext context = app.run();
    cleanup.deferCleanup(context);

    LoggerFactory.getLogger("test").info("test log message");

    assertThat(testing.logRecords()).isEmpty();
  }
}
