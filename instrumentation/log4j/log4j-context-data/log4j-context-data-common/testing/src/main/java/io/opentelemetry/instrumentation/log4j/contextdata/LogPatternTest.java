/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.log4j.contextdata;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.core.pattern.PatternFormatter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public abstract class LogPatternTest {
  private static final boolean ENABLED =
      Boolean.parseBoolean(
          System.getProperty(
              "otel.instrumentation.logging.pattern-injection.enabled",
              System.getenv("OTEL_INSTRUMENTATION_LOGGING_PATTERN_INJECTION_ENABLED")));
  private static final String TRACE =
      System.getProperty("otel.instrumentation.common.logging.trace-id-key", "trace_id");
  private static final String SPAN =
      System.getProperty("otel.instrumentation.common.logging.span-id-key", "span_id");

  protected abstract InstrumentationExtension testing();

  @Test
  void leavesPatternSelectorsUntouched() {
    PatternLayout layout =
        PatternLayout.newBuilder()
            .withPattern("%d %m%n")
            .withPatternSelector(event -> new PatternFormatter[0])
            .build();
    assertThat(layout.getConversionPattern()).isEqualTo("%d %m%n");
  }

  @Test
  void rendersCorrelationAndPreservesApplicationValues() {
    PatternLayout.Builder builder =
        PatternLayout.newBuilder().withPattern("%d{yyyy-MM-dd HH:mm:ss} %m%n");
    PatternLayout layout = builder.build();
    assertThat(builder.build().getConversionPattern()).isEqualTo(layout.getConversionPattern());
    Logger logger = (Logger) LogManager.getLogger("TestLogger");
    List<String> messages = new ArrayList<>();
    AbstractAppender appender =
        new AbstractAppender("pattern-test", null, layout, true) {
          @Override
          public void append(LogEvent event) {
            messages.add(layout.toSerializable(event));
          }
        };
    appender.start();
    logger.addAppender(appender);
    try {
      logger.info("outside");
      if (ENABLED) {
        assertThat(messages.get(0))
            .contains("service.name=pattern-service", TRACE + "= ", SPAN + "= outside");
      } else {
        assertThat(layout.getConversionPattern()).isEqualTo("%d{yyyy-MM-dd HH:mm:ss} %m%n");
      }
      testing()
          .runWithSpan(
              "log",
              () -> {
                logger.info("inside");
                if (ENABLED) {
                  assertThat(messages.get(1))
                      .contains(
                          TRACE + "=" + Span.current().getSpanContext().getTraceId(),
                          SPAN + "=" + Span.current().getSpanContext().getSpanId());
                }
              });
      ThreadContext.put("service.name", "application-service");
      ThreadContext.put(SPAN, "application-span");
      testing().runWithSpan("override", () -> logger.info("application"));
      if (ENABLED) {
        assertThat(messages.get(2))
            .contains("service.name=application-service", SPAN + "=application-span");
      }
    } finally {
      ThreadContext.clearAll();
      logger.removeAppender(appender);
      appender.stop();
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"%d( %-5p %m%n", "%d( %-5p %m)%n"})
  void rendersLiteralParenthesesOnOneLine(String pattern) {
    assertRenderedPattern(
        pattern,
        (layout, rendered) -> {
          assertThat(rendered).endsWith(System.lineSeparator());
          assertThat(rendered.split("\\R")).hasSize(1);
          if (ENABLED) {
            assertThat(rendered)
                .contains(
                    "service.name=pattern-service",
                    TRACE + "=" + Span.current().getSpanContext().getTraceId(),
                    SPAN + "=" + Span.current().getSpanContext().getSpanId());
            assertThat(rendered.indexOf(TRACE + "="))
                .isLessThan(rendered.indexOf("pattern message"));
          }
        });
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void rendersColorWrappedMdcWithoutDuplicates(boolean wholeMdc) {
    String pattern = "%d %cyan{%X" + (wholeMdc ? "" : "{" + TRACE + "}") + "} %m%n";
    assertRenderedPattern(
        pattern,
        (layout, rendered) -> {
          assertThat(rendered).containsOnlyOnce(Span.current().getSpanContext().getTraceId());
          if (ENABLED) {
            assertThat(rendered).containsOnlyOnce(Span.current().getSpanContext().getSpanId());
            if (wholeMdc) {
              assertThat(layout.getConversionPattern()).isEqualTo(pattern);
              assertThat(rendered).doesNotContain("otel.pattern-injection.");
            }
          }
        });
  }

  @Test
  void preservesNativeMdcKeyLists() {
    ThreadContext.put("first", "first-value");
    ThreadContext.put("second", "second-value");
    ThreadContext.put("otel.pattern-injection.log4j2.trace.app", "application-placeholder");
    try {
      assertRenderedPattern(
          "%d %X{first,second} %X{otel.pattern-injection.log4j2.trace.app} %m%n",
          (layout, rendered) -> {
            assertThat(rendered)
                .containsOnlyOnce("first=first-value")
                .containsOnlyOnce("second=second-value")
                .containsOnlyOnce("application-placeholder");
            if (ENABLED) {
              assertThat(rendered)
                  .containsOnlyOnce(Span.current().getSpanContext().getTraceId())
                  .containsOnlyOnce(Span.current().getSpanContext().getSpanId());
            }
          });
    } finally {
      ThreadContext.clearAll();
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {" trace_id ", ",trace_id", ""})
  void preservesNativeSingleMdcKeys(String key) {
    ThreadContext.put(key, "application-value");
    try {
      assertRenderedPattern(
          "%d %X{" + key + "} %m%n",
          (layout, rendered) -> {
            assertThat(rendered).containsOnlyOnce("application-value");
            if (ENABLED) {
              assertThat(rendered)
                  .contains("service.name=pattern-service")
                  .containsOnlyOnce(Span.current().getSpanContext().getTraceId())
                  .containsOnlyOnce(Span.current().getSpanContext().getSpanId());
            }
          });
    } finally {
      ThreadContext.remove(key);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"maxLen", "maxLength", "notEmpty", "varsNotEmpty", "variablesNotEmpty"})
  void rendersNativeWrapperAliasesWithoutDuplicates(String converter) {
    String options = converter.startsWith("max") ? "{100}" : "";
    assertRenderedPattern(
        "%d %" + converter + "{%X{" + TRACE + "}}" + options + " %m%n",
        (layout, rendered) -> {
          assertThat(rendered).containsOnlyOnce(Span.current().getSpanContext().getTraceId());
          if (ENABLED) {
            assertThat(rendered).containsOnlyOnce(Span.current().getSpanContext().getSpanId());
          }
        });
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"%highlight{%d{HH:mm:ss} %-5p %m%n}", "%cyan{%d %m%n}", "%style{%d %m%n}{red}"})
  void keepsWholeLineWrappersOnOneLine(String pattern) {
    assertRenderedPattern(
        pattern,
        (layout, rendered) -> {
          String plain = rendered.replaceAll("\u001B\\[[;\\d]*m", "");
          assertThat(plain).contains("pattern message").endsWith(System.lineSeparator());
          assertThat(plain.split("\\R")).hasSize(1);
          if (ENABLED) {
            assertThat(plain)
                .contains("service.name=pattern-service")
                .containsOnlyOnce(Span.current().getSpanContext().getTraceId())
                .containsOnlyOnce(Span.current().getSpanContext().getSpanId());
          }
        });
  }

  private void assertRenderedPattern(String pattern, BiConsumer<PatternLayout, String> assertion) {
    PatternLayout layout = PatternLayout.newBuilder().withPattern(pattern).build();
    Logger logger = (Logger) LogManager.getLogger("TestLogger");
    List<String> messages = new ArrayList<>();
    AbstractAppender appender =
        new AbstractAppender("pattern-regression", null, layout, true) {
          @Override
          public void append(LogEvent event) {
            messages.add(layout.toSerializable(event));
          }
        };
    appender.start();
    logger.addAppender(appender);
    try {
      testing()
          .runWithSpan(
              "log",
              () -> {
                logger.info("pattern message");
                assertion.accept(layout, messages.get(0));
              });
    } finally {
      logger.removeAppender(appender);
      appender.stop();
    }
  }
}
