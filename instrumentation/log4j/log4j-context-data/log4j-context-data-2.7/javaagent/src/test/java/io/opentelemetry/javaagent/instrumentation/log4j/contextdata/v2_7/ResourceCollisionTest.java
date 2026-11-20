/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.log4j.contextdata.v2_7;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@EnabledIfSystemProperty(named = "otel.resource.attributes", matches = ".*resource-trace.*")
class ResourceCollisionTest {
  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void correlationTakesPrecedenceOverResourceAttributes(boolean applicationSpan) {
    PatternLayout keyed =
        PatternLayout.newBuilder()
            .withPattern("%X{trace_id}|%X{span_id}|%X{trace_flags}|%X{service.name} %m%n")
            .build();
    PatternLayout whole = PatternLayout.newBuilder().withPattern("%X %m%n").build();
    Logger logger = (Logger) LogManager.getLogger("TestLogger");
    List<String> keyedMessages = new ArrayList<>();
    List<String> wholeMessages = new ArrayList<>();
    AbstractAppender appender =
        new AbstractAppender("resource-collision", null, keyed, true) {
          @Override
          public void append(LogEvent event) {
            keyedMessages.add(keyed.toSerializable(event));
            wholeMessages.add(whole.toSerializable(event));
          }
        };
    appender.start();
    logger.addAppender(appender);
    try {
      if (applicationSpan) {
        ThreadContext.put("span_id", "application-span");
      }
      testing.runWithSpan(
          "correlation",
          () -> {
            logger.info("message");
            String trace = Span.current().getSpanContext().getTraceId();
            String span =
                applicationSpan ? "application-span" : Span.current().getSpanContext().getSpanId();
            String flags = Span.current().getSpanContext().getTraceFlags().asHex();
            assertThat(keyedMessages).hasSize(1);
            assertThat(wholeMessages).hasSize(1);
            assertThat(keyedMessages.get(0))
                .startsWith(trace + "|" + span + "|" + flags + "|resource-service ");
            assertThat(wholeMessages.get(0))
                .contains("trace_id=" + trace, "span_id=" + span, "trace_flags=" + flags)
                .doesNotContain("resource-trace", "resource-span", "resource-flags");
          });
    } finally {
      ThreadContext.clearAll();
      logger.removeAppender(appender);
      appender.stop();
    }
  }
}
