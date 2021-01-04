/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.testing.exporter;

import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;

public class AgentTestingSpanProcessor implements SpanProcessor {

  volatile boolean forceFlushCalled;

  @Override
  public void onStart(Context parentContext, ReadWriteSpan span) {
    delegate.onStart(parentContext, span);
  }

  @Override
  public boolean isStartRequired() {
    return delegate.isStartRequired();
  }

  @Override
  public void onEnd(ReadableSpan span) {
    delegate.onEnd(span);
  }

  @Override
  public boolean isEndRequired() {
    return delegate.isEndRequired();
  }

  @Override
  public CompletableResultCode shutdown() {
    return delegate.shutdown();
  }

  @Override
  public CompletableResultCode forceFlush() {
    forceFlushCalled = true;
    return delegate.forceFlush();
  }

  private final SpanProcessor delegate;

  public AgentTestingSpanProcessor(SpanProcessor delegate) {
    this.delegate = delegate;
  }
}
