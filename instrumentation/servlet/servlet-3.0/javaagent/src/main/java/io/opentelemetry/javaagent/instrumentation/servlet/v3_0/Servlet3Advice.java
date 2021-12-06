/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v3_0;

import static io.opentelemetry.javaagent.instrumentation.servlet.v3_0.Servlet3Singletons.helper;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.tracer.ServerSpan;
import io.opentelemetry.javaagent.bootstrap.servlet.AppServerBridge;
import io.opentelemetry.javaagent.bootstrap.servlet.MappingResolver;
import io.opentelemetry.javaagent.instrumentation.api.CallDepth;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import io.opentelemetry.javaagent.instrumentation.servlet.ServletRequestContext;
import javax.servlet.Servlet;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;

@SuppressWarnings("unused")
public class Servlet3Advice {

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

    callDepth = CallDepth.forClass(AppServerBridge.getCallDepthKey());
    callDepth.getAndIncrement();

    HttpServletRequest httpServletRequest = (HttpServletRequest) request;

    Context currentContext = Java8BytecodeBridge.currentContext();
    Context attachedContext = helper().getServerContext(httpServletRequest);
    if (attachedContext != null && helper().needsRescoping(currentContext, attachedContext)) {
      MappingResolver mappingResolver = Servlet3Singletons.getMappingResolver(servletOrFilter);
      boolean servlet = servletOrFilter instanceof Servlet;
      attachedContext =
          helper().updateContext(attachedContext, httpServletRequest, mappingResolver, servlet);
      scope = attachedContext.makeCurrent();
      // We are inside nested servlet/filter/app-server span, don't create new span
      return;
    }

    if (attachedContext != null || ServerSpan.fromContextOrNull(currentContext) != null) {
      // Update context with info from current request to ensure that server span gets the best
      // possible name.
      // In case server span was created by app server instrumentations calling updateContext
      // returns a new context that contains servlet context path that is used in other
      // instrumentations for naming server span.
      MappingResolver mappingResolver = Servlet3Singletons.getMappingResolver(servletOrFilter);
      boolean servlet = servletOrFilter instanceof Servlet;
      Context updatedContext =
          helper().updateContext(currentContext, httpServletRequest, mappingResolver, servlet);
      if (currentContext != updatedContext) {
        // updateContext updated context, need to re-scope
        scope = updatedContext.makeCurrent();
      }
      // We are inside nested servlet/filter/app-server span, don't create new span
      return;
    }

    requestContext = new ServletRequestContext<>(httpServletRequest, servletOrFilter);

    if (!helper().shouldStart(currentContext, requestContext)) {
      return;
    }

    context = helper().start(currentContext, requestContext);
    scope = context.makeCurrent();

    helper().setAsyncListenerResponse(httpServletRequest, (HttpServletResponse) response);
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
