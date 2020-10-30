/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.typed.server;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.javaagent.typed.base.BaseTypedSpan;

public abstract class ServerTypedSpan<T extends ServerTypedSpan, REQUEST, RESPONSE>
    extends BaseTypedSpan<T> {

  public ServerTypedSpan(Span delegate) {
    super(delegate);
  }

  protected abstract T onRequest(REQUEST request);

  protected abstract T onResponse(RESPONSE response);

  public void end(RESPONSE response) {
    onResponse(response).end();
  }
}
