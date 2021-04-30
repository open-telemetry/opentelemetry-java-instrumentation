/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v5_0.service;

import static io.opentelemetry.instrumentation.servlet.jakarta.v5_0.JakartaServletHttpServerTracer.tracer;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.servlet.AppServerBridge;
import io.opentelemetry.instrumentation.api.servlet.MappingResolver;
import io.opentelemetry.javaagent.instrumentation.api.CallDepthThreadLocalMap;
import io.opentelemetry.javaagent.instrumentation.api.InstrumentationContext;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import io.opentelemetry.javaagent.instrumentation.servlet.common.service.ServletAndFilterAdviceHelper;
import jakarta.servlet.Filter;
import jakarta.servlet.Servlet;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;

public class JakartaServletServiceAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void onEnter(
      @Advice.This(typing = Assigner.Typing.DYNAMIC) Object servletOrFilter,
      @Advice.Argument(value = 0, readOnly = false) ServletRequest request,
      @Advice.Argument(value = 1, readOnly = false) ServletResponse response,
      @Advice.Local("otelContext") Context context,
      @Advice.Local("otelScope") Scope scope) {

    CallDepthThreadLocalMap.incrementCallDepth(AppServerBridge.getCallDepthKey());
    if (!(request instanceof HttpServletRequest) || !(response instanceof HttpServletResponse)) {
      return;
    }

    HttpServletRequest httpServletRequest = (HttpServletRequest) request;

    boolean servlet = servletOrFilter instanceof Servlet;
    MappingResolver mappingResolver;
    if (servlet) {
      mappingResolver =
          InstrumentationContext.get(Servlet.class, MappingResolver.class)
              .get((Servlet) servletOrFilter);
    } else {
      mappingResolver =
          InstrumentationContext.get(Filter.class, MappingResolver.class)
              .get((Filter) servletOrFilter);
    }

    Context attachedContext = tracer().getServerContext(httpServletRequest);
    if (attachedContext != null) {
      // We are inside nested servlet/filter/app-server span, don't create new span
      if (tracer().needsRescoping(attachedContext)) {
        attachedContext =
            tracer().updateContext(attachedContext, httpServletRequest, mappingResolver, servlet);
        scope = attachedContext.makeCurrent();
        return;
      }

      // We already have attached context to request but this could have been done by app server
      // instrumentation, if needed update span with info from current request.
      Context currentContext = Java8BytecodeBridge.currentContext();
      Context updatedContext =
          tracer().updateContext(currentContext, httpServletRequest, mappingResolver, servlet);
      if (updatedContext != currentContext) {
        // runOnceUnderAppServer updated context, need to re-scope
        scope = updatedContext.makeCurrent();
      }
      return;
    }

    Context currentContext = Java8BytecodeBridge.currentContext();
    if (currentContext != null
        && Java8BytecodeBridge.spanFromContext(currentContext).isRecording()) {
      // We already have a span but it was not created by servlet instrumentation.
      // In case it was created by app server integration we need to update it with info from
      // current request.
      Context updatedContext =
          tracer().updateContext(currentContext, httpServletRequest, mappingResolver, servlet);
      if (currentContext != updatedContext) {
        // updateContext updated context, need to re-scope
        scope = updatedContext.makeCurrent();
      }
      return;
    }

    context = tracer().startSpan(httpServletRequest, mappingResolver, servlet);
    scope = context.makeCurrent();
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Argument(0) ServletRequest request,
      @Advice.Argument(1) ServletResponse response,
      @Advice.Thrown Throwable throwable,
      @Advice.Local("otelContext") Context context,
      @Advice.Local("otelScope") Scope scope) {
    if (!(request instanceof HttpServletRequest) || !(response instanceof HttpServletResponse)) {
      return;
    }

    ServletAndFilterAdviceHelper.stopSpan(
        tracer(),
        (HttpServletRequest) request,
        (HttpServletResponse) response,
        throwable,
        context,
        scope);
  }
}
