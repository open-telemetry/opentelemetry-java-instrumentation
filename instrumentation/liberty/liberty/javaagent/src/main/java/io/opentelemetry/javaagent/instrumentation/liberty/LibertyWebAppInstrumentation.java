/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.liberty;

import static io.opentelemetry.javaagent.instrumentation.liberty.LibertySingletons.helper;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import io.opentelemetry.javaagent.instrumentation.servlet.ServletRequestContext;
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
      ThreadLocalContext requestInfo = ThreadLocalContext.endRequest();
      if (requestInfo == null) {
        return;
      }

      HttpServletRequest request = (HttpServletRequest) servletRequest;
      HttpServletResponse response = (HttpServletResponse) servletResponse;

      helper()
          .end(
              requestInfo.getRequestContext(),
              request,
              response,
              throwable,
              requestInfo.getContext(),
              requestInfo.getScope());
    }
  }

  @SuppressWarnings("unused")
  public static class IsForbiddenAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter() {
      ThreadLocalContext requestInfo = ThreadLocalContext.get();
      if (requestInfo == null || !requestInfo.startSpan()) {
        return;
      }

      Context parentContext = Java8BytecodeBridge.currentContext();
      ServletRequestContext<HttpServletRequest> requestContext = requestInfo.getRequestContext();

      if (!helper().shouldStart(parentContext, requestContext)) {
        return;
      }

      Context context = helper().start(parentContext, requestContext);
      Scope scope = context.makeCurrent();

      requestInfo.setContext(context);
      requestInfo.setScope(scope);

      // Must be set here since Liberty RequestProcessors can use startAsync outside of servlet
      // scope.
      helper().setAsyncListenerResponse(requestInfo.getRequest(), requestInfo.getResponse());
    }
  }
}
