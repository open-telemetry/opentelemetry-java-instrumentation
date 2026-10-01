/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.config.internal;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

@ResourceLock("SelectorConfig.logger")
class LoggingConfigTest {

  @Test
  void commonSelectorTakesPrecedence() {
    ExtendedOpenTelemetry openTelemetry = mockOpenTelemetry(false);
    DeclarativeConfigProperties loggingConfig =
        openTelemetry.getInstrumentationConfig("common").get("logging");
    when(loggingConfig.get("structured_attributes").getScalarList("included", String.class))
        .thenReturn(singletonList("key*"));
    when(loggingConfig.get("structured_attributes").getScalarList("excluded", String.class))
        .thenReturn(singletonList("key2"));
    DeclarativeConfigProperties sourceConfig =
        mock(DeclarativeConfigProperties.class, RETURNS_DEEP_STUBS);

    Predicate<String> selector =
        LoggingConfig.resolveStructuredAttributes(
            openTelemetry, sourceConfig, "logback-appender", "key-value-pair-attributes");

    assertThat(selector).isNotNull();
    assertThat(selector.test("key1")).isTrue();
    assertThat(selector.test("key2")).isFalse();
    assertThat(selector.test("other")).isFalse();
    verifyNoInteractions(sourceConfig);
  }

  @Test
  void v3PreviewDefaultsToAllForAbsentOrEmptySelector() {
    for (boolean empty : asList(false, true)) {
      ExtendedOpenTelemetry openTelemetry = mockOpenTelemetry(true);
      if (empty) {
        DeclarativeConfigProperties selectorConfig =
            openTelemetry
                .getInstrumentationConfig("common")
                .get("logging")
                .get("structured_attributes");
        when(selectorConfig.getScalarList("included", String.class)).thenReturn(emptyList());
        when(selectorConfig.getScalarList("excluded", String.class)).thenReturn(emptyList());
      }
      DeclarativeConfigProperties sourceConfig =
          mock(DeclarativeConfigProperties.class, RETURNS_DEEP_STUBS);

      Predicate<String> selector =
          LoggingConfig.resolveStructuredAttributes(
              openTelemetry, sourceConfig, "log4j-appender", "map-message-attributes");

      assertThat(selector).isNotNull();
      assertThat(selector.test("anything")).isTrue();
      verifyNoInteractions(sourceConfig);
    }
  }

  @Test
  void sourceSpecificSelectorRemainsFallbackOutsideV3Preview() {
    ExtendedOpenTelemetry openTelemetry = mockOpenTelemetry(false);
    DeclarativeConfigProperties sourceConfig =
        mock(DeclarativeConfigProperties.class, RETURNS_DEEP_STUBS);
    when(sourceConfig
            .get("map_message_attributes/development")
            .getScalarList("included", String.class))
        .thenReturn(singletonList("selected"));
    TestHandler handler = new TestHandler();
    Logger logger = Logger.getLogger(SelectorConfig.class.getName());
    logger.addHandler(handler);
    try {
      Predicate<String> first =
          LoggingConfig.resolveStructuredAttributes(
              openTelemetry, sourceConfig, "log4j-appender", "map-message-attributes");
      Predicate<String> second =
          LoggingConfig.resolveStructuredAttributes(
              openTelemetry, sourceConfig, "log4j-appender", "map-message-attributes");

      assertThat(first).isNotNull();
      assertThat(first.test("selected")).isTrue();
      assertThat(first.test("other")).isFalse();
      assertThat(second).isNotNull();
      assertThat(handler.records).hasSize(1);
      assertThat(handler.records.get(0).getMessage())
          .contains(
              "otel.instrumentation.log4j-appender.experimental"
                  + ".map-message-attributes.included",
              "otel.instrumentation.common.logging.structured-attributes.included");
    } finally {
      logger.removeHandler(handler);
    }
  }

  @Test
  void legacyBooleanRemainsFallbackOutsideV3Preview() {
    ExtendedOpenTelemetry openTelemetry = mockOpenTelemetry(false);
    DeclarativeConfigProperties sourceConfig =
        mock(DeclarativeConfigProperties.class, RETURNS_DEEP_STUBS);
    when(sourceConfig.getBoolean("capture_map_message_attributes/development")).thenReturn(true);
    TestHandler handler = new TestHandler();
    Logger logger = Logger.getLogger(SelectorConfig.class.getName());
    logger.addHandler(handler);
    try {
      Predicate<String> selector =
          LoggingConfig.resolveStructuredAttributes(
              openTelemetry, sourceConfig, "log4j-appender", "map-message-attributes");

      assertThat(selector).isNotNull();
      assertThat(selector.test("anything")).isTrue();
      assertThat(handler.records).hasSize(1);
      assertThat(handler.records.get(0).getMessage())
          .isEqualTo(
              "The otel.instrumentation.log4j-appender.experimental"
                  + ".capture-map-message-attributes setting and the equivalent declarative"
                  + " configuration property are deprecated and will be removed in 3.0. Use"
                  + " otel.instrumentation.common.logging.structured-attributes.included or"
                  + " otel.instrumentation.common.logging.structured-attributes.excluded or"
                  + " equivalent declarative configuration instead.");
    } finally {
      logger.removeHandler(handler);
    }
  }

  private static ExtendedOpenTelemetry mockOpenTelemetry(boolean v3Preview) {
    ExtendedOpenTelemetry openTelemetry = mock(ExtendedOpenTelemetry.class, RETURNS_DEEP_STUBS);
    DeclarativeConfigProperties commonConfig = openTelemetry.getInstrumentationConfig("common");
    when(commonConfig.getBoolean("v3_preview")).thenReturn(v3Preview);
    return openTelemetry;
  }

  private static final class TestHandler extends Handler {
    private final List<LogRecord> records = new ArrayList<>();

    @Override
    public void publish(LogRecord record) {
      records.add(record);
    }

    @Override
    public void flush() {}

    @Override
    public void close() {}
  }
}
