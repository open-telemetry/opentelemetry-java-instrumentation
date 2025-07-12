/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v5_0.service;

import static io.opentelemetry.javaagent.instrumentation.servlet.v5_0.Servlet5Singletons.getSnippetInjectionHelper;
import static io.opentelemetry.javaagent.instrumentation.servlet.v5_0.Servlet5Singletons.helper;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.bootstrap.http.HttpServerResponseCustomizerHolder;
import io.opentelemetry.javaagent.bootstrap.servlet.AppServerBridge;
import io.opentelemetry.javaagent.bootstrap.servlet.MappingResolver;
import io.opentelemetry.javaagent.instrumentation.servlet.ServletRequestContext;
import io.opentelemetry.javaagent.instrumentation.servlet.v5_0.Servlet5Accessor;
import io.opentelemetry.javaagent.instrumentation.servlet.v5_0.Servlet5Singletons;
import io.opentelemetry.javaagent.instrumentation.servlet.v5_0.snippet.Servlet5SnippetInjectingResponseWrapper;
import jakarta.servlet.Servlet;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.implementation.bytecode.assign.Assigner;

@SuppressWarnings("unused")
public class JakartaServletServiceAdvice {

  public static class AdviceScope {
    private final CallDepth callDepth;
    private final ServletRequestContext<HttpServletRequest> requestContext;
    private final Context context;
    private final Scope scope;

    public AdviceScope(
        CallDepth callDepth,
        Object servletOrFilter,
        HttpServletRequest request,
        ServletResponse response) {
      this.callDepth = callDepth;
      this.callDepth.getAndIncrement();

      Context currentContext = Context.current();
      Context attachedContext = helper().getServerContext(request);
      Context contextToUpdate;

      requestContext = new ServletRequestContext<>(request, servletOrFilter);
      if (attachedContext == null && helper().shouldStart(currentContext, requestContext)) {
        context = helper().start(currentContext, requestContext);
        helper().setAsyncListenerResponse(context, (HttpServletResponse) response);

        contextToUpdate = context;
      } else if (attachedContext != null
          && helper().needsRescoping(currentContext, attachedContext)) {
        // Given request already has a context associated with it.
        // see the needsRescoping() javadoc for more explanation
        contextToUpdate = attachedContext;
        context = null;
      } else {
        // We are inside nested servlet/filter/app-server span, don't create new span
        contextToUpdate = currentContext;
        context = null;
      }

      // Update context with info from current request to ensure that server span gets the best
      // possible name.
      // In case server span was created by app server instrumentations calling updateContext
      // returns a new context that contains servlet context path that is used in other
      // instrumentations for naming server span.
      MappingResolver mappingResolver = Servlet5Singletons.getMappingResolver(servletOrFilter);
      boolean servlet = servletOrFilter instanceof Servlet;
      contextToUpdate = helper().updateContext(contextToUpdate, request, mappingResolver, servlet);
      scope = contextToUpdate.makeCurrent();

      if (context != null) {
        // Only trigger response customizer once, so only if server span was created here
        HttpServerResponseCustomizerHolder.getCustomizer()
            .customize(contextToUpdate, (HttpServletResponse) response, Servlet5Accessor.INSTANCE);
      }
    }

    public void exit(
        HttpServletRequest request, HttpServletResponse response, @Nullable Throwable throwable) {
      boolean topLevel = callDepth.decrementAndGet() == 0;
      helper().end(requestContext, request, response, throwable, topLevel, context, scope);
    }
  }

  @AssignReturned.ToArguments({
    @ToArgument(value = 0, index = 1),
    @ToArgument(value = 1, index = 2)
  })
  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static Object[] onEnter(
      @Advice.This(typing = Assigner.Typing.DYNAMIC) Object servletOrFilter,
      @Advice.Argument(0) ServletRequest request,
      @Advice.Argument(1) ServletResponse originalResponse) {

    ServletResponse response = originalResponse;

    if (!(request instanceof HttpServletRequest) || !(response instanceof HttpServletResponse)) {
      return new Object[] {null, request, response};
    }
    HttpServletRequest httpServletRequest = (HttpServletRequest) request;

    String snippet = getSnippetInjectionHelper().getSnippet();
    if (!snippet.isEmpty()
        && !((HttpServletResponse) response)
            .containsHeader(Servlet5SnippetInjectingResponseWrapper.FAKE_SNIPPET_HEADER)) {
      response =
          new Servlet5SnippetInjectingResponseWrapper((HttpServletResponse) response, snippet);
    }

    AdviceScope adviceScope =
        new AdviceScope(
            CallDepth.forClass(AppServerBridge.getCallDepthKey()),
            servletOrFilter,
            (HttpServletRequest) request,
            response);

    return new Object[] {adviceScope, request, response};
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Argument(0) ServletRequest request,
      @Advice.Argument(1) ServletResponse response,
      @Advice.Thrown Throwable throwable,
      @Advice.Enter Object[] enterResult) {

    AdviceScope adviceScope = (AdviceScope) enterResult[0];

    if (adviceScope == null
        || !(request instanceof HttpServletRequest)
        || !(response instanceof HttpServletResponse)) {
      return;
    }

    adviceScope.exit((HttpServletRequest) request, (HttpServletResponse) response, throwable);
  }
}
