/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.dispatcher;

import static io.opentelemetry.instrumentation.api.tracer.HttpServerTracer.CONTEXT_ATTRIBUTE;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import io.opentelemetry.instrumentation.servlet.ServletAccessor;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;

public class RequestDispatcherAdviceHelper {
  public static <REQUEST> Context getStartParentContext(
      ServletAccessor<REQUEST, ?> accessor, REQUEST request) {
    Context parentContext = Java8BytecodeBridge.currentContext();

    Object servletContextObject = accessor.getRequestAttribute(request, CONTEXT_ATTRIBUTE);
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

  public static <REQUEST> void stop(
      BaseTracer tracer,
      ServletAccessor<REQUEST, ?> accessor,
      Object originalContext,
      REQUEST request,
      Context context,
      Scope scope,
      Throwable throwable) {
    scope.close();

    // restore the original servlet span
    // since spanWithScope is non-null here, originalContext must have been set with the
    // prior
    // servlet span (as opposed to remaining unset)
    // TODO review this logic. Seems like manual context management
    accessor.setRequestAttribute(request, CONTEXT_ATTRIBUTE, originalContext);

    if (throwable != null) {
      tracer.endExceptionally(context, throwable);
    } else {
      tracer.end(context);
    }
  }
}
