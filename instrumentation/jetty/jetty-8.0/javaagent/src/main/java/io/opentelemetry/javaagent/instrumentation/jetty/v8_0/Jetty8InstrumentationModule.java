/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.v8_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.instrumentation.jetty.v8_0.Jetty8Singletons.helper;
import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.servlet.internal.ServletRequestContext;
import io.opentelemetry.javaagent.bootstrap.http.HttpServerResponseCustomizerHolder;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.instrumentation.jetty.common.JettyHandlerInstrumentation;
import java.util.List;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class Jetty8InstrumentationModule extends InstrumentationModule {

  public Jetty8InstrumentationModule() {
    super("jetty", "jetty-8.0");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // removed in Servlet 5.0 (renamed to jakarta.servlet)
    return hasClassesNamed("javax.servlet.Servlet");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new JettyHandlerInstrumentation(
            "javax.servlet", getClass().getName() + "$Jetty8HandlerAdvice"),
        new JettyQueuedThreadPoolInstrumentation());
  }

  @SuppressWarnings("unused")
  public static class Jetty8HandlerAdvice {

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
      public static AdviceScope start(HttpServletRequest request, HttpServletResponse response) {
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

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    @Nullable
    public static AdviceScope onEnter(
        @Advice.Argument(2) HttpServletRequest request,
        @Advice.Argument(3) HttpServletResponse response) {
      return AdviceScope.start(request, response);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
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
}
