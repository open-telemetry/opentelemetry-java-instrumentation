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
        PatternLayout.newBuilder().withPattern("%d{yyyy-MM-dd HH:mm:ss} %-5p [%t] prefix: %m%n");
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
        assertThat(layout.getConversionPattern())
            .startsWith("%d{yyyy-MM-dd HH:mm:ss} %-5p [%t] ")
            .endsWith(" prefix: %m%n");
        assertThat(layout.getConversionPattern()).doesNotContain("service.name");
        assertThat(messages.get(0)).contains(TRACE + "= ", SPAN + "= prefix: outside");
      } else {
        assertThat(layout.getConversionPattern())
            .isEqualTo("%d{yyyy-MM-dd HH:mm:ss} %-5p [%t] prefix: %m%n");
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
            .contains(SPAN + "=application-span")
            .doesNotContain("service.name=");
      }
    } finally {
      ThreadContext.clearAll();
      logger.removeAppender(appender);
      appender.stop();
    }
  }

  @Test
  void preservesExplicitServiceNameConverter() {
    ThreadContext.put("service.name", "application-service");
    try {
      assertRenderedPattern(
          "%d %X{service.name} %m%n",
          (layout, rendered) -> {
            assertThat(rendered).containsOnlyOnce("application-service");
            assertThat(layout.getConversionPattern()).containsOnlyOnce("%X{service.name}");
          });
    } finally {
      ThreadContext.remove("service.name");
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
    ThreadContext.put("app.key", "application-value");
    try {
      assertRenderedPattern(
          "%d %X{first,second} %X{app.key} %m%n",
          (layout, rendered) -> {
            assertThat(rendered)
                .containsOnlyOnce("first=first-value")
                .containsOnlyOnce("second=second-value")
                .containsOnlyOnce("application-value");
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
  @ValueSource(strings = {"Suffix", "_suffix"})
  void injectsBeforeMessageConverterWithLiteralSuffix(String suffix) {
    assertRenderedPattern(
        "%msg" + suffix + "%n",
        (layout, rendered) -> {
          assertThat(rendered).endsWith("pattern message" + suffix + System.lineSeparator());
          if (ENABLED) {
            assertThat(rendered)
                .startsWith(
                    TRACE
                        + "="
                        + Span.current().getSpanContext().getTraceId()
                        + " "
                        + SPAN
                        + "="
                        + Span.current().getSpanContext().getSpanId()
                        + " ");
          }
        });
  }

  @Test
  void reusesKeyedMdcConverterWithLiteralSuffix() {
    assertRenderedPattern(
        "%Xtail{" + TRACE + "} %m%n",
        (layout, rendered) -> {
          String traceId = Span.current().getSpanContext().getTraceId();
          assertThat(rendered).startsWith(traceId + "tail ").containsOnlyOnce(traceId);
          if (ENABLED) {
            assertThat(rendered)
                .containsOnlyOnce(SPAN + "=" + Span.current().getSpanContext().getSpanId());
          }
        });
  }

  @Test
  void leavesRegisteredNonMessageConverterUntouched() {
    assertRenderedPattern(
        "%marker%n", (layout, rendered) -> assertThat(rendered).isEqualTo(System.lineSeparator()));
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
                  .doesNotContain("service.name=")
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
                .doesNotContain("service.name=")
                .containsOnlyOnce(Span.current().getSpanContext().getTraceId())
                .containsOnlyOnce(Span.current().getSpanContext().getSpanId());
          }
        });
  }

  @Test
  void rebuildsLayoutFromOriginalConversionPattern() {
    String pattern = "%d %-5p prefix: %m%n";
    PatternLayout first = PatternLayout.newBuilder().withPattern(pattern).build();
    PatternLayout rebuilt =
        PatternLayout.newBuilder().withPattern(first.getConversionPattern()).build();
    assertRenderedPattern(
        rebuilt,
        (layout, rendered) -> {
          if (ENABLED) {
            assertThat(rendered)
                .containsOnlyOnce(TRACE + "=")
                .containsOnlyOnce(SPAN + "=")
                .containsOnlyOnce(Span.current().getSpanContext().getTraceId())
                .containsOnlyOnce(Span.current().getSpanContext().getSpanId());
          }
        });
    assertThat(first.getConversionPattern()).isEqualTo(pattern);
    assertThat(rebuilt.getConversionPattern()).isEqualTo(pattern);
  }

  @ParameterizedTest
  @ValueSource(strings = {"maxLen", "maxLength"})
  void preservesMessageLengthBudget(String converter) {
    assertRenderedPattern(
        "%" + converter + "{%p %m}{20}%n",
        (layout, rendered) -> {
          assertThat(rendered).endsWith("INFO pattern message" + System.lineSeparator());
          if (ENABLED) {
            assertThat(rendered)
                .containsOnlyOnce(Span.current().getSpanContext().getTraceId())
                .containsOnlyOnce(Span.current().getSpanContext().getSpanId());
          }
        });
  }

  @ParameterizedTest
  @ValueSource(strings = {"equals", "equalsIgnoreCase"})
  void preservesEqualsComparison(String converter) {
    assertRenderedPattern(
        "%" + converter + "{%m}{pattern message}{pong}%n",
        (layout, rendered) -> {
          assertThat(rendered).endsWith("pong" + System.lineSeparator());
          if (ENABLED) {
            assertThat(rendered)
                .containsOnlyOnce(Span.current().getSpanContext().getTraceId())
                .containsOnlyOnce(Span.current().getSpanContext().getSpanId());
          }
        });
  }

  @ParameterizedTest
  @ValueSource(strings = {"notEmpty", "varsNotEmpty", "variablesNotEmpty"})
  void keepsEmptyConditionalMessageSuppressed(String converter) {
    String pattern = "%" + converter + "{%X{request_id} msg=%m}%n";
    assertRenderedPattern(
        PatternLayout.newBuilder().withPattern(pattern).build(),
        "",
        (layout, rendered) -> {
          if (ENABLED) {
            assertThat(rendered)
                .isEqualTo(
                    TRACE
                        + "="
                        + Span.current().getSpanContext().getTraceId()
                        + " "
                        + SPAN
                        + "="
                        + Span.current().getSpanContext().getSpanId()
                        + " "
                        + System.lineSeparator());
          } else {
            assertThat(rendered).isEqualTo(System.lineSeparator());
          }
        });
  }

  @ParameterizedTest
  @ValueSource(strings = {"%X{trace_id}", "%X"})
  void recognizesMdcInsideEquals(String mdc) {
    assertRenderedPattern(
        "%equals{" + mdc + "}{missing}{-} %m%n",
        (layout, rendered) -> {
          assertThat(rendered).contains("pattern message");
          if (ENABLED) {
            assertThat(rendered)
                .containsOnlyOnce(Span.current().getSpanContext().getTraceId())
                .containsOnlyOnce(Span.current().getSpanContext().getSpanId());
          }
        });
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "%.-40highlight{%p %m}%n",
        "%replace{%p %m}{[0-9]}{X}%n",
        "%-50highlight{%p %m}%n"
      })
  void preservesWrapperMessageAndCorrelation(String pattern) {
    assertRenderedPattern(
        pattern,
        (layout, rendered) -> {
          assertThat(rendered).contains("pattern message").endsWith(System.lineSeparator());
          if (ENABLED) {
            assertThat(rendered)
                .containsOnlyOnce(Span.current().getSpanContext().getTraceId())
                .containsOnlyOnce(Span.current().getSpanContext().getSpanId());
          }
        });
  }

  private void assertRenderedPattern(String pattern, BiConsumer<PatternLayout, String> assertion) {
    assertRenderedPattern(PatternLayout.newBuilder().withPattern(pattern).build(), assertion);
  }

  private void assertRenderedPattern(
      PatternLayout layout, BiConsumer<PatternLayout, String> assertion) {
    assertRenderedPattern(layout, "pattern message", assertion);
  }

  private void assertRenderedPattern(
      PatternLayout layout, String message, BiConsumer<PatternLayout, String> assertion) {
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
                logger.info(message);
                assertion.accept(layout, messages.get(0));
              });
    } finally {
      logger.removeAppender(appender);
      appender.stop();
    }
  }
}
