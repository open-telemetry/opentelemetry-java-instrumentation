/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.common.dispatcher;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.tracer.HttpServerTracer;
import org.checkerframework.checker.nullness.qual.Nullable;

public class RequestDispatcherAdviceHelper {
  /**
   * Determines if the advice for {@link RequestDispatcherInstrumentation} should create a new span
   * and provides the context in which that span should be created.
   *
   * @param requestContext Value of the {@link HttpServerTracer#CONTEXT_ATTRIBUTE} attribute of the
   *     servlet request.
   * @return The context in which the advice should create the dispatcher span in. Returns <code>
   *     null</code> in case a new span should not be created.
   */
  // TODO (trask) do we need to guard against context leak here?
  //  this could be simplified by always using currentContext, only falling back to requestContext
  //  if currentContext does not have a valid span
  public static @Nullable Context getStartParentContext(
      Context currentContext, @Nullable Context requestContext) {
    Span currentSpan = Span.fromContext(currentContext);
    SpanContext currentSpanContext = currentSpan.getSpanContext();

    if (!currentSpanContext.isValid()) {
      return requestContext;
    }

    if (requestContext == null) {
      return currentContext;
    }

    // at this point: currentContext has a valid span and requestContext is not null

    Span requestSpan = Span.fromContext(requestContext);
    if (requestSpan.getSpanContext().getTraceId().equals(currentSpanContext.getTraceId())) {
      // currentContext is part of the same trace, so return it
      return currentContext;
    }

    // currentContext is part of a different trace, so lets ignore it.
    // This can happen with the way Tomcat does error handling.
    return requestContext;
  }
}
