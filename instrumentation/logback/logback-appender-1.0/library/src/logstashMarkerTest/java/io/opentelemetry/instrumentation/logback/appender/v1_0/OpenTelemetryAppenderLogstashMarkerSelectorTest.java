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
import net.logstash.logback.marker.Markers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.LoggerFactory;

class OpenTelemetryAppenderLogstashMarkerSelectorTest {

  private static final String DEPRECATED_LOGSTASH_MARKER_ATTRIBUTES_WARNING =
      "The captureLogstashMarkerAttributes setting of the OpenTelemetry appender and the"
          + " otel.instrumentation.logback-appender.experimental"
          + ".capture-logstash-marker-attributes property are deprecated and may be removed in"
          + " the next minor release. Use logstashMarkerAttributesIncluded,"
          + " logstashMarkerAttributesExcluded, or otel.instrumentation.logback-appender"
          + ".experimental.logstash-marker-attributes.included instead.";

  @RegisterExtension
  private static final LibraryInstrumentationExtension testing =
      LibraryInstrumentationExtension.create();

  private Logger logger;
  private LoggerContext loggerContext;
  private OpenTelemetryAppender appender;

  @BeforeEach
  void setUp() {
    // logback 1.5 populates the logger context's MDCAdapter only through
    // LogbackServiceProvider.initialize(), so a context built with new LoggerContext() makes
    // ILoggingEvent.getMDCPropertyMap() throw inside every appender that reads the MDC. The
    // context has to come from LoggerFactory.
    logger = (Logger) LoggerFactory.getLogger("logstash-marker-selector-test");
    // the appender under test is the only one that should see these log events
    logger.setAdditive(false);
    loggerContext = logger.getLoggerContext();
    loggerContext.getStatusManager().clear();
    appender = new OpenTelemetryAppender();
    appender.setContext(loggerContext);
  }

  @AfterEach
  void tearDown() {
    logger.detachAppender(appender);
    appender.stop();
  }

  @Test
  void configurationFileSelectorMatchesGlobPatterns() {
    appender.setLogstashMarkerAttributesIncluded("key*");
    appender.setLogstashMarkerAttributesExcluded("*2");

    log();

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord.hasAttributesSatisfyingExactly(equalTo(stringKey("key1"), "value1")));
  }

  @Test
  void configurationFileSelectorCapturesEverythingNotExcluded() {
    appender.setLogstashMarkerAttributesExcluded("key2,other");

    log();

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord.hasAttributesSatisfyingExactly(equalTo(stringKey("key1"), "value1")));
  }

  @Test
  void noSelectorCapturesNothing() {
    log();

    testing.waitAndAssertLogRecords(logRecord -> logRecord.hasAttributesSatisfyingExactly());
    assertThat(warnings()).isEmpty();
  }

  @Test
  void selectorTakesPrecedenceOverConfigurationFileSelector() {
    appender.setLogstashMarkerAttributes(
        IncludeExclude.builder().setIncluded(singletonList("key1")).build());
    appender.setLogstashMarkerAttributesIncluded("key2");

    log();

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord.hasAttributesSatisfyingExactly(equalTo(stringKey("key1"), "value1")));
  }

  @Test
  @SuppressWarnings("deprecation") // testing the deprecated setting
  void configurationFileSelectorTakesPrecedenceOverDeprecatedSetting() {
    appender.setLogstashMarkerAttributesIncluded("key1");
    appender.setCaptureLogstashMarkerAttributes(true);

    log();

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord.hasAttributesSatisfyingExactly(equalTo(stringKey("key1"), "value1")));
    assertThat(warnings()).isEmpty();
  }

  @Test
  @SuppressWarnings("deprecation") // testing the deprecated setting
  void deprecatedSettingCapturesEverythingWhenEnabled() {
    appender.setCaptureLogstashMarkerAttributes(true);

    log();

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord.hasAttributesSatisfyingExactly(
                equalTo(stringKey("key1"), "value1"),
                equalTo(stringKey("key2"), "value2"),
                equalTo(stringKey("other"), "value3")));
    assertThat(warnings()).containsExactly(DEPRECATED_LOGSTASH_MARKER_ATTRIBUTES_WARNING);
  }

  @Test
  @SuppressWarnings("deprecation") // testing the deprecated setting
  void deprecatedSettingCapturesNothingWhenDisabled() {
    appender.setCaptureLogstashMarkerAttributes(false);

    log();

    testing.waitAndAssertLogRecords(logRecord -> logRecord.hasAttributesSatisfyingExactly());
    assertThat(warnings()).containsExactly(DEPRECATED_LOGSTASH_MARKER_ATTRIBUTES_WARNING);
  }

  @Test
  @SuppressWarnings("deprecation") // testing the deprecated setting
  void deprecatedSettingWarnsOnlyOnce() {
    appender.setCaptureLogstashMarkerAttributes(true);

    appender.start();
    appender.stop();
    appender.start();

    assertThat(warnings()).containsExactly(DEPRECATED_LOGSTASH_MARKER_ATTRIBUTES_WARNING);
  }

  private void log() {
    appender.setOpenTelemetry(testing.getOpenTelemetry());
    appender.start();
    logger.addAppender(appender);

    logger
        .atInfo()
        .setMessage("log message")
        .addMarker(Markers.append("key1", "value1"))
        .addMarker(Markers.append("key2", "value2"))
        .addMarker(Markers.append("other", "value3"))
        .log();
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
