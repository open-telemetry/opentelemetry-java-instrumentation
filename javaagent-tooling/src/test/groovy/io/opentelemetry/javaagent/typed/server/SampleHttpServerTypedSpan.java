/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.typed.server;

import io.opentelemetry.javaagent.typed.server.http.HttpServerTypedSpan;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.StatusCode;

public class SampleHttpServerTypedSpan
    extends HttpServerTypedSpan<SampleHttpServerTypedSpan, String, String> {
  public SampleHttpServerTypedSpan(Span delegate) {
    super(delegate);
  }

  @Override
  protected SampleHttpServerTypedSpan onRequest(String o) {
    delegate.setAttribute("requested", true);
    return this;
  }

  @Override
  protected SampleHttpServerTypedSpan onResponse(String o) {
    delegate.setStatus(StatusCode.OK);
    return this;
  }

  @Override
  protected SampleHttpServerTypedSpan self() {
    return this;
  }
}
