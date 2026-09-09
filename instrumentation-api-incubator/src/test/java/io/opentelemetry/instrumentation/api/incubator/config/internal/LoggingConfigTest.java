/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.config.internal;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
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
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

@ResourceLock("LoggingConfig.logger")
class LoggingConfigTest {

  @ParameterizedTest
  @MethodSource("commonSelectors")
  void commonSelectorTakesPrecedence(
      boolean v3Preview,
      List<String> included,
      List<String> excluded,
      List<String> matching,
      List<String> notMatching) {
    ExtendedOpenTelemetry openTelemetry = openTelemetry(v3Preview);
    DeclarativeConfigProperties commonSelector =
        openTelemetry
            .getInstrumentationConfig("common")
            .get("logging")
            .get("structured_attributes");
    when(commonSelector.getScalarList("included", String.class)).thenReturn(included);
    when(commonSelector.getScalarList("excluded", String.class)).thenReturn(excluded);
    DeclarativeConfigProperties sourceConfig = mock(DeclarativeConfigProperties.class);

    Predicate<String> selector =
        LoggingConfig.resolveStructuredAttributes(
            openTelemetry, sourceConfig, "log4j-appender", "map-message-attributes");

    assertThat(selector).isNotNull();
    for (String name : matching) {
      assertThat(selector.test(name)).isTrue();
    }
    for (String name : notMatching) {
      assertThat(selector.test(name)).isFalse();
    }
    verifyNoInteractions(sourceConfig);
  }

  private static Stream<Arguments> commonSelectors() {
    return Stream.of(false, true)
        .flatMap(
            preview ->
                Stream.of(
                    argumentSet(
                        "included globs, preview=" + preview,
                        preview,
                        asList("exact", "public.*", "single?"),
                        singletonList("public.secret"),
                        asList("exact", "public.value", "single1"),
                        asList("EXACT", "other", "public.secret", "single22")),
                    argumentSet(
                        "exclude only, preview=" + preview,
                        preview,
                        null,
                        singletonList("secret*"),
                        asList("public", "Secret"),
                        asList("secret", "secret-token")),
                    argumentSet(
                        "exclude all, preview=" + preview,
                        preview,
                        null,
                        singletonList("*"),
                        emptyList(),
                        asList("public", "secret")),
                    argumentSet(
                        "exclusions override inclusions, preview=" + preview,
                        preview,
                        singletonList("*"),
                        singletonList("*"),
                        emptyList(),
                        asList("public", "secret")),
                    argumentSet(
                        "empty lists, preview=" + preview,
                        preview,
                        emptyList(),
                        emptyList(),
                        asList("public", "secret"),
                        emptyList()),
                    argumentSet(
                        "empty included, preview=" + preview,
                        preview,
                        emptyList(),
                        null,
                        asList("public", "secret"),
                        emptyList()),
                    argumentSet(
                        "empty excluded, preview=" + preview,
                        preview,
                        null,
                        emptyList(),
                        asList("public", "secret"),
                        emptyList())));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void emptyCommonSelectorObjectSelectsAll(boolean v3Preview) {
    ExtendedOpenTelemetry openTelemetry = openTelemetry(v3Preview);
    when(openTelemetry.getInstrumentationConfig("common").get("logging").getPropertyKeys())
        .thenReturn(singleton("structured_attributes"));
    DeclarativeConfigProperties sourceConfig = mock(DeclarativeConfigProperties.class);

    Predicate<String> selector =
        LoggingConfig.resolveStructuredAttributes(
            openTelemetry, sourceConfig, "log4j-appender", "map-message-attributes");

    assertThat(selector).isNotNull();
    assertThat(selector.test("any")).isTrue();
    verifyNoInteractions(sourceConfig);
  }

  @Test
  void absentCommonSelectorInV3SelectsAllWithoutReadingLegacyConfiguration() {
    DeclarativeConfigProperties sourceConfig = mock(DeclarativeConfigProperties.class);

    Predicate<String> selector =
        LoggingConfig.resolveStructuredAttributes(
            openTelemetry(true), sourceConfig, "log4j-appender", "map-message-attributes");

    assertThat(selector).isNotNull();
    assertThat(selector.test("any")).isTrue();
    verifyNoInteractions(sourceConfig);
  }

  @ParameterizedTest
  @CsvSource({
    "log4j-appender, map-message-attributes, map-message-attributes",
    "logback-appender, key-value-pair-attributes, key-value-pair-attributes",
    "logback-appender, logstash-marker-attributes, logstash-marker-attributes",
    "logback-appender, logstash-structured-argument-attributes, logstash-structured-arguments"
  })
  void legacySourceSelectorTakesPrecedenceOverBoolean(
      String instrumentationName, String selectorName, String deprecatedSelectorName) {
    DeclarativeConfigProperties sourceConfig = sourceConfig(selectorName, deprecatedSelectorName);
    DeclarativeConfigProperties sourceSelector =
        sourceConfig.get(selectorName.replace('-', '_') + "/development");
    when(sourceSelector.getScalarList("included", String.class))
        .thenReturn(singletonList("public.*"));
    when(sourceSelector.getScalarList("excluded", String.class))
        .thenReturn(singletonList("public.secret"));
    when(sourceConfig.getBoolean(
            "capture_" + deprecatedSelectorName.replace('-', '_') + "/development"))
        .thenReturn(true);

    Predicate<String> selector =
        LoggingConfig.resolveStructuredAttributes(
            openTelemetry(false),
            sourceConfig,
            instrumentationName,
            selectorName,
            deprecatedSelectorName);

    assertThat(selector).isNotNull();
    assertThat(selector.test("public.value")).isTrue();
    assertThat(selector.test("public.secret")).isFalse();
    assertThat(selector.test("other")).isFalse();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void absentAndEmptyLegacySelectorsPreserveSourceDefault(boolean emptySelector) {
    DeclarativeConfigProperties sourceConfig =
        sourceConfig("map-message-attributes", "map-message-attributes");
    if (emptySelector) {
      when(sourceConfig
              .get("map_message_attributes/development")
              .getScalarList("included", String.class))
          .thenReturn(emptyList());
      when(sourceConfig
              .get("map_message_attributes/development")
              .getScalarList("excluded", String.class))
          .thenReturn(emptyList());
    }

    assertThat(
            LoggingConfig.resolveStructuredAttributes(
                openTelemetry(false), sourceConfig, "log4j-appender", "map-message-attributes"))
        .isNull();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void legacyBooleanPreservesV2Behavior(boolean capture) {
    DeclarativeConfigProperties sourceConfig =
        sourceConfig("logstash-structured-argument-attributes", "logstash-structured-arguments");
    when(sourceConfig.getBoolean("capture_logstash_structured_arguments/development"))
        .thenReturn(capture);

    Predicate<String> selector =
        LoggingConfig.resolveStructuredAttributes(
            openTelemetry(false),
            sourceConfig,
            "logback-appender",
            "logstash-structured-argument-attributes",
            "logstash-structured-arguments");

    if (capture) {
      assertThat(selector).isNotNull();
      assertThat(selector.test("any")).isTrue();
    } else {
      assertThat(selector).isNull();
    }
  }

  @Test
  void legacyExcludeOnlySelectorPreservesV2Behavior() {
    DeclarativeConfigProperties sourceConfig =
        sourceConfig("map-message-attributes", "map-message-attributes");
    when(sourceConfig
            .get("map_message_attributes/development")
            .getScalarList("excluded", String.class))
        .thenReturn(singletonList("secret*"));

    Predicate<String> selector =
        LoggingConfig.resolveStructuredAttributes(
            openTelemetry(false), sourceConfig, "log4j-appender", "map-message-attributes");

    assertThat(selector).isNotNull();
    assertThat(selector.test("public")).isTrue();
    assertThat(selector.test("secret-token")).isFalse();
  }

  @Test
  void warnsOnceForAppliedLegacySelector() {
    DeclarativeConfigProperties sourceConfig =
        sourceConfig("map-message-attributes", "map-message-attributes");
    when(sourceConfig
            .get("map_message_attributes/development")
            .getScalarList("included", String.class))
        .thenReturn(singletonList("*"));
    Logger logger = Logger.getLogger(LoggingConfig.class.getName());
    TestHandler handler = new TestHandler();
    logger.addHandler(handler);
    try {
      for (int i = 0; i < 2; i++) {
        LoggingConfig.resolveStructuredAttributes(
            openTelemetry(false), sourceConfig, "warning-test", "map-message-attributes");
      }

      assertThat(handler.records).hasSize(1);
      assertThat(handler.records.get(0).getMessage())
          .contains(
              "otel.instrumentation.warning-test.experimental.map-message-attributes.included",
              "otel.instrumentation.warning-test.experimental.map-message-attributes.excluded",
              "will be removed in 3.0",
              "otel.instrumentation.common.logging.structured-attributes.included",
              "otel.instrumentation.common.logging.structured-attributes.excluded");
    } finally {
      logger.removeHandler(handler);
    }
  }

  private static ExtendedOpenTelemetry openTelemetry(boolean v3Preview) {
    ExtendedOpenTelemetry openTelemetry = mock(ExtendedOpenTelemetry.class, RETURNS_DEEP_STUBS);
    DeclarativeConfigProperties commonConfig = openTelemetry.getInstrumentationConfig("common");
    when(commonConfig.getBoolean("v3_preview")).thenReturn(v3Preview);
    DeclarativeConfigProperties selector = commonConfig.get("logging").get("structured_attributes");
    when(selector.getScalarList("included", String.class)).thenReturn(null);
    when(selector.getScalarList("excluded", String.class)).thenReturn(null);
    return openTelemetry;
  }

  private static DeclarativeConfigProperties sourceConfig(
      String selectorName, String deprecatedSelectorName) {
    DeclarativeConfigProperties config =
        mock(DeclarativeConfigProperties.class, RETURNS_DEEP_STUBS);
    DeclarativeConfigProperties selector =
        config.get(selectorName.replace('-', '_') + "/development");
    when(selector.getScalarList("included", String.class)).thenReturn(null);
    when(selector.getScalarList("excluded", String.class)).thenReturn(null);
    when(config.getBoolean("capture_" + deprecatedSelectorName.replace('-', '_') + "/development"))
        .thenReturn(null);
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
