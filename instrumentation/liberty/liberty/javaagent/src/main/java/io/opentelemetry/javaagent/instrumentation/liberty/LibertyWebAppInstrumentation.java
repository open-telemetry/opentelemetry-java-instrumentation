/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.liberty;

import static io.opentelemetry.javaagent.instrumentation.liberty.LibertyHttpServerTracer.tracer;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.servlet.AppServerBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.servlet.common.service.ServletAndFilterAdviceHelper;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class LibertyWebAppInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.ibm.ws.webcontainer.webapp.WebApp");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    // https://github.com/OpenLiberty/open-liberty/blob/master/dev/com.ibm.ws.webcontainer/src/com/ibm/ws/webcontainer/webapp/WebApp.java
    transformer.applyAdviceToMethod(
        named("handleRequest")
            .and(takesArgument(0, named("javax.servlet.ServletRequest")))
            .and(takesArgument(1, named("javax.servlet.ServletResponse")))
            .and(takesArgument(2, named("com.ibm.wsspi.http.HttpInboundConnection"))),
        this.getClass().getName() + "$HandleRequestAdvice");

    // isForbidden is called from handleRequest
    transformer.applyAdviceToMethod(
        named("isForbidden").and(takesArgument(0, named(String.class.getName()))),
        this.getClass().getName() + "$IsForbiddenAdvice");
  }

  @SuppressWarnings("unused")
  public static class HandleRequestAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(value = 0) ServletRequest request,
        @Advice.Argument(value = 1) ServletResponse response) {

      if (!(request instanceof HttpServletRequest) || !(response instanceof HttpServletResponse)) {
        return;
      }

      HttpServletRequest httpServletRequest = (HttpServletRequest) request;
      // it is a bit too early to start span at this point because calling
      // some methods on HttpServletRequest will give a NPE
      // just remember the request and use it a bit later to start the span
      ThreadLocalContext.startRequest(httpServletRequest, (HttpServletResponse) response);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Argument(0) ServletRequest servletRequest,
        @Advice.Argument(1) ServletResponse servletResponse,
        @Advice.Thrown Throwable throwable) {
      ThreadLocalContext ctx = ThreadLocalContext.endRequest();
      if (ctx == null) {
        return;
      }

      Context context = ctx.getContext();
      Scope scope = ctx.getScope();
      if (scope == null) {
        return;
      }
      scope.close();

      if (context == null) {
        // an existing span was found
        return;
      }

      HttpServletRequest request = (HttpServletRequest) servletRequest;
      HttpServletResponse response = (HttpServletResponse) servletResponse;

      tracer().setPrincipal(context, request);

      Throwable error = throwable;
      if (error == null) {
        error = AppServerBridge.getException(context);
      }

      if (error != null) {
        tracer().endExceptionally(context, error, response);
        return;
      }

      if (ServletAndFilterAdviceHelper.mustEndOnHandlerMethodExit(tracer(), request)) {
        tracer().end(context, response);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class IsForbiddenAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter() {
      ThreadLocalContext ctx = ThreadLocalContext.get();
      if (ctx == null || !ctx.startSpan()) {
        return;
      }

      Context context = tracer().startSpan(ctx.getRequest());
      Scope scope = context.makeCurrent();

      ctx.setContext(context);
      ctx.setScope(scope);

      // Must be set here since Liberty RequestProcessors can use startAsync outside of servlet
      // scope.
      tracer().setAsyncListenerResponse(ctx.getRequest(), ctx.getResponse());
    }
  }
}
