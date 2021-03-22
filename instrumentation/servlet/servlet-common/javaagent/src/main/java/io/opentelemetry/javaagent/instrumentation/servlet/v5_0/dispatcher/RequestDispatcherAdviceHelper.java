/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v5_0.dispatcher;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;

public class RequestDispatcherAdviceHelper {
  public static Context getStartParentContext(Object servletContextObject) {
    Context parentContext = Context.current();

    Context servletContext =
        servletContextObject instanceof Context ? (Context) servletContextObject : null;

    Span parentSpan = Java8BytecodeBridge.spanFromContext(parentContext);
    SpanContext parentSpanContext = parentSpan.getSpanContext();
    if (!parentSpanContext.isValid() && servletContext == null) {
      // Don't want to generate a new top-level span
      return null;
    }

    Span servletSpan =
        servletContext != null ? Java8BytecodeBridge.spanFromContext(servletContext) : null;
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
