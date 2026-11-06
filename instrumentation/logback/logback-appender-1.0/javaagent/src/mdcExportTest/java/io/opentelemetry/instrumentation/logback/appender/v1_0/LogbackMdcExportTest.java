/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.logback.appender.v1_0;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.AppenderBase;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.javaagent.testing.common.TestAgentListenerAccess;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

class LogbackMdcExportTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static final String TRACE_ID_KEY =
      System.getProperty("otel.instrumentation.common.logging.trace-id-key", "trace_id");
  private static final String SPAN_ID_KEY =
      System.getProperty("otel.instrumentation.common.logging.span-id-key", "span_id");
  private static final String TRACE_FLAGS_KEY =
      System.getProperty("otel.instrumentation.common.logging.trace-flags-key", "trace_flags");

  private final Logger logger = (Logger) LoggerFactory.getLogger("mdc-export");
  private final MdcAppender appender = new MdcAppender();

  @BeforeEach
  void setUp() {
    logger.setLevel(Level.INFO);
    logger.setAdditive(false);
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void tearDown() {
    logger.detachAppender(appender);
    appender.stop();
    MDC.clear();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void exportsOnlyApplicationMdc(boolean withSpan) {
    MDC.put("request", "application");
    MDC.put("secret", "excluded");
    Runnable log =
        () -> {
          try (Scope ignored = Baggage.builder().put("foo", "synthetic").build().makeCurrent()) {
            SpanContext spanContext = Span.current().getSpanContext();
            logger.info("message");
            assertEnrichment("message", spanContext);
          }
        };
    if (withSpan) {
      testing.runWithSpan("parent", log::run);
    } else {
      log.run();
    }

    assertExportedMdc("message", Attributes.of(stringKey("request"), "application"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "trace_id",
        "span_id",
        "trace_flags",
        "custom_trace_id",
        "custom_span_id",
        "custom_trace_flags",
        "service.name",
        "baggage.foo"
      })
  void preservesApplicationOwnedKeys(String key) {
    MDC.put(key, "application");
    testing.runWithSpan(
        "parent",
        () -> {
          try (Scope ignored = Baggage.builder().put("foo", "synthetic").build().makeCurrent()) {
            logger.info("collision");
          }
        });

    assertExportedMdc("collision", Attributes.of(stringKey(key), "application"));
    if (key.equals(TRACE_ID_KEY)) {
      // Normal reads retain the already-instrumented-event shortcut.
      assertThat(appender.mdc.get("collision"))
          .containsExactlyEntriesOf(singletonMap(key, "application"));
    }
  }

  @Test
  void nestedLoggingFromMdcGetterStillEnrichesApplicationAppender() {
    LoggingEvent event =
        new LoggingEvent(getClass().getName(), logger, Level.INFO, "outer", null, null) {
          private boolean logged;

          @Override
          public Map<String, String> getMDCPropertyMap() {
            if (!logged) {
              logged = true;
              logger.info("nested");
            }
            return super.getMDCPropertyMap();
          }
        };
    MDC.put("request", "application");
    testing.runWithSpan(
        "parent",
        () -> {
          try (Scope ignored = Baggage.builder().put("foo", "synthetic").build().makeCurrent()) {
            logger.callAppenders(event);
            assertEnrichment("nested", Span.current().getSpanContext());
            assertEnrichment("outer", Span.current().getSpanContext());
          }
        });

    assertThat(appender.mdc).containsOnlyKeys("nested", "outer");
    // Nested capture is suppressed by logging call depth, but its application appender still runs.
    assertExportedMdc("outer", Attributes.of(stringKey("request"), "application"));
  }

  @Test
  void nullMdcCanBeExportedAndNormalReadsStillEnrich() {
    LoggingEvent event =
        new LoggingEvent(getClass().getName(), logger, Level.INFO, "null MDC", null, null) {
          @Override
          @SuppressWarnings("ReturnsNullCollection")
          public Map<String, String> getMDCPropertyMap() {
            return null;
          }
        };
    testing.runWithSpan(
        "parent",
        () -> {
          try (Scope ignored = Baggage.builder().put("foo", "synthetic").build().makeCurrent()) {
            logger.callAppenders(event);
            assertEnrichment("null MDC", Span.current().getSpanContext());
          }
        });

    assertExportedMdc("null MDC", Attributes.empty());
  }

  @Test
  void restoresExportReadsAndCallDepthAfterGetterThrows() {
    LoggingEvent event =
        new LoggingEvent(getClass().getName(), logger, Level.INFO, "failed mapping", null, null) {
          private boolean fail = true;

          @Override
          public Map<String, String> getMDCPropertyMap() {
            if (fail) {
              fail = false;
              throw new IllegalStateException("MDC getter failed");
            }
            return super.getMDCPropertyMap();
          }
        };
    MDC.put("request", "application");
    testing.runWithSpan(
        "parent",
        () -> {
          try (Scope ignored = Baggage.builder().put("foo", "synthetic").build().makeCurrent()) {
            logger.callAppenders(event);
            assertThat(TestAgentListenerAccess.getAndResetAdviceFailureCount()).isEqualTo(1);
            assertEnrichment("failed mapping", Span.current().getSpanContext());
            logger.info("after failure");
            assertEnrichment("after failure", Span.current().getSpanContext());
          }
        });

    assertExportedMdc("after failure", Attributes.of(stringKey("request"), "application"));
  }

  @Test
  @EnabledIfSystemProperty(named = "test.baggageProcessor", matches = "true")
  void exportsBaggageIndependently() {
    MDC.put("request", "excluded");
    try (Scope ignored =
        Baggage.builder().put("tenant", "selected").put("foo", "synthetic").build().makeCurrent()) {
      logger.info("baggage");
    }

    assertThat(appender.mdc.get("baggage"))
        .containsEntry("request", "excluded")
        .containsEntry("baggage.tenant", "selected")
        .containsEntry("baggage.foo", "synthetic");
    assertExportedMdc("baggage", Attributes.of(stringKey("tenant"), "selected"));
  }

  private void assertEnrichment(String message, SpanContext spanContext) {
    Map<String, String> mdc = appender.mdc.get(message);
    assertThat(mdc)
        .containsEntry("service.name", "mdc-export-test")
        .containsEntry("baggage.foo", "synthetic");
    if (spanContext.isValid()) {
      assertThat(mdc)
          .containsEntry(TRACE_ID_KEY, spanContext.getTraceId())
          .containsEntry(SPAN_ID_KEY, spanContext.getSpanId())
          .containsEntry(TRACE_FLAGS_KEY, spanContext.getTraceFlags().asHex());
    } else {
      assertThat(mdc).doesNotContainKeys(TRACE_ID_KEY, SPAN_ID_KEY, TRACE_FLAGS_KEY);
    }
  }

  private static void assertExportedMdc(String message, Attributes expected) {
    testing.waitAndAssertLogRecords(
        record -> {
          record.hasBody(message);
          record.hasAttributes(expected);
        });
  }

  private static class MdcAppender extends AppenderBase<ILoggingEvent> {
    final Map<String, Map<String, String>> mdc = new LinkedHashMap<>();

    @Override
    protected void append(ILoggingEvent event) {
      mdc.put(event.getMessage(), new HashMap<>(event.getMDCPropertyMap()));
    }
  }
}
