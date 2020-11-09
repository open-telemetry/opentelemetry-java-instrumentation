/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.typed.server;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.javaagent.typed.server.http.HttpServerTypedSpan;

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
