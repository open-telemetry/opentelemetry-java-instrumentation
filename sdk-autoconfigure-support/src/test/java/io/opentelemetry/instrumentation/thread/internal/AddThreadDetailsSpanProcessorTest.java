/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.thread.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.semconv.incubating.ThreadIncubatingAttributes;
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

    verify(span).setAttribute(ThreadIncubatingAttributes.THREAD_ID, thread.getId());
    verify(span).setAttribute(ThreadIncubatingAttributes.THREAD_NAME, thread.getName());
    verifyNoMoreInteractions(span);
  }
}
