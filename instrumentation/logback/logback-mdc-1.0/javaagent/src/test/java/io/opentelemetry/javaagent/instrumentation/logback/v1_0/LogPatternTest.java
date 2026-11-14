/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.logback.v1_0;

import static io.opentelemetry.instrumentation.testing.util.TestLatestDeps.testLatestDeps;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
    PatternLayout layout = new PatternLayout();
    PatternLayout quoted = new PatternLayout();
    PatternLayout control = new PatternLayout();
    try {
      layout.setContext(context);
      layout.setPattern("%d{yyyy-MM-dd HH:mm:ss} %m%n");
      layout.start();
      quoted.setContext(context);
      quoted.setPattern("%d{HH:mm:ss} %X{'quoted,key'} %m%n");
      quoted.start();
      control.setContext(context);
      control.setPattern("%X{otel.pattern-injection.trace.app}");
      control.start();
      assertThat(layout.isStarted()).isTrue();
      assertThat(quoted.isStarted()).isTrue();
      assertThat(control.isStarted()).isTrue();
      MDC.put("quoted,key", "quoted-value");
      MDC.put("otel.pattern-injection.trace.app", "application-placeholder");
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
      assertThat(quoted.doLayout(appender.list.get(0))).containsOnlyOnce("quoted-value");
      assertThat(control.doLayout(appender.list.get(0)))
          .containsOnlyOnce("application-placeholder");
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
      layout.stop();
      quoted.stop();
      control.stop();
      MDC.clear();
      logger.detachAppender(appender);
      logger.setAdditive(additive);
      logger.setLevel(level);
      appender.stop();
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "%replace(%d %msg%n){'unused','replacement'}",
        "%replace(request={){'unused','replacement'} %m%n",
        "%highlight(%d %msg%n)",
        "%cyan(request={) %m%n"
      })
  void keepsWrappedRecordsOnOneLine(String pattern) {
    assumeTrue(testLatestDeps() || pattern.startsWith("%replace"));
    assertRenderedPattern(pattern);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "%d %X{:-fallback} %m%n",
        "%d %X{custom:-trace} %m%n",
        "%d %X{'custom,\\'\"span'} %m%n"
      })
  void respectsNativeMdcKeySemantics(String pattern) {
    assertRenderedPattern(pattern);
  }

  private static void assertRenderedPattern(String pattern) {
    LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
    Logger logger = context.getLogger("pattern-regression");
    boolean additive = logger.isAdditive();
    Level level = logger.getLevel();
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    PatternLayout layout = new PatternLayout();
    try {
      layout.setContext(context);
      layout.setPattern(pattern);
      layout.start();
      String enhanced = layout.getPattern();
      layout.stop();
      layout.start();
      assertThat(layout.getPattern()).isEqualTo(enhanced);
      appender.setContext(context);
      appender.start();
      logger.setLevel(Level.INFO);
      logger.setAdditive(false);
      logger.addAppender(appender);
      testing.runWithSpan(
          "log",
          () -> {
            logger.info("pattern message");
            String rendered =
                layout.doLayout(appender.list.get(0)).replaceAll("\u001B\\[[;\\d]*m", "");
            assertThat(rendered).contains("pattern message").endsWith(System.lineSeparator());
            assertThat(rendered.split("\\R")).hasSize(1);
            if (ENABLED) {
              assertThat(rendered)
                  .contains("service.name=pattern-service")
                  .containsOnlyOnce(Span.current().getSpanContext().getTraceId())
                  .containsOnlyOnce(Span.current().getSpanContext().getSpanId());
            }
          });
    } finally {
      layout.stop();
      logger.detachAppender(appender);
      logger.setAdditive(additive);
      logger.setLevel(level);
      appender.stop();
    }
  }
}
