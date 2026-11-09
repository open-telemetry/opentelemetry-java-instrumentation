/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.logback.v1_0;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

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
    LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
    Logger logger = context.getLogger("pattern-test");
    boolean additive = logger.isAdditive();
    Level level = logger.getLevel();
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    try {
      PatternLayout layout = new PatternLayout();
      layout.setContext(context);
      layout.setPattern("%d{yyyy-MM-dd HH:mm:ss} %m%n");
      layout.start();
      PatternLayout quoted = new PatternLayout();
      quoted.setContext(context);
      quoted.setPattern("%d{HH:mm:ss} %X{'" + TRACE + "'} %m%n");
      quoted.start();
      if (ENABLED) {
        assertThat(quoted.getPattern()).doesNotContain(TRACE + "=%X{");
      }
      String enhanced = layout.getPattern();
      layout.stop();
      layout.start();
      assertThat(layout.getPattern()).isEqualTo(enhanced);
      appender.setContext(context);
      appender.start();
      logger.setLevel(Level.INFO);
      logger.setAdditive(false);
      logger.addAppender(appender);
      logger.info("outside");
      String outside = layout.doLayout(appender.list.get(0));
      if (ENABLED) {
        assertThat(outside)
            .contains("service.name=pattern-service", TRACE + "= ", SPAN + "= outside");
      } else {
        assertThat(outside).matches("\\d{4}-\\d{2}-\\d{2} .* outside\\R");
        assertThat(layout.getPattern()).isEqualTo("%d{yyyy-MM-dd HH:mm:ss} %m%n");
      }
      testing.runWithSpan(
          "log",
          () -> {
            logger.info("inside");
            String text = layout.doLayout(appender.list.get(1));
            if (ENABLED) {
              assertThat(quoted.doLayout(appender.list.get(1)))
                  .containsOnlyOnce(Span.current().getSpanContext().getTraceId());
              assertThat(text)
                  .contains(
                      TRACE + "=" + Span.current().getSpanContext().getTraceId(),
                      SPAN + "=" + Span.current().getSpanContext().getSpanId());
            }
          });
      MDC.put("service.name", "application-service");
      MDC.put(SPAN, "application-span");
      testing.runWithSpan("override", () -> logger.info("application"));
      if (ENABLED) {
        assertThat(layout.doLayout(appender.list.get(2)))
            .contains("service.name=application-service", SPAN + "=application-span");
      }
    } finally {
      MDC.clear();
      logger.detachAppender(appender);
      logger.setAdditive(additive);
      logger.setLevel(level);
      appender.stop();
    }
  }
}
