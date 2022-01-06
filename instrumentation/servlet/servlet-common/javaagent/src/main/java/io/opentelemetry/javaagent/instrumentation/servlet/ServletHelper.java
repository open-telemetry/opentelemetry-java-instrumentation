/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;

public class ServletHelper<REQUEST, RESPONSE> extends BaseServletHelper<REQUEST, RESPONSE> {
  private static final String ASYNC_LISTENER_ATTRIBUTE =
      ServletHelper.class.getName() + ".AsyncListener";
  private static final String ASYNC_LISTENER_RESPONSE_ATTRIBUTE =
      ServletHelper.class.getName() + ".AsyncListenerResponse";
  private static final String ASYNC_EXCEPTION_ATTRIBUTE =
      ServletHelper.class.getName() + ".AsyncException";
  public static final String CONTEXT_ATTRIBUTE = ServletHelper.class.getName() + ".Context";

  public ServletHelper(
      Instrumenter<ServletRequestContext<REQUEST>, ServletResponseContext<RESPONSE>> instrumenter,
      ServletAccessor<REQUEST, RESPONSE> accessor) {
    super(instrumenter, accessor);
  }

  public void end(
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
        if (!mustEndOnHandlerMethodExit(request)) {
          // We could be inside async dispatch. Unlike tomcat jetty does not call
          // ServletAsyncListener.onError when exception is thrown inside async dispatch.
          recordAsyncException(request, throwable);
        }
      }
      // also capture request parameters as servlet attributes
      captureServletAttributes(currentContext, request);
    }

    if (scope == null || context == null) {
      return;
    }

    ServletResponseContext<RESPONSE> responseContext =
        new ServletResponseContext<>(response, throwable);
    if (throwable != null || mustEndOnHandlerMethodExit(request)) {
      instrumenter.end(context, requestContext, responseContext, throwable);
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
    accessor.setRequestAttribute(request, ASYNC_LISTENER_RESPONSE_ATTRIBUTE, response);
  }

  public RESPONSE getAsyncListenerResponse(REQUEST request) {
    return (RESPONSE) accessor.getRequestAttribute(request, ASYNC_LISTENER_RESPONSE_ATTRIBUTE);
  }

  public void attachAsyncListener(REQUEST request) {
    ServletRequestContext<REQUEST> requestContext = new ServletRequestContext<>(request, null);
    attachAsyncListener(requestContext);
  }

  private void attachAsyncListener(ServletRequestContext<REQUEST> requestContext) {
    REQUEST request = requestContext.request();
    Context context = getServerContext(request);

    if (context != null) {
      Object response = getAsyncListenerResponse(request);

      accessor.addRequestAsyncListener(
          request,
          new AsyncRequestCompletionListener<>(this, instrumenter, requestContext, context),
          response);
      accessor.setRequestAttribute(request, ASYNC_LISTENER_ATTRIBUTE, true);
    }
  }

  public boolean isAsyncListenerAttached(REQUEST request) {
    return accessor.getRequestAttribute(request, ASYNC_LISTENER_ATTRIBUTE) != null;
  }

  public Runnable wrapAsyncRunnable(REQUEST request, Runnable runnable) {
    return AsyncRunnableWrapper.wrap(this, request, runnable);
  }

  public void recordAsyncException(REQUEST request, Throwable throwable) {
    accessor.setRequestAttribute(request, ASYNC_EXCEPTION_ATTRIBUTE, throwable);
  }

  public Throwable getAsyncException(REQUEST request) {
    return (Throwable) accessor.getRequestAttribute(request, ASYNC_EXCEPTION_ATTRIBUTE);
  }
}
