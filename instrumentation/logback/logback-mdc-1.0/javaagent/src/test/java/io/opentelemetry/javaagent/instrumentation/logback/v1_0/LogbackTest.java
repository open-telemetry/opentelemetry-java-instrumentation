/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.logback.v1_0;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.spi.ILoggingEvent;
import io.opentelemetry.instrumentation.logback.mdc.v1_0.AbstractLogbackTest;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class LogbackTest extends AbstractLogbackTest {

  @RegisterExtension
  static InstrumentationExtension agentTesting = AgentInstrumentationExtension.create();

  @Override
  public InstrumentationExtension getInstrumentationExtension() {
    return agentTesting;
  }

  @Test
  void resourceAttributes() {
    logger.info("log message 1");

    List<ILoggingEvent> events = listAppender.list;

    assertThat(events).hasSize(1);
    ILoggingEvent event = events.get(0);
    assertThat(event.getMessage()).isEqualTo("log message 1");
    assertThat(event.getMDCPropertyMap().get("trace_id")).isNull();
    assertThat(event.getMDCPropertyMap().get("span_id")).isNull();
    assertThat(event.getMDCPropertyMap().get("trace_flags")).isNull();
    assertThat(event.getMDCPropertyMap().get("service.name")).isEqualTo("unknown_service:java");
    assertThat(event.getMDCPropertyMap().get("telemetry.sdk.language")).isEqualTo("java");
  }
}
