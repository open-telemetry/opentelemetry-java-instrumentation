/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.typed.server;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.javaagent.typed.server.http.HttpServerTypedTracer;

public class SampleHttpServerTypedTracer
    extends HttpServerTypedTracer<SampleHttpServerTypedSpan, String, String> {
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
  protected SampleHttpServerTypedSpan wrapSpan(Span span) {
    return new SampleHttpServerTypedSpan(span);
  }

  @Override
  protected TextMapPropagator.Getter<String> getGetter() {
    return new TextMapPropagator.Getter<String>() {
      @Override
      public String get(String carrier, String key) {
        return null;
      }
    };
  }
}
