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
import org.apache.log4j.spi.LoggingEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.RegisterExtension;

@EnabledIfSystemProperty(
    named = "otel.instrumentation.logging.pattern-injection.enabled",
    matches = "true")
class ResourceCollisionTest {
  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void wholeMdcAndKeyedMdcUseTheSameCorrelationValues() {
    PatternLayout wholeMdc = new PatternLayout("%X %m%n");
    PatternLayout keyedMdc = new PatternLayout("%X{trace_id}|%X{span_id}|%X{trace_flags} %m%n");
    Logger logger = Logger.getLogger("resource-collision");
    ListAppender appender = new ListAppender();
    boolean additive = logger.getAdditivity();
    logger.setAdditivity(false);
    logger.addAppender(appender);
    ListAppender.clearEvents();
    try {
      logger.info("outside");
      assertCorrelation(ListAppender.getEvents().get(0), wholeMdc, keyedMdc, null, null, null);
      testing.runWithSpan(
          "correlation",
          () -> {
            logger.info("inside");
            assertCorrelation(
                ListAppender.getEvents().get(1),
                wholeMdc,
                keyedMdc,
                Span.current().getSpanContext().getTraceId(),
                Span.current().getSpanContext().getSpanId(),
                Span.current().getSpanContext().getTraceFlags().asHex());
          });
      MDC.put("trace_id", "application-trace");
      MDC.put("span_id", "application-span");
      MDC.put("trace_flags", "application-flags");
      testing.runWithSpan(
          "application",
          () -> {
            logger.info("override");
            assertCorrelation(
                ListAppender.getEvents().get(2),
                wholeMdc,
                keyedMdc,
                "application-trace",
                "application-span",
                "application-flags");
          });
    } finally {
      MDC.remove("trace_id");
      MDC.remove("span_id");
      MDC.remove("trace_flags");
      logger.removeAppender(appender);
      logger.setAdditivity(additive);
      ListAppender.clearEvents();
    }
  }

  private static void assertCorrelation(
      LoggingEvent event,
      PatternLayout wholeMdc,
      PatternLayout keyedMdc,
      String trace,
      String span,
      String flags) {
    assertThat(event.getMDC("trace_id")).isEqualTo(trace);
    assertThat(event.getMDC("span_id")).isEqualTo(span);
    assertThat(event.getMDC("trace_flags")).isEqualTo(flags);
    String keyed = keyedMdc.format(event);
    assertThat(keyed).doesNotContain("resource-trace", "resource-span", "resource-flags");
    if (testLatestDeps()) {
      String whole = wholeMdc.format(event);
      assertThat(whole)
          .doesNotContain("resource-trace", "resource-span", "resource-flags")
          .contains("service.name,resource-service");
      if (trace != null) {
        assertThat(whole).contains(trace, span, flags);
        assertThat(keyed).contains(trace, span, flags);
      }
    }
  }
}
