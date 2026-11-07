/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.logback.appender.v1_0;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

class OpenTelemetryAppenderMdcExportTest {
  @RegisterExtension
  private static final LibraryInstrumentationExtension testing =
      LibraryInstrumentationExtension.create();

  private Logger logger;
  private OpenTelemetryAppender exporter;
  private io.opentelemetry.instrumentation.logback.mdc.v1_0.OpenTelemetryAppender mdcAppender;
  private ListAppender<ILoggingEvent> applicationAppender;

  @BeforeEach
  void setUp() {
    logger = (Logger) LoggerFactory.getLogger("mdc-export-test");
    logger.setAdditive(false);
    exporter = new OpenTelemetryAppender();
    exporter.setContext(logger.getLoggerContext());
    mdcAppender = new io.opentelemetry.instrumentation.logback.mdc.v1_0.OpenTelemetryAppender();
    mdcAppender.setAddBaggage(true);
    mdcAppender.setContext(logger.getLoggerContext());
    applicationAppender = new ListAppender<>();
    applicationAppender.start();
    mdcAppender.addAppender(applicationAppender);
    mdcAppender.start();
  }

  @AfterEach
  void tearDown() {
    logger.detachAndStopAllAppenders();
    exporter.stop();
    applicationAppender.stop();
    logger.getLoggerContext().putProperty("context-key", null);
    MDC.clear();
  }

  @ParameterizedTest
  @CsvSource({
    "false, before",
    "false, after",
    "false, nested",
    "true, before",
    "true, after",
    "true, nested"
  })
  void exportsOnlyApplicationMdc(boolean replay, String position) {
    exporter.setMdcAttributesIncluded("*");
    startExporter(replay, position);
    MDC.put("request-id", "123");
    MDC.put("span_id", "application-span");
    MDC.put("trace_flags", "application-flags");
    MDC.put("baggage.key", "application-baggage");
    Span span = testing.getOpenTelemetry().getTracer("test").spanBuilder("test").startSpan();
    try (Scope ignored = span.makeCurrent();
        Scope ignoredBaggage =
            Baggage.builder().put("key", "enriched-baggage").build().makeCurrent()) {
      logger.info("test log");
    } finally {
      span.end();
    }
    MDC.clear();
    if (replay) {
      exporter.setOpenTelemetry(testing.getOpenTelemetry());
    }

    testing.waitAndAssertLogRecords(
        log ->
            log.hasBody("test log")
                .hasAttributesSatisfyingExactly(
                    equalTo(stringKey("request-id"), "123"),
                    equalTo(stringKey("span_id"), "application-span"),
                    equalTo(stringKey("trace_flags"), "application-flags"),
                    equalTo(stringKey("baggage.key"), "application-baggage")));
    assertThat(applicationAppender.list)
        .singleElement()
        .satisfies(
            event ->
                assertThat(event.getMDCPropertyMap())
                    .containsEntry("trace_id", span.getSpanContext().getTraceId())
                    .containsEntry("span_id", span.getSpanContext().getSpanId())
                    .containsEntry("trace_flags", span.getSpanContext().getTraceFlags().asHex())
                    .containsEntry("baggage.key", "enriched-baggage")
                    .containsEntry("request-id", "123"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"trace_id", "custom-trace-id"})
  void doesNotExportEnrichmentWithoutApplicationMdc(String traceIdKey) {
    mdcAppender.setTraceIdKey(traceIdKey);
    exporter.setMdcAttributesIncluded("*");
    startExporter(false, "nested");
    Span span = testing.getOpenTelemetry().getTracer("test").spanBuilder("test").startSpan();
    try (Scope ignored = span.makeCurrent()) {
      logger.info("test log");
    } finally {
      span.end();
    }

    testing.waitAndAssertLogRecords(
        log -> log.hasSpanContext(span.getSpanContext()).hasAttributesSatisfyingExactly());
    assertThat(applicationAppender.list)
        .singleElement()
        .satisfies(
            event ->
                assertThat(event.getMDCPropertyMap())
                    .containsEntry(traceIdKey, span.getSpanContext().getTraceId()));
  }

  @ParameterizedTest
  @ValueSource(strings = {"*", "trace_id,span_id,trace_flags,baggage.*"})
  void preservesApplicationOwnedEnrichmentKeys(String included) {
    exporter.setMdcAttributesIncluded(included);
    startExporter(false, "nested");
    MDC.put("trace_id", "application-trace");
    MDC.put("span_id", "application-span");
    MDC.put("trace_flags", "application-flags");
    MDC.put("baggage.key", "application-baggage");
    logger.info("test log");

    testing.waitAndAssertLogRecords(
        log ->
            log.hasAttributesSatisfyingExactly(
                equalTo(stringKey("trace_id"), "application-trace"),
                equalTo(stringKey("span_id"), "application-span"),
                equalTo(stringKey("trace_flags"), "application-flags"),
                equalTo(stringKey("baggage.key"), "application-baggage")));
  }

  @Test
  void preservesLoggerContextProperties() {
    logger.getLoggerContext().putProperty("context-key", "context-value");
    exporter.setLoggerContextAttributesIncluded(
        "context-key,trace_id,span_id,trace_flags,baggage.*");
    startExporter(false, "nested");
    Span span = testing.getOpenTelemetry().getTracer("test").spanBuilder("test").startSpan();
    try (Scope ignored = span.makeCurrent();
        Scope ignoredBaggage =
            Baggage.builder().put("key", "enriched-baggage").build().makeCurrent()) {
      logger.info("test log");
    } finally {
      span.end();
    }

    testing.waitAndAssertLogRecords(
        log ->
            log.hasAttributesSatisfyingExactly(equalTo(stringKey("context-key"), "context-value")));
    assertThat(applicationAppender.list)
        .singleElement()
        .satisfies(
            event ->
                assertThat(event.getLoggerContextVO().getPropertyMap())
                    .containsEntry("context-key", "context-value")
                    .doesNotContainKeys("trace_id", "span_id", "trace_flags", "baggage.key"));
  }

  private void startExporter(boolean replay, String position) {
    exporter.start();
    if (!replay) {
      exporter.setOpenTelemetry(testing.getOpenTelemetry());
    }
    if (position.equals("before")) {
      logger.addAppender(exporter);
    }
    logger.addAppender(mdcAppender);
    if (position.equals("after")) {
      logger.addAppender(exporter);
    } else if (position.equals("nested")) {
      mdcAppender.addAppender(exporter);
    }
  }
}
