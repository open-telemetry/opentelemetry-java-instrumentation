/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.logging;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.log4j.appender.v2_17.OpenTelemetryAppender;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.StringMapMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junitpioneer.jupiter.SetSystemProperty;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@SetSystemProperty(
    key = "org.springframework.boot.logging.LoggingSystem",
    value = "org.springframework.boot.logging.log4j2.Log4J2LoggingSystem")
class Log4jAppenderTest {

  private static final Logger logger = LogManager.getLogger("spring-log4j-appender-test");

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  @BeforeEach
  void resetAppender() {
    OpenTelemetryAppender.install(null);
  }

  @Test
  void capturesMapMessageAttributesByDefault() {
    startSpringApplication();
    testing.clearData();

    logger.info(new StringMapMessage().with("included", "captured").with("excluded", "captured"));

    testing.waitAndAssertLogRecords(
        logRecord ->
            assertThat(logRecord.actual().getAttributes().asMap())
                .containsOnly(
                    entry(stringKey("included"), "captured"),
                    entry(stringKey("excluded"), "captured")));
  }

  @Test
  void filtersMapMessageAttributesWithXmlSelector() {
    startSpringApplication();
    testing.clearData();

    LogManager.getLogger("spring-log4j-appender-selector-test")
        .info(
            new StringMapMessage()
                .with("request-id", "captured")
                .with("request-secret", "not captured")
                .with("other", "not captured"));

    testing.waitAndAssertLogRecords(
        logRecord ->
            assertThat(logRecord.actual().getAttributes().asMap())
                .containsOnly(entry(stringKey("request-id"), "captured")));
  }

  private static void startSpringApplication() {
    SpringApplication app =
        new SpringApplication(
            TestingOpenTelemetryConfiguration.class, OpenTelemetryAppenderAutoConfiguration.class);
    ConfigurableApplicationContext context = app.run();
    cleanup.deferCleanup(context);
  }

  @Configuration
  static class TestingOpenTelemetryConfiguration {

    @Bean
    OpenTelemetry openTelemetry() {
      return testing.getOpenTelemetry();
    }
  }
}
