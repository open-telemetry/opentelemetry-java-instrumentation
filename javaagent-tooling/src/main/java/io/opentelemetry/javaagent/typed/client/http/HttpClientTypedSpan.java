/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.typed.client.http;

import io.opentelemetry.javaagent.typed.client.ClientTypedSpan;
import io.opentelemetry.trace.Span;

public abstract class HttpClientTypedSpan<T extends HttpClientTypedSpan, REQUEST, RESPONSE>
    extends ClientTypedSpan<T, REQUEST, RESPONSE> {

  public HttpClientTypedSpan(Span delegate) {
    super(delegate);
  }
}
