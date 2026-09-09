/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.logback.appender.v1_0;

import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.v3Preview;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

class OpenTelemetryAppenderLogstashStructuredArgsSelectorTest {

  private static final String DEPRECATED_LOGSTASH_STRUCTURED_ARGUMENTS_WARNING =
      "The captureLogstashStructuredArguments setting of the OpenTelemetry appender and the"
          + " otel.instrumentation.logback-appender.experimental"
          + ".capture-logstash-structured-arguments property are deprecated and may be removed"
          + " in the next minor release. Use logstashStructuredArgumentAttributesIncluded,"
          + " logstashStructuredArgumentAttributesExcluded, or"
          + " otel.instrumentation.logback-appender.experimental"
          + ".logstash-structured-argument-attributes.included instead.";

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
    appender.setLogstashStructuredArgumentAttributesIncluded("key*");
    appender.setLogstashStructuredArgumentAttributesExcluded("*2");

    log();

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord.hasAttributesSatisfyingExactly(equalTo(stringKey("key1"), "value1")));
  }

  @Test
  void configurationFileSelectorCapturesEverythingNotExcluded() {
    appender.setLogstashStructuredArgumentAttributesExcluded("key2,other");

    log();

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord.hasAttributesSatisfyingExactly(equalTo(stringKey("key1"), "value1")));
  }

  @Test
  void defaultStructuredAttributeCapture() {
    log();

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord.hasAttributesSatisfyingExactly(
                equalTo(stringKey("key1"), v3Preview() ? "value1" : null),
                equalTo(stringKey("key2"), v3Preview() ? "value2" : null),
                equalTo(stringKey("other"), v3Preview() ? "value3" : null)));
    assertThat(warnings()).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "*", " , "})
  void unifiedXmlSelectorCapturesAll(String included) {
    appender.setLogstashStructuredArgumentAttributesIncluded("none");
    appender.setStructuredAttributesIncluded(included);

    log();

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord.hasAttributesSatisfyingExactly(
                equalTo(stringKey("key1"), "value1"),
                equalTo(stringKey("key2"), "value2"),
                equalTo(stringKey("other"), "value3")));
  }

  @Test
  void emptyUnifiedApiSelectorOverridesXml() {
    appender.setStructuredAttributes(IncludeExclude.builder().build());
    appender.setStructuredAttributesExcluded("*");
    appender.setLogstashStructuredArgumentAttributesIncluded("none");

    log();

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord.hasAttributesSatisfyingExactly(
                equalTo(stringKey("key1"), "value1"),
                equalTo(stringKey("key2"), "value2"),
                equalTo(stringKey("other"), "value3")));
  }

  @Test
  void unifiedApiSelectorFiltersKeys() {
    appender.setStructuredAttributes(
        IncludeExclude.builder().setIncluded("key?").setExcluded("*2").build());
    appender.setLogstashStructuredArgumentAttributesIncluded("*");

    log();

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord.hasAttributesSatisfyingExactly(equalTo(stringKey("key1"), "value1")));
  }

  @Test
  void unifiedExcludeOnlySelector() {
    appender.setStructuredAttributesExcluded("key2,other");

    log();

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord.hasAttributesSatisfyingExactly(equalTo(stringKey("key1"), "value1")));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void unifiedSelectorPreservesAmbientAttributesAndCollisionPrecedence(boolean disabled) {
    appender.setStructuredAttributes(
        IncludeExclude.builder().setExcluded(disabled ? "*" : "secret").build());
    appender.setMdcAttributesIncluded("collision,ambient");
    appender.setLoggerContextAttributesIncluded("collision,context");
    loggerContext.putProperty("collision", "context");
    loggerContext.putProperty("context", "context-value");
    appender.setOpenTelemetry(testing.getOpenTelemetry());
    appender.start();
    logger.addAppender(appender);

    MDC.put("ambient", "mdc-value");
    MDC.put("collision", "mdc");
    try {
      logger
          .atInfo()
          .addMarker(Markers.append("collision", "marker"))
          .addMarker(Markers.append("marker", 1))
          .addKeyValue("collision", "kvp")
          .addKeyValue("kvp", 3)
          .addKeyValue("secret", "hidden")
          .log(
              "log message",
              StructuredArguments.keyValue("collision", "argument"),
              StructuredArguments.keyValue("argument", 2),
              StructuredArguments.keyValue("otel.event.name", "test.event"));
    } finally {
      MDC.clear();
      loggerContext.putProperty("collision", null);
      loggerContext.putProperty("context", null);
    }

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord
                .hasEventName("test.event")
                .hasAttributesSatisfyingExactly(
                    equalTo(stringKey("ambient"), "mdc-value"),
                    equalTo(stringKey("context"), "context-value"),
                    equalTo(stringKey("collision"), disabled ? "mdc" : "kvp"),
                    equalTo(longKey("marker"), disabled ? null : 1L),
                    equalTo(longKey("argument"), disabled ? null : 2L),
                    equalTo(longKey("kvp"), disabled ? null : 3L)));
  }

  @Test
  void selectorTakesPrecedenceOverConfigurationFileSelector() {
    appender.setLogstashStructuredArgumentAttributes(
        IncludeExclude.builder().setIncluded(singletonList("key1")).build());
    appender.setLogstashStructuredArgumentAttributesIncluded("key2");

    log();

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord.hasAttributesSatisfyingExactly(equalTo(stringKey("key1"), "value1")));
  }

  @Test
  @SuppressWarnings("deprecation") // testing the deprecated setting
  void configurationFileSelectorTakesPrecedenceOverDeprecatedSetting() {
    appender.setLogstashStructuredArgumentAttributesIncluded("key1");
    appender.setCaptureLogstashStructuredArguments(true);

    log();

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord.hasAttributesSatisfyingExactly(equalTo(stringKey("key1"), "value1")));
    assertThat(warnings()).isEmpty();
  }

  @Test
  @SuppressWarnings("deprecation") // testing the deprecated setting
  void deprecatedSettingCapturesEverythingWhenEnabled() {
    appender.setCaptureLogstashStructuredArguments(true);

    log();

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord.hasAttributesSatisfyingExactly(
                equalTo(stringKey("key1"), "value1"),
                equalTo(stringKey("key2"), "value2"),
                equalTo(stringKey("other"), "value3")));
    assertThat(warnings()).containsExactly(DEPRECATED_LOGSTASH_STRUCTURED_ARGUMENTS_WARNING);
  }

  @Test
  @SuppressWarnings("deprecation") // testing the deprecated setting
  void deprecatedSettingCapturesNothingWhenDisabled() {
    appender.setCaptureLogstashStructuredArguments(false);

    log();

    testing.waitAndAssertLogRecords(logRecord -> logRecord.hasAttributesSatisfyingExactly());
    assertThat(warnings()).containsExactly(DEPRECATED_LOGSTASH_STRUCTURED_ARGUMENTS_WARNING);
  }

  @Test
  @SuppressWarnings("deprecation") // testing the deprecated setting
  void deprecatedSettingWarnsOnlyOnce() {
    appender.setCaptureLogstashStructuredArguments(true);

    appender.start();
    appender.stop();
    appender.start();

    assertThat(warnings()).containsExactly(DEPRECATED_LOGSTASH_STRUCTURED_ARGUMENTS_WARNING);
  }

  @Test
  void eventNameIsCapturedWithoutSelector() {
    appender.setOpenTelemetry(testing.getOpenTelemetry());
    appender.start();
    logger.addAppender(appender);

    logger.info(
        "log message {} {}",
        StructuredArguments.keyValue("otel.event.name", "test.event"),
        StructuredArguments.keyValue("key1", "value1"));

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord
                .hasEventName("test.event")
                .hasAttributesSatisfyingExactly(
                    equalTo(stringKey("key1"), v3Preview() ? "value1" : null)));
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
