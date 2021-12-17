/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.log4j.v2_16;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.sdk.logs.SdkLogEmitterProvider;
import io.opentelemetry.sdk.logs.export.InMemoryLogExporter;
import io.opentelemetry.sdk.logs.export.SimpleLogProcessor;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.core.LogEvent;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OpenTelemetryAppenderTest {

  private OpenTelemetryAppender appender;
  private InMemoryLogExporter logExporter;
  private SdkLogEmitterProvider logEmitterProvider;
  private LogEvent logEvent;

  @AfterAll
  static void afterAll() {
    OpenTelemetryLog4j.resetForTest();
  }

  @BeforeEach
  void setup() {
    appender = OpenTelemetryAppender.builder().setName("my-otel-appender").build();

    logEvent = mock(LogEvent.class);
    when(logEvent.getContextStack()).thenReturn(mock(ThreadContext.ContextStack.class));

    logExporter = InMemoryLogExporter.create();
    logEmitterProvider =
        SdkLogEmitterProvider.builder()
            .addLogProcessor(SimpleLogProcessor.create(logExporter))
            .build();
  }

  @Test
  void append_Uninitialized() {
    appender.append(logEvent);

    assertThat(logExporter.getFinishedLogItems()).isEmpty();
  }

  @Test
  void append_Initialized() {
    appender.initialize(logEmitterProvider);
    appender.append(logEvent);

    assertThat(logExporter.getFinishedLogItems()).hasSize(1);
  }
}
