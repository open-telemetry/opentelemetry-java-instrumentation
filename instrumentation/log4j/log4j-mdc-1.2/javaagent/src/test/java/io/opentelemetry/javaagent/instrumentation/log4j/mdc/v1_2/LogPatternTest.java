/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.log4j.mdc.v1_2;

import static io.opentelemetry.instrumentation.testing.util.TestLatestDeps.testLatestDeps;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.apache.log4j.Logger;
import org.apache.log4j.MDC;
import org.apache.log4j.PatternLayout;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class LogPatternTest {
  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static final boolean ENABLED =
      Boolean.parseBoolean(
          System.getProperty(
              "otel.instrumentation.logging.pattern-injection.enabled",
              System.getenv("OTEL_INSTRUMENTATION_LOGGING_PATTERN_INJECTION_ENABLED")));
  private static final String TRACE =
      System.getProperty("otel.instrumentation.common.logging.trace-id-key", "trace_id");
  private static final String SPAN =
      System.getProperty("otel.instrumentation.common.logging.span-id-key", "span_id");

  @Test
  void rendersCorrelationAndPreservesApplicationValues() {
    PatternLayout layout = new PatternLayout("%d{yyyy-MM-dd HH:mm:ss} %m%n");
    PatternLayout wholeMdc = new PatternLayout("%d{yyyy-MM-dd HH:mm:ss} %X %m%n");
    if (ENABLED && testLatestDeps()) {
      assertThat(wholeMdc.getConversionPattern()).isEqualTo("%d{yyyy-MM-dd HH:mm:ss} %X %m%n");
    }
    PatternLayout control = new PatternLayout("%X{otel.pattern-injection.log4j1.trace.app}");
    String pattern = layout.getConversionPattern();
    layout.setConversionPattern(pattern);
    layout.activateOptions();
    assertThat(layout.getConversionPattern()).isEqualTo(pattern);
    Logger logger = Logger.getLogger("pattern-test");
    ListAppender appender = new ListAppender();
    boolean additive = logger.getAdditivity();
    logger.setAdditivity(false);
    logger.addAppender(appender);
    ListAppender.clearEvents();
    try {
      logger.info("outside");
      String outside = layout.format(ListAppender.getEvents().get(0));
      if (ENABLED) {
        assertThat(wholeMdc.format(ListAppender.getEvents().get(0))).contains("pattern-service");
        assertThat(outside)
            .contains("service.name=pattern-service", TRACE + "= ", SPAN + "= outside");
      } else {
        assertThat(layout.getConversionPattern()).isEqualTo("%d{yyyy-MM-dd HH:mm:ss} %m%n");
      }
      testing.runWithSpan(
          "log",
          () -> {
            logger.info("inside");
            if (ENABLED) {
              assertThat(wholeMdc.format(ListAppender.getEvents().get(1)))
                  .contains(
                      Span.current().getSpanContext().getTraceId(),
                      Span.current().getSpanContext().getSpanId());
              assertThat(layout.format(ListAppender.getEvents().get(1)))
                  .contains(
                      TRACE + "=" + Span.current().getSpanContext().getTraceId(),
                      SPAN + "=" + Span.current().getSpanContext().getSpanId());
            }
          });
      MDC.put("otel.pattern-injection.log4j1.trace.app", "application-placeholder");
      MDC.put("service.name", "application-service");
      MDC.put(SPAN, "application-span");
      testing.runWithSpan("override", () -> logger.info("application"));
      assertThat(control.format(ListAppender.getEvents().get(2)))
          .containsOnlyOnce("application-placeholder");
      if (ENABLED) {
        assertThat(layout.format(ListAppender.getEvents().get(2)))
            .contains("service.name=application-service", SPAN + "=application-span");
      }
    } finally {
      MDC.remove("otel.pattern-injection.log4j1.trace.app");
      MDC.remove("service.name");
      MDC.remove(SPAN);
      logger.removeAppender(appender);
      logger.setAdditivity(additive);
      ListAppender.clearEvents();
    }
  }
}
