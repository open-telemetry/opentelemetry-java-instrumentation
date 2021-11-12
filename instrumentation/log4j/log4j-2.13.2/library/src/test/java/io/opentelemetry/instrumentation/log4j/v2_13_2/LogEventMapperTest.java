/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.log4j.v2_13_2;

import static io.opentelemetry.instrumentation.api.log.LoggingContextConstants.SPAN_ID;
import static io.opentelemetry.instrumentation.api.log.LoggingContextConstants.TRACE_FLAGS;
import static io.opentelemetry.instrumentation.api.log.LoggingContextConstants.TRACE_ID;
import static io.opentelemetry.instrumentation.log4j.v2_13_2.LogEventMapper.ATTR_FQCN;
import static io.opentelemetry.instrumentation.log4j.v2_13_2.LogEventMapper.ATTR_LOGGER_NAME;
import static io.opentelemetry.instrumentation.log4j.v2_13_2.LogEventMapper.ATTR_MARKER;
import static io.opentelemetry.instrumentation.log4j.v2_13_2.LogEventMapper.ATTR_NDC;
import static io.opentelemetry.instrumentation.log4j.v2_13_2.LogEventMapper.ATTR_THREAD_ID;
import static io.opentelemetry.instrumentation.log4j.v2_13_2.LogEventMapper.ATTR_THREAD_NAME;
import static io.opentelemetry.instrumentation.log4j.v2_13_2.LogEventMapper.ATTR_THREAD_PRIORITY;
import static io.opentelemetry.instrumentation.log4j.v2_13_2.LogEventMapper.ATTR_THROWABLE_MESSAGE;
import static io.opentelemetry.instrumentation.log4j.v2_13_2.LogEventMapper.ATTR_THROWABLE_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.logs.LogBuilder;
import io.opentelemetry.sdk.logs.data.Severity;
import io.opentelemetry.sdk.trace.IdGenerator;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.MarkerManager;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.spi.DefaultThreadContextStack;
import org.apache.logging.log4j.spi.ThreadContextStack;
import org.apache.logging.log4j.util.ReadOnlyStringMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LogEventMapperTest {

  private LogEvent logEvent;
  private LogBuilder logBuilder;

  @BeforeEach
  void setup() {
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
  }

  @Test
  void mapLogEvent_MinimalFields() {
    LogEventMapper.mapLogEvent(logBuilder, logEvent);

    verify(logBuilder, never()).setBody(any());
    verify(logBuilder, never()).setEpoch(any());
    verify(logBuilder, never()).setSeverity(any());
    verify(logBuilder, never()).setSeverityText(any());
    verify(logBuilder, never()).setName(any());
    verify(logBuilder).setEpoch(0, TimeUnit.NANOSECONDS);
    ArgumentCaptor<Attributes> attributesCaptor = ArgumentCaptor.forClass(Attributes.class);
    verify(logBuilder).setAttributes(attributesCaptor.capture());
    Attributes attributes = attributesCaptor.getValue();
    assertThat(attributes)
        .isEqualTo(
            Attributes.builder().put(ATTR_THREAD_ID, 0).put(ATTR_THREAD_PRIORITY, 0).build());
  }

  @Test
  void mapLogEvent_AllFields() {
    Message message = mock(Message.class);
    when(message.getFormattedMessage()).thenReturn("Message!");
    when(logEvent.getMessage()).thenReturn(message);
    when(logEvent.getNanoTime()).thenReturn(123L);
    when(logEvent.getLevel()).thenReturn(Level.DEBUG);
    when(logEvent.getLoggerName()).thenReturn("my.example.LoggerClassName");
    when(logEvent.getLoggerFqcn()).thenReturn("my.example.FullQualifiedClassName");
    when(logEvent.getThreadName()).thenReturn("my-thread");
    when(logEvent.getThreadId()).thenReturn(2L);
    when(logEvent.getThreadPriority()).thenReturn(1);
    when(logEvent.getThrown()).thenReturn(new IllegalStateException("Error!"));
    when(logEvent.getMarker()).thenReturn(new MarkerManager.Log4jMarker("marker name"));
    ThreadContextStack threadContextStack = new DefaultThreadContextStack(true);
    threadContextStack.add("foo");
    threadContextStack.add("bar");
    when(logEvent.getContextStack()).thenReturn(threadContextStack);
    Map<String, String> contextData = new HashMap<>();
    String traceId = IdGenerator.random().generateTraceId();
    String spanId = IdGenerator.random().generateSpanId();
    contextData.put(TRACE_ID, traceId);
    contextData.put(SPAN_ID, spanId);
    contextData.put(TRACE_FLAGS, TraceFlags.getSampled().asHex());
    contextData.put("key", "value");
    ReadOnlyStringMap mockContextData = mock(ReadOnlyStringMap.class);
    when(mockContextData.toMap()).thenReturn(contextData);
    when(logEvent.getContextData()).thenReturn(mockContextData);

    LogEventMapper.mapLogEvent(logBuilder, logEvent);

    verify(logBuilder, never()).setName(any());
    verify(logBuilder, never()).setEpoch(any());
    verify(logBuilder).setBody(eq("Message!"));
    verify(logBuilder).setEpoch(123L, TimeUnit.NANOSECONDS);
    verify(logBuilder).setSeverity(Severity.DEBUG);
    verify(logBuilder).setSeverityText(eq("DEBUG"));
    ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);
    verify(logBuilder).setContext(contextCaptor.capture());
    Context context = contextCaptor.getValue();
    assertThat(Span.fromContext(context).getSpanContext())
        .isEqualTo(
            SpanContext.create(traceId, spanId, TraceFlags.getSampled(), TraceState.getDefault()));
    ArgumentCaptor<Attributes> attributesCaptor = ArgumentCaptor.forClass(Attributes.class);
    verify(logBuilder).setAttributes(attributesCaptor.capture());
    Attributes attributes = attributesCaptor.getValue();
    assertThat(attributes)
        .isEqualTo(
            Attributes.builder()
                .put(ATTR_LOGGER_NAME, "my.example.LoggerClassName")
                .put(ATTR_FQCN, "my.example.FullQualifiedClassName")
                .put(ATTR_THREAD_NAME, "my-thread")
                .put(ATTR_THREAD_ID, 2)
                .put(ATTR_THREAD_PRIORITY, 1)
                .put(ATTR_THROWABLE_NAME, IllegalStateException.class.getName())
                .put(ATTR_THROWABLE_MESSAGE, "Error!")
                .put(ATTR_MARKER, "marker name")
                .put(ATTR_NDC, Arrays.asList("foo", "bar"))
                .put("key", "value")
                .build());
  }
}
