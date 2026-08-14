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
import org.slf4j.MDC;

class OpenTelemetryAppenderMdcSelectorTest {

  private static final String DEPRECATED_MDC_ATTRIBUTES_WARNING =
      "The captureMdcAttributes setting of the OpenTelemetry appender and the"
          + " otel.instrumentation.logback-appender.experimental.capture-mdc-attributes"
          + " property are deprecated and may be removed in the next minor release. Use"
          + " mdcAttributesIncluded, mdcAttributesExcluded, or"
          + " otel.instrumentation.logback-appender.experimental.mdc-attributes.included"
          + " instead.";

  @RegisterExtension
  private static final LibraryInstrumentationExtension testing =
      LibraryInstrumentationExtension.create();

  private Logger logger;
  private OpenTelemetryAppender appender;

  @BeforeEach
  void setUp() {
    logger = (Logger) LoggerFactory.getLogger("mdc-selector-test");
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
    MDC.clear();
  }

  @Test
  void configurationFileSelectorMatchesGlobPatterns() {
    appender.setMdcAttributesIncluded("key*");
    appender.setMdcAttributesExcluded("*2");

    log(mdc("key1", "value1", "key2", "value2", "other", "value3"));

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord.hasAttributesSatisfyingExactly(equalTo(stringKey("key1"), "value1")));
  }

  @Test
  void configurationFileSelectorCapturesEverythingNotExcluded() {
    appender.setMdcAttributesExcluded("*-secret");

    log(mdc("request-id", "123", "client-secret", "shh"));

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord.hasAttributesSatisfyingExactly(equalTo(stringKey("request-id"), "123")));
  }

  @Test
  void noSelectorCapturesNothing() {
    log(mdc("key1", "value1"));

    testing.waitAndAssertLogRecords(logRecord -> logRecord.hasAttributesSatisfyingExactly());
  }

  @Test
  void selectorTakesPrecedenceOverConfigurationFileSelector() {
    appender.setMdcAttributes(IncludeExclude.builder().setIncluded(singletonList("key1")).build());
    appender.setMdcAttributesIncluded("key2");

    log(mdc("key1", "value1", "key2", "value2"));

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord.hasAttributesSatisfyingExactly(equalTo(stringKey("key1"), "value1")));
  }

  @Test
  @SuppressWarnings("deprecation") // testing the deprecated setting
  void configurationFileSelectorTakesPrecedenceOverDeprecatedSetting() {
    appender.setMdcAttributesIncluded("key1");
    appender.setCaptureMdcAttributes("key2");

    log(mdc("key1", "value1", "key2", "value2"));

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord.hasAttributesSatisfyingExactly(equalTo(stringKey("key1"), "value1")));
    assertThat(warnings()).isEmpty();
  }

  @Test
  @SuppressWarnings("deprecation") // testing the deprecated setting
  void deprecatedSettingMatchesKeysLiterally() {
    appender.setCaptureMdcAttributes("*,key1");

    log(mdc("key1", "value1", "key2", "value2", "*", "star"));

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord.hasAttributesSatisfyingExactly(
                equalTo(stringKey("*"), "star"), equalTo(stringKey("key1"), "value1")));
    assertThat(warnings()).containsExactly(DEPRECATED_MDC_ATTRIBUTES_WARNING);
  }

  @Test
  @SuppressWarnings("deprecation") // testing the deprecated setting
  void deprecatedSettingWarnsOnlyOnce() {
    appender.setCaptureMdcAttributes("key1");

    appender.start();
    appender.stop();
    appender.start();

    assertThat(warnings()).containsExactly(DEPRECATED_MDC_ATTRIBUTES_WARNING);
  }

  @Test
  @SuppressWarnings("deprecation") // testing the deprecated setting
  void deprecatedSettingDoesNotMatchSingleCharacterWildcard() {
    appender.setCaptureMdcAttributes("key?");

    log(mdc("key1", "value1", "key?", "literal"));

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord.hasAttributesSatisfyingExactly(equalTo(stringKey("key?"), "literal")));
  }

  @Test
  @SuppressWarnings("deprecation") // testing the deprecated setting
  void deprecatedSettingCapturesEverythingWithSoleWildcard() {
    appender.setCaptureMdcAttributes("*");

    log(mdc("key1", "value1", "key2", "value2"));

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord.hasAttributesSatisfyingExactly(
                equalTo(stringKey("key1"), "value1"), equalTo(stringKey("key2"), "value2")));
  }

  @Test
  @SuppressWarnings("deprecation") // testing the deprecated setting
  void deprecatedSettingCapturesNothingWhenEmpty() {
    appender.setCaptureMdcAttributes("");

    log(mdc("key1", "value1"));

    testing.waitAndAssertLogRecords(logRecord -> logRecord.hasAttributesSatisfyingExactly());
    assertThat(warnings()).isEmpty();
  }

  private void log(Map<String, String> mdcProperties) {
    appender.setOpenTelemetry(testing.getOpenTelemetry());
    appender.start();
    logger.addAppender(appender);

    mdcProperties.forEach(MDC::put);
    try {
      logger.info("log message");
    } finally {
      MDC.clear();
    }
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

  private static Map<String, String> mdc(String... keyValuePairs) {
    Map<String, String> mdcProperties = new LinkedHashMap<>();
    for (int i = 0; i < keyValuePairs.length; i += 2) {
      mdcProperties.put(keyValuePairs[i], keyValuePairs[i + 1]);
    }
    return mdcProperties;
  }
}
