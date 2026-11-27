/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.log4j.contextdata.v2_17;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.opentelemetry.instrumentation.log4j.contextdata.LogPatternTest;
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
import org.apache.logging.log4j.spi.ObjectThreadContextMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class AutoLogPatternTest extends LogPatternTest {
  @RegisterExtension
  static final InstrumentationExtension agent = AgentInstrumentationExtension.create();

  @Test
  void preservesObjectValuedApplicationMdc() {
    String key = System.getProperty("otel.instrumentation.common.logging.span-id-key", "span_id");
    assumeTrue(ThreadContext.getThreadContextMap() instanceof ObjectThreadContextMap);
    ObjectThreadContextMap contextMap =
        (ObjectThreadContextMap) ThreadContext.getThreadContextMap();
    contextMap.putValue(key, 42);
    PatternLayout layout = PatternLayout.newBuilder().withPattern("%X %m%n").build();
    List<Object> values = new ArrayList<>();
    List<String> messages = new ArrayList<>();
    AbstractAppender appender =
        new AbstractAppender("object-mdc", null, layout, true) {
          @Override
          public void append(LogEvent event) {
            values.add(event.getContextData().getValue(key));
            messages.add(layout.toSerializable(event));
          }
        };
    Logger logger = (Logger) LogManager.getLogger("TestLogger");
    appender.start();
    logger.addAppender(appender);
    try {
      agent.runWithSpan("object-mdc", () -> logger.info("object message"));
      assertThat(values).containsExactly(42);
      assertThat(messages).hasSize(1);
      assertThat(messages.get(0)).containsOnlyOnce(key + "=42").contains("object message");
    } finally {
      ThreadContext.clearAll();
      logger.removeAppender(appender);
      appender.stop();
    }
  }

  @Override
  protected InstrumentationExtension testing() {
    return agent;
  }
}
