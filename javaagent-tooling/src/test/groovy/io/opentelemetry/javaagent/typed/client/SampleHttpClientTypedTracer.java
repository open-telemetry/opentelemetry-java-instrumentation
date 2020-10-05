/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.typed.client;

import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.javaagent.typed.client.http.HttpClientTypedTracer;
import io.opentelemetry.trace.Span;

public class SampleHttpClientTypedTracer
    extends HttpClientTypedTracer<SampleHttpClientTypedSpan, String, String> {
  @Override
  protected String getInstrumentationName() {
    return "test";
  }

  @Override
  protected String getVersion() {
    return "test";
  }

  @Override
  protected String getSpanName(String o) {
    return "test-span";
  }

  @Override
  protected TextMapPropagator.Setter<String> getSetter() {
    return new TextMapPropagator.Setter<String>() {
      @Override
      public void set(String carrier, String key, String value) {}
    };
  }

  @Override
  protected SampleHttpClientTypedSpan wrapSpan(Span span) {
    return new SampleHttpClientTypedSpan(span);
  }
}
