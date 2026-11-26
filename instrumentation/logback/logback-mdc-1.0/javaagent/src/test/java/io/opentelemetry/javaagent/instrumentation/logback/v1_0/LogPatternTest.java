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
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
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
      layout.setPattern("%d{yyyy-MM-dd HH:mm:ss} %-5p [%t] prefix: %m%n");
      layout.start();
      quoted.setContext(context);
      quoted.setPattern("%d{HH:mm:ss} %X{'quoted,key'} %m%n");
      quoted.start();
      control.setContext(context);
      control.setPattern("%X{'app,key'} service.name=%X{service.name}");
      control.start();
      assertThat(layout.isStarted()).isTrue();
      assertThat(quoted.isStarted()).isTrue();
      assertThat(control.isStarted()).isTrue();
      MDC.put("quoted,key", "quoted-value");
      MDC.put("app,key", "application-value");
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
      assertThat(control.doLayout(appender.list.get(0))).containsOnlyOnce("application-value");
      if (ENABLED) {
        assertThat(layout.getPattern())
            .startsWith("%d{yyyy-MM-dd HH:mm:ss} %-5p [%t] ")
            .endsWith(" prefix: %m%n");
        assertThat(layout.getPattern()).doesNotContain("service.name");
        assertThat(outside).contains(TRACE + "= ", SPAN + "= prefix: outside");
      } else {
        assertThat(outside).matches("\\d{4}-\\d{2}-\\d{2} .* outside\\R");
        assertThat(layout.getPattern()).isEqualTo("%d{yyyy-MM-dd HH:mm:ss} %-5p [%t] prefix: %m%n");
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
      assertThat(control.doLayout(appender.list.get(2)))
          .contains("service.name=application-service");
      if (ENABLED) {
        assertThat(layout.doLayout(appender.list.get(2)))
            .contains(SPAN + "=application-span")
            .doesNotContain("service.name=");
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
        "%d %-5p [%t] %msg%n",
        "%d %-5p prefix: %message%n",
        "%d %X{:-fallback} %m%n",
        "%d %X{custom:-trace} %m%n",
        "%d %X{'custom,\\'\"span'} %m%n"
      })
  void respectsNativeMdcKeySemantics(String pattern) {
    assertRenderedPattern(pattern);
  }

  @ParameterizedTest
  @ValueSource(strings = {"%.-20(%p %m)", "%replace(%p %m){'[0-9]','X'}%n", "%-50(%p %m)"})
  void preservesWrapperMessageAndCorrelation(String pattern) {
    assertRenderedPattern(pattern, pattern.endsWith("%n"));
  }

  @Test
  @EnabledIfSystemProperty(
      named = "otel.instrumentation.common.logging.trace-id-key",
      matches = "custom,trace")
  void preservesExplicitConverterForNonPortableKey() {
    LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
    Logger logger = context.getLogger("mixed-keys");
    boolean additive = logger.isAdditive();
    Level level = logger.getLevel();
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    PatternLayout layout = new PatternLayout();
    PatternLayout explicit = new PatternLayout();
    try {
      layout.setContext(context);
      layout.setPattern("%m%n");
      layout.start();
      explicit.setContext(context);
      explicit.setPattern("trace=%X{'custom,trace'} %m%n");
      explicit.start();
      appender.setContext(context);
      appender.start();
      logger.setLevel(Level.INFO);
      logger.setAdditive(false);
      logger.addAppender(appender);
      testing.runWithSpan(
          "mixed-keys",
          () -> {
            logger.info("message");
            ILoggingEvent event = appender.list.get(0);
            String traceId = Span.current().getSpanContext().getTraceId();
            String spanId = Span.current().getSpanContext().getSpanId();
            assertThat(layout.doLayout(event))
                .isEqualTo("otel.span-id_1=" + spanId + " message" + System.lineSeparator());
            assertThat(explicit.doLayout(event))
                .isEqualTo(
                    "trace="
                        + traceId
                        + " otel.span-id_1="
                        + spanId
                        + " message"
                        + System.lineSeparator());
          });
    } finally {
      layout.stop();
      explicit.stop();
      logger.detachAppender(appender);
      logger.setAdditive(additive);
      logger.setLevel(level);
      appender.stop();
    }
  }

  private static void assertRenderedPattern(String pattern) {
    assertRenderedPattern(pattern, true);
  }

  private static void assertRenderedPattern(String pattern, boolean endsWithNewline) {
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
            assertThat(rendered).contains("pattern message");
            if (endsWithNewline) {
              assertThat(rendered).endsWith(System.lineSeparator());
            }
            assertThat(rendered.split("\\R")).hasSize(1);
            if (ENABLED) {
              assertThat(rendered)
                  .doesNotContain("service.name=")
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
