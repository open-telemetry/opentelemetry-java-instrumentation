/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.thread.internal;

import static io.opentelemetry.semconv.incubating.ThreadIncubatingAttributes.THREAD_ID;
import static io.opentelemetry.semconv.incubating.ThreadIncubatingAttributes.THREAD_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;
import org.junit.jupiter.api.Test;

class AddThreadDetailsSpanProcessorTest {

  private final ReadWriteSpan span = mock(ReadWriteSpan.class);

  private final SpanProcessor spanProcessor = new AddThreadDetailsSpanProcessor();

  @Test
  void onStart() {
    assertThat(spanProcessor.isStartRequired()).isTrue();
  }

  @Test
  void setThreadAttributes() {
    Thread thread = Thread.currentThread();
    spanProcessor.onStart(Context.root(), span);

    verify(span).getAttribute(THREAD_ID);
    verify(span).setAttribute(THREAD_ID, thread.getId());
    verify(span).getAttribute(THREAD_NAME);
    verify(span).setAttribute(THREAD_NAME, thread.getName());
    verifyNoMoreInteractions(span);
  }

  @Test
  void doesNotOverrideExistingThreadAttributes() {
    when(span.getAttribute(THREAD_ID)).thenReturn(123L);
    when(span.getAttribute(THREAD_NAME)).thenReturn("worker-1");

    spanProcessor.onStart(Context.root(), span);

    verify(span).getAttribute(THREAD_ID);
    verify(span).getAttribute(THREAD_NAME);
    verify(span, never()).setAttribute(THREAD_ID, Thread.currentThread().getId());
    verify(span, never()).setAttribute(THREAD_NAME, Thread.currentThread().getName());
    verifyNoMoreInteractions(span);
  }
}
