/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;

public class AddThreadDetailsSpanProcessor implements OnStartSpanProcessor {

  @Override
  public void onStart(Context context, ReadWriteSpan span) {
    Thread currentThread = Thread.currentThread();
    span.setAttribute(SemanticAttributes.THREAD_ID, currentThread.getId());
    span.setAttribute(SemanticAttributes.THREAD_NAME, currentThread.getName());
  }
}
