/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.log4j.v2_13_2;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.opentelemetry.sdk.logs.LogBuilder;
import io.opentelemetry.sdk.logs.LogEmitter;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.core.LogEvent;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OpenTelemetryAppenderTest {

  private OpenTelemetryAppender appender;
  private LogEvent logEvent;
  private LogBuilder logBuilder;
  private LogEmitter logEmitter;

  @AfterAll
  static void afterAll() {
    OpenTelemetryLog4j.resetForTest();
  }

  @BeforeEach
  void setup() {
    appender = OpenTelemetryAppender.newBuilder().setName("my-otel-appender").build();

    logEvent = mock(LogEvent.class);
    when(logEvent.getContextStack()).thenReturn(mock(ThreadContext.ContextStack.class));

    logBuilder = mock(LogBuilder.class);
    when(logBuilder.setEpoch(anyLong(), any())).thenReturn(logBuilder);
    when(logBuilder.setEpoch(any())).thenReturn(logBuilder);
    when(logBuilder.setContext(any())).thenReturn(logBuilder);
    when(logBuilder.setSeverity(any())).thenReturn(logBuilder);
    when(logBuilder.setSeverityText(any())).thenReturn(logBuilder);
    when(logBuilder.setName(any())).thenReturn(logBuilder);
    when(logBuilder.setBody(any())).thenReturn(logBuilder);
    when(logBuilder.setAttributes(any())).thenReturn(logBuilder);

    logEmitter = mock(LogEmitter.class);
    when(logEmitter.logBuilder()).thenReturn(logBuilder);
  }

  @Test
  void append_Uninitialized() {
    appender.append(logEvent);

    verify(logEmitter, never()).logBuilder();
  }

  @Test
  void append_Initialized() {
    appender.initialize(logEmitter);
    appender.append(logEvent);

    verify(logEmitter).logBuilder();
    verify(logBuilder).emit();
  }
}
