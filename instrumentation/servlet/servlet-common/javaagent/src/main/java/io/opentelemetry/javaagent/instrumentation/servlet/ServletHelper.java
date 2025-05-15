/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.bootstrap.servlet.ServletAsyncContext;

public class ServletHelper<REQUEST, RESPONSE> extends BaseServletHelper<REQUEST, RESPONSE> {
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
        if (!mustEndOnHandlerMethodExit(currentContext)) {
          // We could be inside async dispatch. Unlike tomcat jetty does not call
          // ServletAsyncListener.onError when exception is thrown inside async dispatch.
          recordAsyncException(currentContext, throwable);
        }
      }
      // also capture request parameters as servlet attributes
      captureServletAttributes(currentContext, request);
    }

    if (scope == null || context == null) {
      return;
    }

    ServletResponseContext<RESPONSE> responseContext = new ServletResponseContext<>(response);
    if (throwable != null || mustEndOnHandlerMethodExit(context)) {
      instrumenter.end(context, requestContext, responseContext, throwable);
    }
  }

  /**
   * Helper method to determine whether the appserver handler/servlet service/servlet filter method
   * that started a span must also end it, even if no error was detected. Extracted as a separate
   * method to avoid duplicating the comments on the logic behind this choice.
   */
  public boolean mustEndOnHandlerMethodExit(Context context) {
    if (isAsyncListenerAttached(context)) {
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
   * Response object must be attached to a request prior to {@link #attachAsyncListener(REQUEST,
   * Context)} being called, as otherwise in some environments it is not possible to access response
   * from async event in listeners.
   */
  public void setAsyncListenerResponse(Context context, RESPONSE response) {
    ServletAsyncContext.setAsyncListenerResponse(context, response);
  }

  @SuppressWarnings("unchecked")
  public RESPONSE getAsyncListenerResponse(Context context) {
    return (RESPONSE) ServletAsyncContext.getAsyncListenerResponse(context);
  }

  public void attachAsyncListener(REQUEST request, Context context) {
    if (isAsyncListenerAttached(context)) {
      return;
    }

    Object response = getAsyncListenerResponse(context);

    ServletRequestContext<REQUEST> requestContext = new ServletRequestContext<>(request, null);
    accessor.addRequestAsyncListener(
        request,
        new AsyncRequestCompletionListener<>(this, instrumenter, requestContext, context),
        response);
    ServletAsyncContext.setAsyncListenerAttached(context, true);
  }

  private static boolean isAsyncListenerAttached(Context context) {
    return ServletAsyncContext.isAsyncListenerAttached(context);
  }

  public Runnable wrapAsyncRunnable(Runnable runnable) {
    return AsyncRunnableWrapper.wrap(this, runnable);
  }

  public void recordAsyncException(Context context, Throwable throwable) {
    ServletAsyncContext.recordAsyncException(context, throwable);
  }

  public Throwable getAsyncException(Context context) {
    return ServletAsyncContext.getAsyncException(context);
  }

  public Context getAsyncListenerContext(Context context) {
    return ServletAsyncContext.getAsyncListenerContext(context);
  }
}
