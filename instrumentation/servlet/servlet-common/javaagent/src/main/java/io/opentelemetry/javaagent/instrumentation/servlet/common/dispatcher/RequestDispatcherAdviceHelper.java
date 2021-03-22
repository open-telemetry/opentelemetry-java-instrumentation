/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.common.dispatcher;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.tracer.HttpServerTracer;

public class RequestDispatcherAdviceHelper {
  /**
   * Determines if the advice for {@link RequestDispatcherInstrumentation} should create a new span
   * and provides the context in which that span should be created.
   *
   * @param servletContextObject Value of the {@link HttpServerTracer#CONTEXT_ATTRIBUTE} attribute
   *     of the servlet request.
   * @return The context in which the advice should create the dispatcher span in. Returns <code>
   *     null</code> in case a new span should not be created.
   */
  public static Context getStartParentContext(Object servletContextObject) {
    Context parentContext = Context.current();

    Context servletContext =
        servletContextObject instanceof Context ? (Context) servletContextObject : null;

    Span parentSpan = Span.fromContext(parentContext);
    SpanContext parentSpanContext = parentSpan.getSpanContext();
    if (!parentSpanContext.isValid() && servletContext == null) {
      // Don't want to generate a new top-level span
      return null;
    }

    Span servletSpan = servletContext != null ? Span.fromContext(servletContext) : null;
    Context parent;
    if (servletContext == null
        || (parentSpanContext.isValid()
            && servletSpan.getSpanContext().getTraceId().equals(parentSpanContext.getTraceId()))) {
      // Use the parentSpan if the servletSpan is null or part of the same trace.
      parent = parentContext;
    } else {
      // parentSpan is part of a different trace, so lets ignore it.
      // This can happen with the way Tomcat does error handling.
      parent = servletContext;
    }

    return parent;
  }
}
