/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.ratpack;

import static io.opentelemetry.trace.TracingContextUtils.currentContextWith;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ratpack.func.Action;

public class ActionWrapper<T> implements Action<T> {

  private static final Logger log = LoggerFactory.getLogger(ActionWrapper.class);

  private static final Tracer TRACER = OpenTelemetry.getTracer("io.opentelemetry.auto.ratpack-1.4");

  private final Action<T> delegate;
  private final Span span;

  private ActionWrapper(Action<T> delegate, Span span) {
    assert span != null;
    this.delegate = delegate;
    this.span = span;
  }

  @Override
  public void execute(T t) throws Exception {
    try (Scope scope = currentContextWith(span)) {
      delegate.execute(t);
    }
  }

  public static <T> Action<T> wrapIfNeeded(Action<T> delegate) {
    Span span = TRACER.getCurrentSpan();
    if (delegate instanceof ActionWrapper || !span.getContext().isValid()) {
      return delegate;
    }
    log.debug("Wrapping action task {}", delegate);
    return new ActionWrapper(delegate, span);
  }
}
