/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.v8_0;

import static io.opentelemetry.javaagent.instrumentation.jetty.v8_0.Jetty8Singletons.helper;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.bootstrap.http.HttpServerResponseCustomizerHolder;
import io.opentelemetry.javaagent.instrumentation.servlet.ServletRequestContext;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.bytebuddy.asm.Advice;

@SuppressWarnings("unused")
public class Jetty8HandlerAdvice {

  public static class AdviceScope {
    private final ServletRequestContext<HttpServletRequest> requestContext;
    private final Context context;
    private final Scope scope;

    private AdviceScope(
        ServletRequestContext<HttpServletRequest> requestContext, Context context, Scope scope) {
      this.requestContext = requestContext;
      this.context = context;
      this.scope = scope;
    }

    @Nullable
    public static AdviceScope start(
        Object source, HttpServletRequest request, HttpServletResponse response) {
      Context attachedContext = helper().getServerContext(request);
      if (attachedContext != null) {
        // We are inside nested handler, don't create new span
        return null;
      }
      Context parentContext = Context.current();
      ServletRequestContext<HttpServletRequest> requestContext =
          new ServletRequestContext<>(request);
      if (!helper().shouldStart(parentContext, requestContext)) {
        return null;
      }
      Context context = helper().start(parentContext, requestContext);
      Scope scope = context.makeCurrent();
      // Must be set here since Jetty handlers can use startAsync outside of servlet scope.
      helper().setAsyncListenerResponse(context, response);
      HttpServerResponseCustomizerHolder.getCustomizer()
          .customize(context, response, Jetty8ResponseMutator.INSTANCE);
      return new AdviceScope(requestContext, context, scope);
    }

    public void end(
        @Nullable Throwable throwable, HttpServletRequest request, HttpServletResponse response) {
      helper().end(requestContext, request, response, throwable, context, scope);
    }
  }

  @Advice.OnMethodEnter(suppress = Throwable.class)
  @Nullable
  public static AdviceScope onEnter(
      @Advice.This Object source,
      @Advice.Argument(2) HttpServletRequest request,
      @Advice.Argument(3) HttpServletResponse response) {
    return AdviceScope.start(source, request, response);
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Argument(2) HttpServletRequest request,
      @Advice.Argument(3) HttpServletResponse response,
      @Advice.Thrown @Nullable Throwable throwable,
      @Advice.Enter @Nullable AdviceScope adviceScope) {
    if (adviceScope != null) {
      adviceScope.end(throwable, request, response);
    }
  }
}
