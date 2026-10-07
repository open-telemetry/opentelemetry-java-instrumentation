/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.logback.appender.v1_0;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.status.Status;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;

class OpenTelemetryAppenderKeyValuePairSelectorTest {

  @RegisterExtension
  private static final LibraryInstrumentationExtension testing =
      LibraryInstrumentationExtension.create();

  private Logger logger;
  private OpenTelemetryAppender appender;

  @BeforeEach
  void setUp() {
    logger = (Logger) LoggerFactory.getLogger("key-value-pair-selector-test");
    // the appender under test is the only one that should see these log events
    logger.setAdditive(false);
    logger.getLoggerContext().getStatusManager().clear();
    appender = new OpenTelemetryAppender();
    appender.setContext(logger.getLoggerContext());
  }

  @AfterEach
  void tearDown() {
    logger.detachAppender(appender);
    appender.stop();
  }

  @Test
  void configurationFileSelectorMatchesGlobPatterns() {
    appender.setKeyValuePairAttributesIncluded("key*");
    appender.setKeyValuePairAttributesExcluded("*2");

    log(keyValuePairs("key1", "value1", "key2", "value2", "other", "value3"));

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord.hasAttributesSatisfyingExactly(equalTo(stringKey("key1"), "value1")));
  }

  @Test
  void configurationFileSelectorCapturesEverythingNotExcluded() {
    appender.setKeyValuePairAttributesExcluded("*-secret");

    log(keyValuePairs("request-id", "123", "client-secret", "shh"));

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord.hasAttributesSatisfyingExactly(equalTo(stringKey("request-id"), "123")));
  }

  @Test
  void noSelectorCapturesNothing() {
    log(keyValuePairs("key1", "value1"));

    testing.waitAndAssertLogRecords(logRecord -> logRecord.hasAttributesSatisfyingExactly());
    assertThat(warnings()).isEmpty();
  }

  @Test
  void selectorTakesPrecedenceOverConfigurationFileSelector() {
    appender.setKeyValuePairAttributes(
        IncludeExclude.builder().setIncluded(singletonList("key1")).build());
    appender.setKeyValuePairAttributesIncluded("key2");

    log(keyValuePairs("key1", "value1", "key2", "value2"));

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord.hasAttributesSatisfyingExactly(equalTo(stringKey("key1"), "value1")));
  }

  @Test
  void emptySelectorFallsBackToConfigurationFileSelector() {
    appender.setKeyValuePairAttributesIncluded("key1");
    appender.setKeyValuePairAttributes(IncludeExclude.builder().build());

    log(keyValuePairs("key1", "value1", "key2", "value2"));

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord.hasAttributesSatisfyingExactly(equalTo(stringKey("key1"), "value1")));
    assertThat(warnings()).isEmpty();
  }

  @Test
  void selectorCapturesEverythingWhenIncluded() {
    appender.setKeyValuePairAttributes(IncludeExclude.builder().setIncluded("*").build());

    log(keyValuePairs("key1", "value1", "key2", "value2"));

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord.hasAttributesSatisfyingExactly(
                equalTo(stringKey("key1"), "value1"), equalTo(stringKey("key2"), "value2")));
    assertThat(warnings()).isEmpty();
  }

  @Test
  void configurationFileSelectorCapturesNothingWhenEverythingExcluded() {
    appender.setKeyValuePairAttributesExcluded("*");

    log(keyValuePairs("key1", "value1"));

    testing.waitAndAssertLogRecords(logRecord -> logRecord.hasAttributesSatisfyingExactly());
    assertThat(warnings()).isEmpty();
  }

  @Test
  void emptySelectorCapturesNothing() {
    appender.setKeyValuePairAttributes(IncludeExclude.builder().build());

    log(keyValuePairs("key1", "value1"));

    testing.waitAndAssertLogRecords(logRecord -> logRecord.hasAttributesSatisfyingExactly());
  }

  @Test
  void nullKeyIsIgnored() {
    appender.setKeyValuePairAttributesIncluded("*");

    Map<String, String> keyValuePairs = new LinkedHashMap<>();
    keyValuePairs.put(null, "value1");
    keyValuePairs.put("key1", "value2");
    log(keyValuePairs);

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord.hasAttributesSatisfyingExactly(equalTo(stringKey("key1"), "value2")));
  }

  private void log(Map<String, String> keyValuePairs) {
    appender.setOpenTelemetry(testing.getOpenTelemetry());
    appender.start();
    logger.addAppender(appender);

    LoggingEventBuilder eventBuilder = logger.atInfo();
    for (Map.Entry<String, String> keyValuePair : keyValuePairs.entrySet()) {
      eventBuilder = eventBuilder.addKeyValue(keyValuePair.getKey(), keyValuePair.getValue());
    }
    eventBuilder.log("log message");
  }

  private List<String> warnings() {
    List<String> warnings = new ArrayList<>();
    for (Status status : logger.getLoggerContext().getStatusManager().getCopyOfStatusList()) {
      String message = status.getMessage();
      if (status.getLevel() == Status.WARN && message != null) {
        warnings.add(message);
      }
    }
    return warnings;
  }

  private static Map<String, String> keyValuePairs(String... keyValuePairs) {
    Map<String, String> map = new LinkedHashMap<>();
    for (int i = 0; i < keyValuePairs.length; i += 2) {
      map.put(keyValuePairs[i], keyValuePairs[i + 1]);
    }
    return map;
  }
}
