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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.core.pattern.PatternFormatter;
import org.junit.jupiter.api.Test;

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
}
