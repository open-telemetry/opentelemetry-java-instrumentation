/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet;

import static io.opentelemetry.instrumentation.api.servlet.ServerSpanNaming.Source.FILTER;
import static io.opentelemetry.instrumentation.api.servlet.ServerSpanNaming.Source.SERVLET;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.servlet.ServerSpanNaming;
import io.opentelemetry.instrumentation.servlet.ServletAccessor;
import io.opentelemetry.instrumentation.servlet.ServletHttpServerTracer;

public abstract class ServletHelper<REQUEST, RESPONSE>
    extends BaseServletHelper<REQUEST, RESPONSE> {

  protected ServletHelper(
      Instrumenter<ServletRequestContext<REQUEST>, ServletResponseContext<RESPONSE>> instrumenter,
      ServletAccessor<REQUEST, RESPONSE> accessor) {
    super(instrumenter, accessor);
  }

  public Context startServletSpan(
      Context parentContext, ServletRequestContext<REQUEST> requestContext, boolean servlet) {
    ServerSpanNaming.Source namingSource = servlet ? SERVLET : FILTER;
    return startSpan(parentContext, requestContext, namingSource);
  }

  public void stopSpan(
      ServletRequestContext<REQUEST> requestContext,
      REQUEST request,
      RESPONSE response,
      Throwable throwable,
      boolean topLevel,
      Context context,
      Scope scope) {

    if (scope != null) {
      scope.close();
    }

    if (context == null && topLevel) {
      Context currentContext = Context.current();
      // Something else is managing the context, we're in the outermost level of Servlet
      // instrumentation and we have an uncaught throwable. Let's add it to the current span.
      if (throwable != null) {
        recordException(currentContext, throwable);
      }
    }

    if (scope == null || context == null) {
      return;
    }

    ServletResponseContext<RESPONSE> responseContext =
        new ServletResponseContext<>(response, throwable);
    if (throwable != null) {
      instrumenter.end(context, requestContext, responseContext, throwable);
      return;
    }

    if (mustEndOnHandlerMethodExit(request)) {
      instrumenter.end(context, requestContext, responseContext, null);
    }
  }

  /**
   * Helper method to determine whether the appserver handler/servlet service/servlet filter method
   * that started a span must also end it, even if no error was detected. Extracted as a separate
   * method to avoid duplicating the comments on the logic behind this choice.
   */
  public boolean mustEndOnHandlerMethodExit(REQUEST request) {
    if (isAsyncListenerAttached(request)) {
      // This request is handled asynchronously and startAsync instrumentation has already attached
      // the listener.
      return false;
    }

    // This means that startAsync was not called (assuming startAsync instrumentation works
    // correctly on this servlet engine), therefore the request was handled synchronously, and
    // handler method end must also end the span.
    return true;
  }

  /**
   * Response object must be attached to a request prior to {@link
   * #attachAsyncListener(ServletRequestContext)} being called, as otherwise in some environments it
   * is not possible to access response from async event in listeners.
   */
  public void setAsyncListenerResponse(REQUEST request, RESPONSE response) {
    accessor.setRequestAttribute(
        request, ServletHttpServerTracer.ASYNC_LISTENER_RESPONSE_ATTRIBUTE, response);
  }

  public RESPONSE getAsyncListenerResponse(REQUEST request) {
    return (RESPONSE)
        accessor.getRequestAttribute(
            request, ServletHttpServerTracer.ASYNC_LISTENER_RESPONSE_ATTRIBUTE);
  }

  public void attachAsyncListener(REQUEST request) {
    ServletRequestContext<REQUEST> requestContext = new ServletRequestContext<>(request, null);
    attachAsyncListener(requestContext);
  }

  public void attachAsyncListener(ServletRequestContext<REQUEST> requestContext) {
    REQUEST request = requestContext.request();
    Context context = getServerContext(request);

    if (context != null) {
      Object response = getAsyncListenerResponse(request);

      accessor.addRequestAsyncListener(
          request,
          new AsyncRequestCompletionListener<>(this, instrumenter, requestContext, context),
          response);
      accessor.setRequestAttribute(request, ServletHttpServerTracer.ASYNC_LISTENER_ATTRIBUTE, true);
    }
  }

  public boolean isAsyncListenerAttached(REQUEST request) {
    return accessor.getRequestAttribute(request, ServletHttpServerTracer.ASYNC_LISTENER_ATTRIBUTE)
        != null;
  }
}
