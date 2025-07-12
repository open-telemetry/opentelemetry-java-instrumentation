/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v3_0;

import static io.opentelemetry.javaagent.instrumentation.servlet.v3_0.Servlet3Singletons.getSnippetInjectionHelper;
import static io.opentelemetry.javaagent.instrumentation.servlet.v3_0.Servlet3Singletons.helper;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.bootstrap.http.HttpServerResponseCustomizerHolder;
import io.opentelemetry.javaagent.bootstrap.servlet.AppServerBridge;
import io.opentelemetry.javaagent.bootstrap.servlet.MappingResolver;
import io.opentelemetry.javaagent.instrumentation.servlet.ServletRequestContext;
import io.opentelemetry.javaagent.instrumentation.servlet.v3_0.snippet.Servlet3SnippetInjectingResponseWrapper;
import javax.annotation.Nullable;
import javax.servlet.Servlet;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.implementation.bytecode.assign.Assigner;

@SuppressWarnings("unused")
public class Servlet3Advice {

  public static class AdviceScope {
    private final CallDepth callDepth;
    private final ServletRequestContext<HttpServletRequest> requestContext;
    private final Context context;
    private final Scope scope;

    public AdviceScope(
        CallDepth callDepth,
        HttpServletRequest request,
        HttpServletResponse response,
        Object servletOrFilter) {
      this.callDepth = callDepth;
      this.callDepth.getAndIncrement();

      Context currentContext = Context.current();
      Context attachedContext = helper().getServerContext(request);
      Context contextToUpdate;

      requestContext = new ServletRequestContext<>(request, servletOrFilter);
      if (attachedContext == null && helper().shouldStart(currentContext, requestContext)) {
        context = helper().start(currentContext, requestContext);
        helper().setAsyncListenerResponse(context, response);

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
      MappingResolver mappingResolver = Servlet3Singletons.getMappingResolver(servletOrFilter);
      boolean servlet = servletOrFilter instanceof Servlet;
      contextToUpdate = helper().updateContext(contextToUpdate, request, mappingResolver, servlet);
      scope = contextToUpdate.makeCurrent();

      if (context != null) {
        // Only trigger response customizer once, so only if server span was created here
        HttpServerResponseCustomizerHolder.getCustomizer()
            .customize(contextToUpdate, response, Servlet3Accessor.INSTANCE);
      }
    }

    public void exit(
        @Nullable Throwable throwable, HttpServletRequest request, HttpServletResponse response) {

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

    String snippet = getSnippetInjectionHelper().getSnippet();
    if (!snippet.isEmpty()
        && !((HttpServletResponse) response)
            .containsHeader(Servlet3SnippetInjectingResponseWrapper.FAKE_SNIPPET_HEADER)) {
      response =
          new Servlet3SnippetInjectingResponseWrapper((HttpServletResponse) response, snippet);
    }
    AdviceScope adviceScope =
        new AdviceScope(
            CallDepth.forClass(AppServerBridge.getCallDepthKey()),
            (HttpServletRequest) request,
            (HttpServletResponse) response,
            servletOrFilter);
    return new Object[] {adviceScope, request, response};
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Argument(0) ServletRequest request,
      @Advice.Argument(1) ServletResponse response,
      @Advice.Thrown @Nullable Throwable throwable,
      @Advice.Enter Object[] enterResult) {
    AdviceScope adviceScope = (AdviceScope) enterResult[0];
    if (adviceScope == null
        || !(request instanceof HttpServletRequest)
        || !(response instanceof HttpServletResponse)) {
      return;
    }
    adviceScope.exit(throwable, (HttpServletRequest) request, (HttpServletResponse) response);
  }
}
