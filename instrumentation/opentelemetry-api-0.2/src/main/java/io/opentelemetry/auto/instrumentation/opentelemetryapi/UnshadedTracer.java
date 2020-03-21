/*
 * Copyright 2020, OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.opentelemetry.auto.instrumentation.opentelemetryapi;

import lombok.extern.slf4j.Slf4j;
import unshaded.io.opentelemetry.context.NoopScope;
import unshaded.io.opentelemetry.context.Scope;
import unshaded.io.opentelemetry.context.propagation.HttpTextFormat;
import unshaded.io.opentelemetry.trace.Span;
import unshaded.io.opentelemetry.trace.SpanContext;
import unshaded.io.opentelemetry.trace.Tracer;

@Slf4j
public class UnshadedTracer implements Tracer {

  private final io.opentelemetry.trace.Tracer shadedTracer;

  public UnshadedTracer(final io.opentelemetry.trace.Tracer shadedTracer) {
    this.shadedTracer = shadedTracer;
  }

  @Override
  public Span getCurrentSpan() {
    return new UnshadedSpan(shadedTracer.getCurrentSpan());
  }

  @Override
  public Scope withSpan(final Span span) {
    if (span instanceof UnshadedSpan) {
      return new UnshadedScope(shadedTracer.withSpan(((UnshadedSpan) span).getShadedSpan()));
    } else {
      log.debug("unexpected span: {}", span);
      return NoopScope.getInstance();
    }
  }

  @Override
  public Span.Builder spanBuilder(final String spanName) {
    return new UnshadedSpanBuilder(shadedTracer.spanBuilder(spanName));
  }

  @Override
  public HttpTextFormat<SpanContext> getHttpTextFormat() {
    return new UnshadedHttpTextFormat(shadedTracer.getHttpTextFormat());
  }
}
