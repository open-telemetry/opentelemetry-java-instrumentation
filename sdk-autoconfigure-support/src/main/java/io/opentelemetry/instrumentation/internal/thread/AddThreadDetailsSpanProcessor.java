/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.internal.thread;

import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class AddThreadDetailsSpanProcessor implements SpanProcessor {

  // attributes are not stable yet
  public static final AttributeKey<Long> THREAD_ID = longKey("thread.id");
  public static final AttributeKey<String> THREAD_NAME = stringKey("thread.name");

  @Override
  public void onStart(Context context, ReadWriteSpan span) {
    Thread currentThread = Thread.currentThread();
    span.setAttribute(THREAD_ID, currentThread.getId());
    span.setAttribute(THREAD_NAME, currentThread.getName());
  }

  @Override
  public boolean isStartRequired() {
    return true;
  }

  @Override
  public void onEnd(ReadableSpan span) {}

  @Override
  public boolean isEndRequired() {
    return false;
  }

  @Override
  public CompletableResultCode shutdown() {
    return CompletableResultCode.ofSuccess();
  }

  @Override
  public CompletableResultCode forceFlush() {
    return CompletableResultCode.ofSuccess();
  }
}
