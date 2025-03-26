/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webmvc.v6_0;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.spring.webmvc.v6_0.SpringWebMvcSingletons.modelAndViewInstrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isProtected;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.bootstrap.InstrumentationProxyHelper;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.List;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.context.ApplicationContext;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.v6_0.OpenTelemetryHandlerMappingFilter;

public class DispatcherServletInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.springframework.web.servlet.DispatcherServlet");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(isProtected())
            .and(named("onRefresh"))
            .and(takesArgument(0, named("org.springframework.context.ApplicationContext")))
            .and(takesArguments(1)),
        DispatcherServletInstrumentation.class.getName() + "$HandlerMappingAdvice");
    transformer.applyAdviceToMethod(
        isMethod()
            .and(isProtected())
            .and(named("render"))
            .and(takesArgument(0, named("org.springframework.web.servlet.ModelAndView"))),
        DispatcherServletInstrumentation.class.getName() + "$RenderAdvice");
  }

  /**
   * This advice creates a filter that has reference to the handlerMappings from DispatcherServlet
   * which allows the mappings to be evaluated outside of regular request processing.
   */
  @SuppressWarnings("unused")
  public static class HandlerMappingAdvice {

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void afterRefresh(
        @Advice.Argument(0) ApplicationContext springCtx,
        @Advice.FieldValue("handlerMappings") List<HandlerMapping> handlerMappings) {

      if (handlerMappings == null || !springCtx.containsBean("otelAutoDispatcherFilter")) {
        return;
      }

      Object bean = springCtx.getBean("otelAutoDispatcherFilter");
      OpenTelemetryHandlerMappingFilter filter =
          InstrumentationProxyHelper.unwrapIfNeeded(bean, OpenTelemetryHandlerMappingFilter.class);
      filter.setHandlerMappings(handlerMappings);
    }
  }

  @SuppressWarnings("unused")
  public static class RenderAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(0) ModelAndView mv,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      Context parentContext = currentContext();
      if (modelAndViewInstrumenter().shouldStart(parentContext, mv)) {
        context = modelAndViewInstrumenter().start(parentContext, mv);
        scope = context.makeCurrent();
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Argument(0) ModelAndView mv,
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      if (scope == null) {
        return;
      }
      scope.close();
      modelAndViewInstrumenter().end(context, mv, null, throwable);
    }
  }
}
