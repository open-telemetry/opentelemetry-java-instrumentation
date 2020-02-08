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

package io.opentelemetry.helpers.core;

import io.opentelemetry.context.propagation.HttpTextFormat;
import io.opentelemetry.distributedcontext.DistributedContext;
import io.opentelemetry.distributedcontext.DistributedContextManager;
import io.opentelemetry.metrics.Meter;
import io.opentelemetry.trace.Span.Kind;
import io.opentelemetry.trace.SpanContext;
import io.opentelemetry.trace.Tracer;

/**
 * Abstract base span decorator implementation for client-side spans.
 *
 * @param <C> the context propagation carrier type
 * @param <Q> the request or input object type
 * @param <P> the response or output object type
 */
public abstract class ClientSpanDecorator<C, Q, P> extends BaseSpanDecorator<C, Q, P> {

  private final HttpTextFormat.Setter<C> propagationSetter;

  /**
   * Constructs a span decorator object.
   *
   * @param tracer the tracer to use in recording spans
   * @param contextManager the context manager to use in handling correlation contexts
   * @param meter the meter to use in recoding measurements
   * @param propagationSetter the decorator-specific context propagation setter
   */
  protected ClientSpanDecorator(
      Tracer tracer,
      DistributedContextManager contextManager,
      Meter meter,
      HttpTextFormat.Setter<C> propagationSetter) {
    super(tracer, contextManager, meter);
    this.propagationSetter = propagationSetter;
  }

  protected abstract String service();

  @Override
  protected Kind spanKind() {
    return Kind.CLIENT;
  }

  @Override
  protected boolean isParentRemote() {
    return false;
  }

  @Override
  protected boolean isNeedingPropagation() {
    return true;
  }

  @Override
  protected SpanContext extractRemoteParentSpan(C carrier) {
    return null; // NoOp
  }

  @Override
  protected DistributedContext extractRemoteCorrelationContext(C carrier) {
    return null; // NoOp
  }

  @Override
  protected void propagateContexts(C carrier, SpanContext span, DistributedContext corlat) {
    if (isBinaryPropagation()) {
      getTracer().getBinaryFormat().toByteArray(span);
      getContextManager().getBinaryFormat().toByteArray(corlat);
    } else {
      getTracer().getHttpTextFormat().inject(span, carrier, propagationSetter);
      getContextManager().getHttpTextFormat().inject(corlat, carrier, propagationSetter);
    }
  }
}
