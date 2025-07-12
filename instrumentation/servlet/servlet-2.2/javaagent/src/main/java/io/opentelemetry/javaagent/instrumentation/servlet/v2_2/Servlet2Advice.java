/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v2_2;

import static io.opentelemetry.javaagent.instrumentation.servlet.v2_2.Servlet2Singletons.RESPONSE_STATUS;
import static io.opentelemetry.javaagent.instrumentation.servlet.v2_2.Servlet2Singletons.helper;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.bootstrap.http.HttpServerResponseCustomizerHolder;
import io.opentelemetry.javaagent.bootstrap.servlet.AppServerBridge;
import io.opentelemetry.javaagent.instrumentation.servlet.ServletRequestContext;
import javax.annotation.Nullable;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;

@SuppressWarnings("unused")
public class Servlet2Advice {

  public static class AdviceScope {

    private final CallDepth callDepth;
    private final ServletRequestContext<HttpServletRequest> requestContext;
    private final Context context;
    private final Scope scope;

    public AdviceScope(
        CallDepth callDepth, HttpServletRequest request, HttpServletResponse response) {
      this.callDepth = callDepth;
      callDepth.getAndIncrement();

      Context serverContext = helper().getServerContext(request);
      if (serverContext != null) {
        Context updatedContext = helper().updateContext(serverContext, request);
        if (updatedContext != serverContext) {
          // updateContext updated context, need to re-scope
          scope = updatedContext.makeCurrent();
        } else {
          scope = null;
        }
        requestContext = null;
        context = null;
        return;
      }

      Context parentContext = Context.current();
      requestContext = new ServletRequestContext<>(request);

      if (!helper().shouldStart(parentContext, requestContext)) {
        context = null;
        scope = null;
        return;
      }

      context = helper().start(parentContext, requestContext);
      scope = context.makeCurrent();
      // reset response status from previous request
      // (some servlet containers reuse response objects to reduce memory allocations)
      RESPONSE_STATUS.set(response, null);

      HttpServerResponseCustomizerHolder.getCustomizer()
          .customize(context, response, Servlet2Accessor.INSTANCE);
    }

    public void exit(
        @Nullable Throwable throwable, HttpServletRequest request, HttpServletResponse response) {

      if (scope != null) {
        scope.close();
      }

      boolean topLevel = callDepth.decrementAndGet() == 0;
      if (context == null && topLevel) {
        Context currentContext = Context.current();
        // Something else is managing the context, we're in the outermost level of Servlet
        // instrumentation and we have an uncaught throwable. Let's add it to the current span.
        if (throwable != null) {
          helper().recordException(currentContext, throwable);
        }
        // also capture request parameters as servlet attributes
        helper().captureServletAttributes(currentContext, request);
      }

      if (scope == null || context == null) {
        return;
      }

      int responseStatusCode = HttpServletResponse.SC_OK;
      Integer responseStatus = RESPONSE_STATUS.get(response);
      if (responseStatus != null) {
        responseStatusCode = responseStatus;
      }

      helper().end(context, requestContext, response, responseStatusCode, throwable);
    }
  }

  @Nullable
  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static AdviceScope onEnter(
      @Advice.Argument(0) ServletRequest request,
      @Advice.Argument(value = 1, typing = Assigner.Typing.DYNAMIC) ServletResponse response) {
    if (!(request instanceof HttpServletRequest) || !(response instanceof HttpServletResponse)) {
      return null;
    }
    return new AdviceScope(
        CallDepth.forClass(AppServerBridge.getCallDepthKey()),
        (HttpServletRequest) request,
        (HttpServletResponse) response);
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Argument(0) ServletRequest request,
      @Advice.Argument(1) ServletResponse response,
      @Advice.Thrown @Nullable Throwable throwable,
      @Advice.Enter @Nullable AdviceScope adviceScope) {
    if (adviceScope == null
        || !(request instanceof HttpServletRequest)
        || !(response instanceof HttpServletResponse)) {
      return;
    }
    adviceScope.exit(throwable, (HttpServletRequest) request, (HttpServletResponse) response);
  }
}
