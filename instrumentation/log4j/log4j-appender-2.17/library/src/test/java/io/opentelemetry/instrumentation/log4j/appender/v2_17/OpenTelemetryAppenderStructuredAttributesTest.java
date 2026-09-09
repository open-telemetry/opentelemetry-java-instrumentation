/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.log4j.appender.v2_17;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.v3Preview;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static java.nio.charset.StandardCharsets.UTF_8;

import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.xml.XmlConfiguration;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.StringMapMessage;
import org.apache.logging.log4j.util.SortedArrayStringMap;
import org.apache.logging.log4j.util.StringMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OpenTelemetryAppenderStructuredAttributesTest {

  @RegisterExtension
  private static final LibraryInstrumentationExtension testing =
      LibraryInstrumentationExtension.create();

  @Test
  void defaultStructuredAttributeCapture() {
    log(builder());

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord
                .hasEventName("test.event")
                .hasBody("log message")
                .hasAttributesSatisfyingExactly(
                    equalTo(stringKey("key1"), v3Preview() ? "value1" : null),
                    equalTo(stringKey("key2"), v3Preview() ? "value2" : null),
                    equalTo(stringKey("other"), v3Preview() ? "value3" : null)));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "*", " , "})
  void unifiedXmlSettingsOverrideSourceSpecificSelector(String included) {
    log(
        builder()
            .setMapMessageAttributes(IncludeExclude.builder().setExcluded("*").build())
            .setStructuredAttributesIncluded(included));

    assertAllAttributes();
  }

  @Test
  void emptyUnifiedApiSelectorOverridesXml() {
    log(
        builder()
            .setStructuredAttributes(IncludeExclude.builder().build())
            .setStructuredAttributesExcluded("*")
            .setMapMessageAttributesIncluded("none"));

    assertAllAttributes();
  }

  @Test
  void clearingUnifiedApiSelectorRestoresSourceSpecificSelector() {
    log(builder()
        .setStructuredAttributes(IncludeExclude.builder().build())
        .setStructuredAttributes(null)
        .setMapMessageAttributesIncluded("key1"));

    testing.waitAndAssertLogRecords(logRecord -> logRecord.hasAttributesSatisfyingExactly(
        equalTo(stringKey(v3Preview() ? "key1" : "log4j.map_message.key1"), "value1")));
  }

  @Test
  void unifiedApiSelectorFiltersKeys() {
    log(
        builder()
            .setStructuredAttributes(
                IncludeExclude.builder().setIncluded("key?").setExcluded("*2").build())
            .setStructuredAttributesIncluded("*")
            .setMapMessageAttributesIncluded("*"));

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord
                .hasEventName("test.event")
                .hasAttributesSatisfyingExactly(
                    equalTo(stringKey(v3Preview() ? "key1" : "log4j.map_message.key1"), "value1")));
  }

  @Test
  void unifiedExcludeOnlySelector() {
    log(builder().setStructuredAttributesExcluded("key2,other"));

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord.hasAttributesSatisfyingExactly(
                equalTo(stringKey(v3Preview() ? "key1" : "log4j.map_message.key1"), "value1")));
  }

  @Test
  void disablingStructuredAttributesPreservesEventNameAndBody() {
    log(builder().setStructuredAttributesExcluded("*").setMapMessageAttributesIncluded("*"));

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord
                .hasEventName("test.event")
                .hasBody("log message")
                .hasAttributesSatisfyingExactly());
  }

  @Test
  void sourceSpecificSelectorRemainsSupported() {
    log(builder().setMapMessageAttributes(IncludeExclude.builder().setIncluded("key1").build()));

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord.hasAttributesSatisfyingExactly(
                equalTo(stringKey(v3Preview() ? "key1" : "log4j.map_message.key1"), "value1")));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void ambientAttributesAndCollisionPrecedenceAreUnchanged(boolean disabled) {
    OpenTelemetryAppender appender =
        builder()
            .setStructuredAttributes(
                IncludeExclude.builder().setExcluded(disabled ? "*" : "secret").build())
            .setContextDataAttributesIncluded("*")
            .build();
    appender.start();
    try {
      StringMap contextData = new SortedArrayStringMap();
      contextData.putValue("ambient", "context-value");
      contextData.putValue("collision", "context");
      contextData.putValue("otel.event.name", "context.event");
      appender.append(
          Log4jLogEvent.newBuilder()
              .setLoggerName("structured-selector-test")
              .setLevel(Level.INFO)
              .setContextData(contextData)
              .setMessage(
                  new StringMapMessage()
                      .with("collision", "message")
                      .with("secret", "hidden")
                      .with("otel.event.name", "test.event"))
              .build());
    } finally {
      appender.stop();
    }

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord
                .hasEventName("test.event")
                .hasAttributesSatisfyingExactly(
                    equalTo(stringKey("ambient"), "context-value"),
                    equalTo(
                        stringKey("collision"), v3Preview() && !disabled ? "message" : "context"),
                    equalTo(
                        stringKey("log4j.map_message.collision"),
                        !v3Preview() && !disabled ? "message" : null)));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "*"})
  void unifiedSelectorIsAppliedFromXml(String included) throws IOException {
    String xml =
        "<Configuration><Appenders><OpenTelemetry name='OTEL' structuredAttributesIncluded='"
            + included
            + "' mapMessageAttributesIncluded='none'/></Appenders></Configuration>";
    try (LoggerContext context = new LoggerContext("structured-xml")) {
      XmlConfiguration configuration =
          new XmlConfiguration(
              context, new ConfigurationSource(new ByteArrayInputStream(xml.getBytes(UTF_8))));
      context.start(configuration);
      OpenTelemetryAppender appender = configuration.getAppender("OTEL");
      appender.setOpenTelemetry(testing.getOpenTelemetry());
      append(appender);
    }

    assertAllAttributes();
  }

  private static OpenTelemetryAppender.Builder<?> builder() {
    return OpenTelemetryAppender.builder()
        .setName("structured-selector-test")
        .setOpenTelemetry(testing.getOpenTelemetry());
  }

  private static void log(OpenTelemetryAppender.Builder<?> builder) {
    OpenTelemetryAppender appender = builder.build();
    appender.start();
    try {
      append(appender);
    } finally {
      appender.stop();
    }
  }

  private static void append(OpenTelemetryAppender appender) {
    appender.append(
        Log4jLogEvent.newBuilder()
            .setLoggerName("structured-selector-test")
            .setLevel(Level.INFO)
            .setMessage(
                new StringMapMessage()
                    .with("message", "log message")
                    .with("otel.event.name", "test.event")
                    .with("key1", "value1")
                    .with("key2", "value2")
                    .with("other", "value3"))
            .build());
  }

  private static void assertAllAttributes() {
    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord
                .hasEventName("test.event")
                .hasBody("log message")
                .hasAttributesSatisfyingExactly(
                    equalTo(stringKey(v3Preview() ? "key1" : "log4j.map_message.key1"), "value1"),
                    equalTo(stringKey(v3Preview() ? "key2" : "log4j.map_message.key2"), "value2"),
                    equalTo(
                        stringKey(v3Preview() ? "other" : "log4j.map_message.other"), "value3")));
  }
}
