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
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.status.Status;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class OpenTelemetryAppenderLoggerContextSelectorTest {

  private static final String DEPRECATED_LOGGER_CONTEXT_ATTRIBUTES_WARNING =
      "The captureLoggerContext setting of the OpenTelemetry appender and the"
          + " otel.instrumentation.logback-appender.experimental"
          + ".capture-logger-context-attributes property are deprecated and may be removed in"
          + " the next minor release. Use loggerContextAttributesIncluded,"
          + " loggerContextAttributesExcluded, or otel.instrumentation.logback-appender"
          + ".experimental.logger-context-attributes.included instead.";

  @RegisterExtension
  private static final LibraryInstrumentationExtension testing =
      LibraryInstrumentationExtension.create();

  private Logger logger;
  private LoggerContext loggerContext;
  private OpenTelemetryAppender appender;

  @BeforeEach
  void setUp() {
    // an isolated logger context keeps logback-test.xml out of these assertions
    loggerContext = new LoggerContext();
    logger = loggerContext.getLogger("logger-context-selector-test");
    appender = new OpenTelemetryAppender();
    appender.setContext(loggerContext);
  }

  @AfterEach
  void tearDown() {
    appender.stop();
    loggerContext.stop();
  }

  @Test
  void configurationFileSelectorMatchesGlobPatterns() {
    appender.setLoggerContextAttributesIncluded("key*");
    appender.setLoggerContextAttributesExcluded("*2");
    loggerContext.putProperty("key1", "value1");
    loggerContext.putProperty("key2", "value2");
    loggerContext.putProperty("other", "value3");

    log();

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord.hasAttributesSatisfyingExactly(equalTo(stringKey("key1"), "value1")));
  }

  @Test
  void configurationFileSelectorCapturesEverythingNotExcluded() {
    appender.setLoggerContextAttributesExcluded("*-secret");
    loggerContext.putProperty("request-id", "123");
    loggerContext.putProperty("client-secret", "shh");

    log();

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord.hasAttributesSatisfyingExactly(equalTo(stringKey("request-id"), "123")));
  }

  @Test
  void noSelectorCapturesNothing() {
    loggerContext.putProperty("key1", "value1");

    log();

    testing.waitAndAssertLogRecords(logRecord -> logRecord.hasAttributesSatisfyingExactly());
    assertThat(warnings()).isEmpty();
  }

  @Test
  void selectorTakesPrecedenceOverConfigurationFileSelector() {
    appender.setLoggerContextAttributes(
        IncludeExclude.builder().setIncluded(singletonList("key1")).build());
    appender.setLoggerContextAttributesIncluded("key2");
    loggerContext.putProperty("key1", "value1");
    loggerContext.putProperty("key2", "value2");

    log();

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord.hasAttributesSatisfyingExactly(equalTo(stringKey("key1"), "value1")));
  }

  @Test
  @SuppressWarnings("deprecation") // testing the deprecated setting
  void configurationFileSelectorTakesPrecedenceOverDeprecatedSetting() {
    appender.setLoggerContextAttributesIncluded("key1");
    appender.setCaptureLoggerContext(true);
    loggerContext.putProperty("key1", "value1");
    loggerContext.putProperty("key2", "value2");

    log();

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord.hasAttributesSatisfyingExactly(equalTo(stringKey("key1"), "value1")));
    assertThat(warnings()).isEmpty();
  }

  @Test
  @SuppressWarnings("deprecation") // testing the deprecated setting
  void deprecatedSettingCapturesEverythingWhenEnabled() {
    appender.setCaptureLoggerContext(true);
    loggerContext.putProperty("key1", "value1");
    loggerContext.putProperty("key2", "value2");

    log();

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord.hasAttributesSatisfyingExactly(
                equalTo(stringKey("key1"), "value1"), equalTo(stringKey("key2"), "value2")));
    assertThat(warnings()).containsExactly(DEPRECATED_LOGGER_CONTEXT_ATTRIBUTES_WARNING);
  }

  @Test
  @SuppressWarnings("deprecation") // testing the deprecated setting
  void deprecatedSettingCapturesNothingWhenDisabled() {
    appender.setCaptureLoggerContext(false);
    loggerContext.putProperty("key1", "value1");

    log();

    testing.waitAndAssertLogRecords(logRecord -> logRecord.hasAttributesSatisfyingExactly());
    assertThat(warnings()).containsExactly(DEPRECATED_LOGGER_CONTEXT_ATTRIBUTES_WARNING);
  }

  @Test
  @SuppressWarnings("deprecation") // testing the deprecated setting
  void deprecatedSettingWarnsOnlyOnce() {
    appender.setCaptureLoggerContext(true);

    appender.start();
    appender.stop();
    appender.start();

    assertThat(warnings()).containsExactly(DEPRECATED_LOGGER_CONTEXT_ATTRIBUTES_WARNING);
  }

  private void log() {
    appender.setOpenTelemetry(testing.getOpenTelemetry());
    appender.start();
    logger.addAppender(appender);

    logger.info("log message");
  }

  private List<String> warnings() {
    List<String> warnings = new ArrayList<>();
    for (Status status : loggerContext.getStatusManager().getCopyOfStatusList()) {
      String message = status.getMessage();
      if (status.getLevel() == Status.WARN && message != null) {
        warnings.add(message);
      }
    }
    return warnings;
  }
}
