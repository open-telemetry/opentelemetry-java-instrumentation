/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.testing.http;

import com.google.auto.service.AutoService;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.bootstrap.http.HttpServerResponseCustomizer;
import io.opentelemetry.javaagent.bootstrap.http.HttpServerResponseMutator;

@AutoService(HttpServerResponseCustomizer.class)
public class TestAgentHttpResponseCustomizer implements HttpServerResponseCustomizer {

  @Override
  public <T> void onStart(
      Context serverContext, T response, HttpServerResponseMutator<T> responseMutator) {

    String traceId = Span.fromContext(serverContext).getSpanContext().getTraceId();
    String spanId = Span.fromContext(serverContext).getSpanContext().getSpanId();

    responseMutator.appendHeader(response, "X-Test-TraceId", traceId);
    responseMutator.appendHeader(response, "X-Test-SpanId", spanId);
  }
}
