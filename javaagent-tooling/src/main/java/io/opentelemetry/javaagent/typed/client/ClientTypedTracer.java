/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.typed.client;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.javaagent.typed.base.BaseTypedTracer;

public abstract class ClientTypedTracer<
        T extends ClientTypedSpan<T, REQUEST, RESPONSE>, REQUEST, RESPONSE>
    extends BaseTypedTracer<T, REQUEST> {
  @Override
  protected Span.Kind getSpanKind() {
    return Span.Kind.CLIENT;
  }

  @Override
  protected T startSpan(REQUEST request, T span) {
    return span.onRequest(request);
  }
}
