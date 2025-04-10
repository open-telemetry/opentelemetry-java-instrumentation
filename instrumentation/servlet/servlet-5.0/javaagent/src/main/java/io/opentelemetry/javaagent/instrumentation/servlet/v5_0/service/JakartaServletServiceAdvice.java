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
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
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
import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;

@SuppressWarnings("unused")
public class JakartaServletServiceAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void onEnter(
      @Advice.This(typing = Assigner.Typing.DYNAMIC) Object servletOrFilter,
      @Advice.Argument(value = 0, readOnly = false) ServletRequest request,
      @Advice.Argument(value = 1, readOnly = false) ServletResponse response,
      @Advice.Local("otelCallDepth") CallDepth callDepth,
      @Advice.Local("otelRequest") ServletRequestContext<HttpServletRequest> requestContext,
      @Advice.Local("otelContext") Context context,
      @Advice.Local("otelScope") Scope scope) {

    if (!(request instanceof HttpServletRequest) || !(response instanceof HttpServletResponse)) {
      return;
    }
    HttpServletRequest httpServletRequest = (HttpServletRequest) request;

    String snippet = getSnippetInjectionHelper().getSnippet();
    if (!snippet.isEmpty()
        && !((HttpServletResponse) response)
            .containsHeader(Servlet5SnippetInjectingResponseWrapper.FAKE_SNIPPET_HEADER)) {
      response =
          new Servlet5SnippetInjectingResponseWrapper((HttpServletResponse) response, snippet);
    }

    callDepth = CallDepth.forClass(AppServerBridge.getCallDepthKey());
    callDepth.getAndIncrement();

    Context currentContext = Java8BytecodeBridge.currentContext();
    Context attachedContext = helper().getServerContext(httpServletRequest);
    Context contextToUpdate;

    requestContext = new ServletRequestContext<>(httpServletRequest, servletOrFilter);
    if (attachedContext == null && helper().shouldStart(currentContext, requestContext)) {
      context = helper().start(currentContext, requestContext);
      helper().setAsyncListenerResponse(context, (HttpServletResponse) response);

      contextToUpdate = context;
    } else if (attachedContext != null
        && helper().needsRescoping(currentContext, attachedContext)) {
      // Given request already has a context associated with it.
      // see the needsRescoping() javadoc for more explanation
      contextToUpdate = attachedContext;
    } else {
      // We are inside nested servlet/filter/app-server span, don't create new span
      contextToUpdate = currentContext;
    }

    // Update context with info from current request to ensure that server span gets the best
    // possible name.
    // In case server span was created by app server instrumentations calling updateContext
    // returns a new context that contains servlet context path that is used in other
    // instrumentations for naming server span.
    MappingResolver mappingResolver = Servlet5Singletons.getMappingResolver(servletOrFilter);
    boolean servlet = servletOrFilter instanceof Servlet;
    contextToUpdate =
        helper().updateContext(contextToUpdate, httpServletRequest, mappingResolver, servlet);
    scope = contextToUpdate.makeCurrent();

    if (context != null) {
      // Only trigger response customizer once, so only if server span was created here
      HttpServerResponseCustomizerHolder.getCustomizer()
          .customize(contextToUpdate, (HttpServletResponse) response, Servlet5Accessor.INSTANCE);
    }
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Argument(0) ServletRequest request,
      @Advice.Argument(1) ServletResponse response,
      @Advice.Thrown Throwable throwable,
      @Advice.Local("otelCallDepth") CallDepth callDepth,
      @Advice.Local("otelRequest") ServletRequestContext<HttpServletRequest> requestContext,
      @Advice.Local("otelContext") Context context,
      @Advice.Local("otelScope") Scope scope) {

    if (!(request instanceof HttpServletRequest) || !(response instanceof HttpServletResponse)) {
      return;
    }

    boolean topLevel = callDepth.decrementAndGet() == 0;

    helper()
        .end(
            requestContext,
            (HttpServletRequest) request,
            (HttpServletResponse) response,
            throwable,
            topLevel,
            context,
            scope);
  }
}
