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
package io.opentelemetry.auto.instrumentation.ratpack;

import static io.opentelemetry.trace.TracingContextUtils.currentContextWith;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import lombok.extern.slf4j.Slf4j;
import ratpack.func.Action;

@Slf4j
public class ActionWrapper<T> implements Action<T> {
  private static final Tracer TRACER =
      OpenTelemetry.getTracerProvider().get("io.opentelemetry.auto.ratpack-1.4");

  private final Action<T> delegate;
  private final Span span;

  private ActionWrapper(final Action<T> delegate, final Span span) {
    assert span != null;
    this.delegate = delegate;
    this.span = span;
  }

  @Override
  public void execute(final T t) throws Exception {
    try (final Scope scope = currentContextWith(span)) {
      delegate.execute(t);
    }
  }

  public static <T> Action<T> wrapIfNeeded(final Action<T> delegate) {
    final Span span = TRACER.getCurrentSpan();
    if (delegate instanceof ActionWrapper || !span.getContext().isValid()) {
      return delegate;
    }
    log.debug("Wrapping action task {}", delegate);
    return new ActionWrapper(delegate, span);
  }
}
