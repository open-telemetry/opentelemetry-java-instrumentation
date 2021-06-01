/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webflux.server;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.extension.matcher.ClassLoaderMatcher.hasClassesNamed;
import static net.bytebuddy.matcher.ElementMatchers.isAbstract;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.servlet.ServletContextPath;
import io.opentelemetry.instrumentation.api.tracer.ServerSpan;
import io.opentelemetry.instrumentation.api.tracer.SpanNames;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.spring.webflux.SpringWebfluxConfig;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.pattern.PathPattern;

public class HandlerAdapterInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("org.springframework.web.reactive.HandlerAdapter");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return not(isAbstract())
        .and(implementsInterface(named("org.springframework.web.reactive.HandlerAdapter")));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(isPublic())
            .and(named("handle"))
            .and(takesArgument(0, named("org.springframework.web.server.ServerWebExchange")))
            .and(takesArgument(1, Object.class))
            .and(takesArguments(2)),
        this.getClass().getName() + "$HandleAdvice");
  }

  public static class HandleAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.Argument(0) ServerWebExchange exchange,
        @Advice.Argument(1) Object handler,
        @Advice.Local("otelScope") Scope scope) {

      Context context = exchange.getAttribute(AdviceUtils.CONTEXT_ATTRIBUTE);
      if (handler != null && context != null) {
        Span span = Span.fromContext(context);
        String handlerType;
        String operationName;

        if (handler instanceof HandlerMethod) {
          // Special case for requests mapped with annotations
          HandlerMethod handlerMethod = (HandlerMethod) handler;
          operationName = SpanNames.spanNameForMethod(handlerMethod.getMethod());
          handlerType = handlerMethod.getMethod().getDeclaringClass().getName();
        } else {
          operationName = AdviceUtils.parseOperationName(handler);
          handlerType = handler.getClass().getName();
        }

        span.updateName(operationName);
        if (SpringWebfluxConfig.captureExperimentalSpanAttributes()) {
          span.setAttribute("spring-webflux.handler.type", handlerType);
        }

        scope = context.makeCurrent();
      }

      if (context != null) {
        Span serverSpan = ServerSpan.fromContextOrNull(context);

        PathPattern bestPattern =
            exchange.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (serverSpan != null && bestPattern != null) {
          serverSpan.updateName(
              ServletContextPath.prepend(Context.current(), bestPattern.toString()));
        }
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Argument(0) ServerWebExchange exchange,
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelScope") Scope scope) {
      if (throwable != null) {
        AdviceUtils.finishSpanIfPresent(exchange, throwable);
      }
      if (scope != null) {
        scope.close();
        // span finished in SpanFinishingSubscriber
      }
    }
  }
}
