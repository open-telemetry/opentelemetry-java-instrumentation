/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.v11_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.instrumentation.jetty.v11_0.Jetty11Singletons.helper;
import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.servlet.internal.ServletRequestContext;
import io.opentelemetry.javaagent.bootstrap.http.HttpServerResponseCustomizerHolder;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.instrumentation.jetty.common.JettyHandlerInstrumentation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class Jetty11InstrumentationModule extends InstrumentationModule {

  public Jetty11InstrumentationModule() {
    super("jetty", "jetty-11.0");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // added in Servlet 5.0 (renamed from javax.servlet)
    return hasClassesNamed("jakarta.servlet.Servlet");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(
        new JettyHandlerInstrumentation(
            "jakarta.servlet", getClass().getName() + "$Jetty11HandlerAdvice"));
  }

  @SuppressWarnings("unused")
  public static class Jetty11HandlerAdvice {

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
            .customize(context, response, Jetty11ResponseMutator.INSTANCE);
        return new AdviceScope(requestContext, context, scope);
      }

      public void end(
          @Nullable Throwable throwable, HttpServletRequest request, HttpServletResponse response) {
        helper().end(requestContext, request, response, throwable, context, scope);
      }
    }

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
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
