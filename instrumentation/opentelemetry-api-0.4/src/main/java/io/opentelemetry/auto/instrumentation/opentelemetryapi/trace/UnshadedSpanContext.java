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
package io.opentelemetry.auto.instrumentation.opentelemetryapi.trace;

import static io.opentelemetry.auto.instrumentation.opentelemetryapi.trace.Bridging.toUnshaded;

import unshaded.io.opentelemetry.trace.SpanContext;
import unshaded.io.opentelemetry.trace.SpanId;
import unshaded.io.opentelemetry.trace.TraceFlags;
import unshaded.io.opentelemetry.trace.TraceId;
import unshaded.io.opentelemetry.trace.TraceState;

public class UnshadedSpanContext extends SpanContext {

  private final io.opentelemetry.trace.SpanContext shadedSpanContext;

  public UnshadedSpanContext(final io.opentelemetry.trace.SpanContext shadedSpanContext) {
    this.shadedSpanContext = shadedSpanContext;
  }

  io.opentelemetry.trace.SpanContext getShadedSpanContext() {
    return shadedSpanContext;
  }

  @Override
  public TraceId getTraceId() {
    return toUnshaded(shadedSpanContext.getTraceId());
  }

  @Override
  public SpanId getSpanId() {
    return toUnshaded(shadedSpanContext.getSpanId());
  }

  @Override
  public TraceFlags getTraceFlags() {
    return toUnshaded(shadedSpanContext.getTraceFlags());
  }

  @Override
  public TraceState getTraceState() {
    return toUnshaded(shadedSpanContext.getTraceState());
  }

  @Override
  public boolean isRemote() {
    return shadedSpanContext.isRemote();
  }
}
