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
import net.logstash.logback.argument.StructuredArguments;
import net.logstash.logback.marker.Markers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

class OpenTelemetryAppenderLogstashStructuredArgsSelectorTest {

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
    logger = (Logger) LoggerFactory.getLogger("logstash-structured-args-selector-test");
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
    appender.setStructuredAttributesIncluded("key*");
    appender.setStructuredAttributesExcluded("*2");

    log();

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord.hasAttributesSatisfyingExactly(equalTo(stringKey("key1"), "value1")));
  }

  @Test
  void configurationFileSelectorCapturesEverythingNotExcluded() {
    appender.setStructuredAttributesExcluded("key2,other");

    log();

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord.hasAttributesSatisfyingExactly(equalTo(stringKey("key1"), "value1")));
  }

  @Test
  void noSelectorCapturesEverything() {
    log();

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord.hasAttributesSatisfyingExactly(
                equalTo(stringKey("key1"), "value1"),
                equalTo(stringKey("key2"), "value2"),
                equalTo(stringKey("other"), "value3")));
    assertThat(warnings()).isEmpty();
  }

  @Test
  void selectorTakesPrecedenceOverConfigurationFileSelector() {
    appender.setStructuredAttributes(
        IncludeExclude.builder().setIncluded(singletonList("key1")).build());
    appender.setStructuredAttributesIncluded("key2");

    log();

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord.hasAttributesSatisfyingExactly(equalTo(stringKey("key1"), "value1")));
  }

  @Test
  void emptySelectorFallsBackToConfigurationFileSelector() {
    appender.setStructuredAttributesIncluded("key1");
    appender.setStructuredAttributes(IncludeExclude.builder().build());

    log();

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord.hasAttributesSatisfyingExactly(equalTo(stringKey("key1"), "value1")));
    assertThat(warnings()).isEmpty();
  }

  @Test
  void selectorCapturesEverythingWhenIncluded() {
    appender.setStructuredAttributes(IncludeExclude.builder().setIncluded("*").build());

    log();

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord.hasAttributesSatisfyingExactly(
                equalTo(stringKey("key1"), "value1"),
                equalTo(stringKey("key2"), "value2"),
                equalTo(stringKey("other"), "value3")));
    assertThat(warnings()).isEmpty();
  }

  @Test
  void configurationFileSelectorCapturesNothingWhenEverythingExcluded() {
    appender.setStructuredAttributesExcluded("*");

    log();

    testing.waitAndAssertLogRecords(logRecord -> logRecord.hasAttributesSatisfyingExactly());
    assertThat(warnings()).isEmpty();
  }

  @Test
  void emptySelectorCapturesEverything() {
    appender.setStructuredAttributes(IncludeExclude.builder().build());

    log();

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord.hasAttributesSatisfyingExactly(
                equalTo(stringKey("key1"), "value1"),
                equalTo(stringKey("key2"), "value2"),
                equalTo(stringKey("other"), "value3")));
  }

  @Test
  void eventNameIsCapturedWhenStructuredAttributesExcluded() {
    appender.setStructuredAttributesExcluded("*");
    appender.setOpenTelemetry(testing.getOpenTelemetry());
    appender.start();
    logger.addAppender(appender);

    logger.info(
        "log message {} {}",
        StructuredArguments.keyValue("otel.event.name", "test.event"),
        StructuredArguments.keyValue("key1", "value1"));

    testing.waitAndAssertLogRecords(
        logRecord -> logRecord.hasEventName("test.event").hasAttributesSatisfyingExactly());
  }

  @Test
  void selectorFiltersAllStructuredSourcesWithoutFilteringMdc() {
    appender.setStructuredAttributes(
        IncludeExclude.builder().setIncluded("request-*").setExcluded("*-secret").build());
    appender.setMdcAttributes(IncludeExclude.builder().setIncluded("mdc-secret").build());
    appender.setOpenTelemetry(testing.getOpenTelemetry());
    appender.start();
    logger.addAppender(appender);

    MDC.put("mdc-secret", "separate");
    try {
      logger
          .atInfo()
          .addKeyValue("request-kvp", "captured")
          .addKeyValue("request-kvp-secret", "ignored")
          .addMarker(Markers.append("request-marker", "captured"))
          .addMarker(Markers.append("request-marker-secret", "ignored"))
          .addArgument(StructuredArguments.keyValue("request-argument", "captured"))
          .addArgument(StructuredArguments.keyValue("request-argument-secret", "ignored"))
          .log("log message {} {}");
    } finally {
      MDC.remove("mdc-secret");
    }

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord.hasAttributesSatisfyingExactly(
                equalTo(stringKey("request-kvp"), "captured"),
                equalTo(stringKey("request-marker"), "captured"),
                equalTo(stringKey("request-argument"), "captured"),
                equalTo(stringKey("mdc-secret"), "separate")));
  }

  @Test
  void xmlSelectorFiltersAllStructuredSources() {
    OpenTelemetryAppender.install(testing.getOpenTelemetry());
    loggerContext
        .getLogger("structured-selector-xml-test")
        .atInfo()
        .addKeyValue("request-kvp", "captured")
        .addKeyValue("request-kvp-secret", "ignored")
        .addMarker(Markers.append("request-marker", "captured"))
        .addMarker(Markers.append("request-marker-secret", "ignored"))
        .addArgument(StructuredArguments.keyValue("request-argument", "captured"))
        .addArgument(StructuredArguments.keyValue("request-argument-secret", "ignored"))
        .log("log message {} {}");

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord.hasAttributesSatisfyingExactly(
                equalTo(stringKey("request-kvp"), "captured"),
                equalTo(stringKey("request-marker"), "captured"),
                equalTo(stringKey("request-argument"), "captured")));
  }

  private void log() {
    appender.setOpenTelemetry(testing.getOpenTelemetry());
    appender.start();
    logger.addAppender(appender);

    logger.info(
        "log message {} {} {}",
        StructuredArguments.keyValue("key1", "value1"),
        StructuredArguments.keyValue("key2", "value2"),
        StructuredArguments.keyValue("other", "value3"));
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
