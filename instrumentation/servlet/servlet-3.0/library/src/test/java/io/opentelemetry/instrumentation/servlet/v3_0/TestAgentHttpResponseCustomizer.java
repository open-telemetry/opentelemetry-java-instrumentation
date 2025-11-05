/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.v3_0;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.bootstrap.http.HttpServerResponseCustomizer;
import io.opentelemetry.javaagent.bootstrap.http.HttpServerResponseMutator;

public class TestAgentHttpResponseCustomizer implements HttpServerResponseCustomizer {

  @Override
  public <T> void customize(
      Context serverContext, T response, HttpServerResponseMutator<T> responseMutator) {

    SpanContext spanContext = Span.fromContext(serverContext).getSpanContext();
    String traceId = spanContext.getTraceId();
    String spanId = spanContext.getSpanId();

    responseMutator.appendHeader(response, "X-Test-TraceId", traceId);
    responseMutator.appendHeader(response, "X-Test-SpanId", spanId);
  }
}
