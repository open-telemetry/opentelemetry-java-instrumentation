/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.typed.client;

import io.opentelemetry.javaagent.typed.base.BaseTypedSpan;
import io.opentelemetry.trace.Span;

public abstract class ClientTypedSpan<T extends ClientTypedSpan, REQUEST, RESPONSE>
    extends BaseTypedSpan<T> {

  public ClientTypedSpan(Span delegate) {
    super(delegate);
  }

  protected abstract T onRequest(REQUEST request);

  protected abstract T onResponse(RESPONSE response);

  public void end(RESPONSE response) {
    onResponse(response).end();
  }
}
