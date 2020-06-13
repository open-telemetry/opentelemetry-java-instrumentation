/*
 * Copyright The OpenTelemetry Authors
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
package io.opentelemetry.auto.instrumentation.opentelemetryapi.vbeta.trace;

import static io.opentelemetry.auto.instrumentation.opentelemetryapi.vbeta.trace.Bridging.toUnshaded;

import lombok.extern.slf4j.Slf4j;
import unshaded.io.opentelemetry.context.Scope;
import unshaded.io.opentelemetry.trace.Span;
import unshaded.io.opentelemetry.trace.Tracer;

@Slf4j
class UnshadedTracer implements Tracer {

  private final io.opentelemetry.trace.Tracer shadedTracer;

  UnshadedTracer(final io.opentelemetry.trace.Tracer shadedTracer) {
    this.shadedTracer = shadedTracer;
  }

  @Override
  public Span getCurrentSpan() {
    return toUnshaded(shadedTracer.getCurrentSpan());
  }

  @Override
  public Scope withSpan(final Span span) {
    return TracingContextUtils.currentContextWith(span);
  }

  @Override
  public Span.Builder spanBuilder(final String spanName) {
    return new UnshadedSpan.Builder(shadedTracer.spanBuilder(spanName));
  }
}
