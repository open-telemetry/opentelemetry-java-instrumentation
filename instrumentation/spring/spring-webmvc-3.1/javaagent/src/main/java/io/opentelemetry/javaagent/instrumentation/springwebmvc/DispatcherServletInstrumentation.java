/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.springwebmvc;

import static io.opentelemetry.javaagent.instrumentation.springwebmvc.SpringWebMvcTracer.tracer;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isProtected;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.context.ApplicationContext;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.ModelAndView;

public class DispatcherServletInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.springframework.web.servlet.DispatcherServlet");
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();
    transformers.put(
        isMethod()
            .and(isProtected())
            .and(named("onRefresh"))
            .and(takesArgument(0, named("org.springframework.context.ApplicationContext")))
            .and(takesArguments(1)),
        DispatcherServletInstrumentation.class.getName() + "$HandlerMappingAdvice");
    transformers.put(
        isMethod()
            .and(isProtected())
            .and(named("render"))
            .and(takesArgument(0, named("org.springframework.web.servlet.ModelAndView"))),
        DispatcherServletInstrumentation.class.getName() + "$RenderAdvice");
    transformers.put(
        isMethod()
            .and(isProtected())
            .and(nameStartsWith("processHandlerException"))
            .and(takesArgument(3, Exception.class)),
        DispatcherServletInstrumentation.class.getName() + "$ErrorHandlerAdvice");
    return transformers;
  }

  /**
   * This advice creates a filter that has reference to the handlerMappings from DispatcherServlet
   * which allows the mappings to be evaluated at the beginning of the filter chain. This evaluation
   * is done inside the Servlet3Decorator.onContext method.
   */
  public static class HandlerMappingAdvice {

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void afterRefresh(
        @Advice.Argument(0) ApplicationContext springCtx,
        @Advice.FieldValue("handlerMappings") List<HandlerMapping> handlerMappings) {
      if (springCtx.containsBean("otelAutoDispatcherFilter")) {
        HandlerMappingResourceNameFilter filter =
            (HandlerMappingResourceNameFilter) springCtx.getBean("otelAutoDispatcherFilter");
        if (handlerMappings != null && filter != null) {
          filter.setHandlerMappings(handlerMappings);
        }
      }
    }
  }

  public static class RenderAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(0) ModelAndView mv,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      context = tracer().startSpan(mv);
      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      scope.close();
      if (throwable == null) {
        tracer().end(context);
      } else {
        tracer().endExceptionally(context, throwable);
      }
    }
  }

  public static class ErrorHandlerAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void nameResource(@Advice.Argument(3) Exception exception) {
      Span span = Java8BytecodeBridge.currentSpan();
      if (span.getSpanContext().isValid() && exception != null) {
        // We want to capture the stacktrace, but that doesn't mean it should be an error.
        // We rely on a decorator to set the error state based on response code. (5xx -> error)
        tracer().addThrowable(span, exception);
      }
    }
  }
}
