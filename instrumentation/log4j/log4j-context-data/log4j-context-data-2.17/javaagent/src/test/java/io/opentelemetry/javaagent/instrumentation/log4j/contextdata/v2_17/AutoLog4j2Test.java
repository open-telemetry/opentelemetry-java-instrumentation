/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.log4j.contextdata.v2_17;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.log4j.contextdata.ListAppender;
import io.opentelemetry.instrumentation.log4j.contextdata.Log4j2Test;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class AutoLog4j2Test extends Log4j2Test {
  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Override
  public InstrumentationExtension getInstrumentationExtension() {
    return testing;
  }

  @Test
  void testResourceAttributes() {
    Logger logger = LogManager.getLogger("TestLogger");

    logger.info("log message 1");

    List<ListAppender.LoggedEvent> events = ListAppender.get().getEvents();

    assertThat(events.size()).isEqualTo(1);
    assertThat(events.get(0).getMessage()).isEqualTo("log message 1");
    assertThat(events.get(0).getContextData().get("trace_id")).isNull();
    assertThat(events.get(0).getContextData().get("span_id")).isNull();
    assertThat(events.get(0).getContextData().get("service.name"))
        .isEqualTo("unknown_service:java");
    assertThat(events.get(0).getContextData().get("telemetry.sdk.language")).isEqualTo("java");
  }
}
