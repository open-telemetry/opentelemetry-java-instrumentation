/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.log4j.appender.v2_17;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class Log4jConfigTest {

  @BeforeEach
  void clearWarnings() throws Exception {
    Field field = Log4jConfig.class.getDeclaredField("warnedDeprecatedProperties");
    field.setAccessible(true);
    ((Set<?>) field.get(null)).clear();
  }

  @Test
  void readsNewSelector() {
    DeclarativeConfigProperties config = mockConfig();
    DeclarativeConfigProperties mdcAttributes = config.get("mdc_attributes/development");
    when(mdcAttributes.getScalarList("included", String.class))
        .thenReturn(asList("exact", "prefix.*", "single?"));
    when(mdcAttributes.getScalarList("excluded", String.class))
        .thenReturn(singletonList("prefix.secret"));

    Predicate<String> selector = new Log4jConfig(config).getContextDataAttributes();

    assertThat(selector).isNotNull();
    assertThat(selector.test("exact")).isTrue();
    assertThat(selector.test("prefix.value")).isTrue();
    assertThat(selector.test("single1")).isTrue();
    assertThat(selector.test("single22")).isFalse();
    assertThat(selector.test("prefix.secret")).isFalse();
    assertThat(selector.test("other")).isFalse();
  }

  @Test
  void excludeOnlySelectorSelectsAllExceptExclusions() {
    DeclarativeConfigProperties config = mockConfig();
    when(config.get("mdc_attributes/development").getScalarList("excluded", String.class))
        .thenReturn(singletonList("secret*"));

    Predicate<String> selector = new Log4jConfig(config).getContextDataAttributes();

    assertThat(selector).isNotNull();
    assertThat(selector.test("public")).isTrue();
    assertThat(selector.test("secret-token")).isFalse();
  }

  @Test
  void absentAndEmptySelectorsCaptureNothing() {
    DeclarativeConfigProperties absent = mockConfig();
    DeclarativeConfigProperties empty = mockConfig();
    when(empty.get("mdc_attributes/development").getScalarList("included", String.class))
        .thenReturn(emptyList());
    when(empty.get("mdc_attributes/development").getScalarList("excluded", String.class))
        .thenReturn(emptyList());

    assertThat(new Log4jConfig(absent).getContextDataAttributes()).isNull();
    assertThat(new Log4jConfig(empty).getContextDataAttributes()).isNull();
  }

  @Test
  void deprecatedConfigMatchesKeysLiterallyAndWarnsOnce() {
    DeclarativeConfigProperties config = mockConfig();
    when(config.getScalarList("capture_mdc_attributes/development", String.class))
        .thenReturn(asList("literal?", "embedded*", "*"));
    TestHandler handler = attachWarningHandler();
    try {
      Predicate<String> first = new Log4jConfig(config).getContextDataAttributes();
      Predicate<String> second = new Log4jConfig(config).getContextDataAttributes();

      assertThat(first).isNotNull();
      assertThat(first.test("literal?")).isTrue();
      assertThat(first.test("literal1")).isFalse();
      assertThat(first.test("embedded*")).isTrue();
      assertThat(first.test("embedded-value")).isFalse();
      assertThat(first.test("*")).isTrue();
      assertThat(first.test("other")).isFalse();
      assertThat(second.test("other")).isFalse();
      assertThat(handler.records).hasSize(1);
      assertThat(handler.records.get(0).getMessage())
          .contains("capture-mdc-attributes", "deprecated", "mdc-attributes.included");
    } finally {
      detachWarningHandler(handler);
    }
  }

  @Test
  void deprecatedConfigLoneWildcardCapturesAll() {
    DeclarativeConfigProperties config = mockConfig();
    when(config.getScalarList("capture_mdc_attributes/development", String.class))
        .thenReturn(singletonList("*"));

    Predicate<String> selector = new Log4jConfig(config).getContextDataAttributes();

    assertThat(selector).isNotNull();
    assertThat(selector.test("literal?")).isTrue();
    assertThat(selector.test("embedded-value")).isTrue();
  }

  @Test
  void deprecatedConfigEmptyListCapturesNothing() {
    DeclarativeConfigProperties config = mockConfig();
    when(config.getScalarList("capture_mdc_attributes/development", String.class))
        .thenReturn(emptyList());

    assertThat(new Log4jConfig(config).getContextDataAttributes()).isNull();
  }

  @Test
  void newSelectorTakesPrecedenceAndWarnsOnce() {
    DeclarativeConfigProperties config = mockConfig();
    when(config.get("mdc_attributes/development").getScalarList("included", String.class))
        .thenReturn(singletonList("new"));
    when(config.getScalarList("capture_mdc_attributes/development", String.class))
        .thenReturn(singletonList("legacy"));
    TestHandler handler = attachWarningHandler();
    try {
      Predicate<String> first = new Log4jConfig(config).getContextDataAttributes();
      Predicate<String> second = new Log4jConfig(config).getContextDataAttributes();

      assertThat(first).isNotNull();
      assertThat(first.test("new")).isTrue();
      assertThat(first.test("legacy")).isFalse();
      assertThat(second.test("new")).isTrue();
      assertThat(handler.records).hasSize(1);
      assertThat(handler.records.get(0).getMessage())
          .contains("capture-mdc-attributes", "ignored", "mdc-attributes.excluded");
    } finally {
      detachWarningHandler(handler);
    }
  }

  private static DeclarativeConfigProperties mockConfig() {
    DeclarativeConfigProperties config =
        mock(DeclarativeConfigProperties.class, RETURNS_DEEP_STUBS);
    DeclarativeConfigProperties mdcAttributes = config.get("mdc_attributes/development");
    when(mdcAttributes.getScalarList("included", String.class)).thenReturn(null);
    when(mdcAttributes.getScalarList("excluded", String.class)).thenReturn(null);
    when(config.getScalarList("capture_mdc_attributes/development", String.class)).thenReturn(null);
    return config;
  }

  private static TestHandler attachWarningHandler() {
    TestHandler handler = new TestHandler();
    Logger.getLogger(Log4jConfig.class.getName()).addHandler(handler);
    return handler;
  }

  private static void detachWarningHandler(TestHandler handler) {
    Logger.getLogger(Log4jConfig.class.getName()).removeHandler(handler);
  }

  private static class TestHandler extends Handler {

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
