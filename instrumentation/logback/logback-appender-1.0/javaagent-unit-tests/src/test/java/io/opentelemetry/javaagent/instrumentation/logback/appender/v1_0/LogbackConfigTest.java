/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.logback.appender.v1_0;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class LogbackConfigTest {

  @AfterEach
  void clearWarnings() throws ReflectiveOperationException {
    Field field = LogbackConfig.class.getDeclaredField("warnings");
    field.setAccessible(true);
    ((Set<?>) field.get(null)).clear();
  }

  @Test
  void readsExcludedOnlySelector() {
    DeclarativeConfigProperties config = mockConfig();
    when(config.get("mdc_attributes/development").getScalarList("excluded", String.class))
        .thenReturn(singletonList("*-secret"));

    Predicate<String> mdcAttributes = new LogbackConfig(config).getMdcAttributes();

    assertThat(mdcAttributes).isNotNull();
    assertThat(mdcAttributes.test("request-id")).isTrue();
    assertThat(mdcAttributes.test("request-secret")).isFalse();
  }

  @Test
  void readsGlobPatterns() {
    DeclarativeConfigProperties config = mockConfig();
    when(config.get("mdc_attributes/development").getScalarList("included", String.class))
        .thenReturn(asList("request-*", "user-?"));

    Predicate<String> mdcAttributes = new LogbackConfig(config).getMdcAttributes();

    assertThat(mdcAttributes).isNotNull();
    assertThat(mdcAttributes.test("request-id")).isTrue();
    assertThat(mdcAttributes.test("user-1")).isTrue();
    assertThat(mdcAttributes.test("user-name")).isFalse();
  }

  @Test
  void emptySelectorSelectsNothing() {
    assertThat(new LogbackConfig(mockConfig()).getMdcAttributes()).isNull();
  }

  @Test
  void newSelectorTakesPrecedenceOverDeprecatedConfigAndWarnsOnce() {
    DeclarativeConfigProperties config = mockConfig();
    when(config.get("mdc_attributes/development").getScalarList("included", String.class))
        .thenReturn(singletonList("new"));
    when(config.getScalarList("capture_mdc_attributes/development", String.class))
        .thenReturn(singletonList("deprecated"));
    Logger logger = Logger.getLogger(LogbackConfig.class.getName());
    TestHandler handler = new TestHandler();
    logger.addHandler(handler);
    try {
      Predicate<String> mdcAttributes = new LogbackConfig(config).getMdcAttributes();
      new LogbackConfig(config);

      assertThat(mdcAttributes).isNotNull();
      assertThat(mdcAttributes.test("new")).isTrue();
      assertThat(mdcAttributes.test("deprecated")).isFalse();
      assertThat(handler.records).hasSize(1);
      assertThat(handler.records.get(0).getMessage())
          .isEqualTo(
              "The otel.instrumentation.logback-appender.experimental.capture-mdc-attributes"
                  + " setting and the equivalent declarative configuration property are deprecated"
                  + " and ignored because"
                  + " otel.instrumentation.logback-appender.experimental.mdc-attributes.included or"
                  + " otel.instrumentation.logback-appender.experimental.mdc-attributes.excluded is"
                  + " configured. They may be removed in the next minor release.");
    } finally {
      logger.removeHandler(handler);
    }
  }

  @Test
  void deprecatedConfigSelectsKeysLiterallyAndWarnsOnce() {
    DeclarativeConfigProperties config = mockConfig();
    when(config.getScalarList("capture_mdc_attributes/development", String.class))
        .thenReturn(asList("*", "key?", "userId"));
    Logger logger = Logger.getLogger(LogbackConfig.class.getName());
    TestHandler handler = new TestHandler();
    logger.addHandler(handler);
    try {
      Predicate<String> mdcAttributes = new LogbackConfig(config).getMdcAttributes();
      new LogbackConfig(config);

      assertThat(mdcAttributes).isNotNull();
      assertThat(mdcAttributes.test("*")).isTrue();
      assertThat(mdcAttributes.test("key?")).isTrue();
      assertThat(mdcAttributes.test("userId")).isTrue();
      assertThat(mdcAttributes.test("key1")).isFalse();
      assertThat(mdcAttributes.test("anything")).isFalse();
      assertThat(handler.records).hasSize(1);
      assertThat(handler.records.get(0).getMessage())
          .isEqualTo(
              "The otel.instrumentation.logback-appender.experimental.capture-mdc-attributes"
                  + " setting and the equivalent declarative configuration property are"
                  + " deprecated and may be removed in the next minor release. Use"
                  + " otel.instrumentation.logback-appender.experimental.mdc-attributes.included"
                  + " or equivalent declarative configuration instead.");
    } finally {
      logger.removeHandler(handler);
    }
  }

  @Test
  void deprecatedConfigSelectsEverythingWithSoleWildcard() {
    DeclarativeConfigProperties config = mockConfig();
    when(config.getScalarList("capture_mdc_attributes/development", String.class))
        .thenReturn(singletonList("*"));

    Predicate<String> mdcAttributes = new LogbackConfig(config).getMdcAttributes();

    assertThat(mdcAttributes).isNotNull();
    assertThat(mdcAttributes.test("anything")).isTrue();
  }

  private static DeclarativeConfigProperties mockConfig() {
    DeclarativeConfigProperties config =
        mock(DeclarativeConfigProperties.class, RETURNS_DEEP_STUBS);
    when(config.get("mdc_attributes/development").getScalarList("included", String.class))
        .thenReturn(null);
    when(config.get("mdc_attributes/development").getScalarList("excluded", String.class))
        .thenReturn(null);
    when(config.getScalarList("capture_mdc_attributes/development", String.class)).thenReturn(null);
    return config;
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
